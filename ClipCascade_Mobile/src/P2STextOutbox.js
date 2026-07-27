'use strict';

const DEFAULT_STORAGE_KEY = 'p2s_text_outbox_v1';
const DEFAULT_MAX_ITEMS = 20;
const DEFAULT_MAX_BYTES = 256 * 1024;
const DEFAULT_MAX_AGE_MS = 24 * 60 * 60 * 1000;

/**
 * Persistent, bounded, single-flight text outbox for the existing P2S protocol.
 *
 * The caller supplies an already-prepared wire payload plus a hash of the
 * original plaintext. When ClipCascade encryption is enabled, the persisted
 * wire payload is ciphertext. No network transport is created here.
 */
class P2STextOutbox {
  constructor({
    storage,
    storageKey = DEFAULT_STORAGE_KEY,
    maxItems = DEFAULT_MAX_ITEMS,
    maxBytes = DEFAULT_MAX_BYTES,
    maxAgeMs = DEFAULT_MAX_AGE_MS,
    now = () => Date.now(),
    idFactory = defaultIdFactory,
  }) {
    if (!storage || typeof storage.read !== 'function' || typeof storage.write !== 'function') {
      throw new TypeError('storage.read and storage.write are required');
    }
    if (!Number.isInteger(maxItems) || maxItems < 1) {
      throw new RangeError('maxItems must be a positive integer');
    }
    if (!Number.isFinite(maxBytes) || maxBytes < 1) {
      throw new RangeError('maxBytes must be positive');
    }
    if (!Number.isFinite(maxAgeMs) || maxAgeMs < 0) {
      throw new RangeError('maxAgeMs must be non-negative');
    }

    this.storage = storage;
    this.storageKey = storageKey;
    this.maxItems = maxItems;
    this.maxBytes = maxBytes;
    this.maxAgeMs = maxAgeMs;
    this.now = now;
    this.idFactory = idFactory;

    this.loaded = false;
    this.items = [];
    this.dropped = 0;
    this._serial = Promise.resolve();
  }

  load() {
    return this._run(async () => {
      if (this.loaded) return this.snapshotSync();

      const raw = await this.storage.read(this.storageKey);
      const source = Array.isArray(raw) ? raw : [];
      const currentTime = this.now();
      this.items = source
        .map(item => sanitizeItem(item))
        .filter(Boolean)
        .filter(item => !this._isExpired(item, currentTime))
        .map(item => ({ ...item, state: 'queued' }));
      this.loaded = true;
      this._trimToBounds();
      await this._persist();
      return this.snapshotSync();
    });
  }

  enqueue({ wirePayload, contentHash, wireBytes }) {
    return this._run(async () => {
      this._requireLoaded();
      validateEnqueueInput(wirePayload, contentHash, wireBytes);

      this._dropExpired();
      const existing = this.items.find(item => item.contentHash === contentHash);
      if (existing) {
        return {
          accepted: false,
          duplicate: true,
          id: existing.id,
          dropped: 0,
          snapshot: this.snapshotSync(),
        };
      }
      if (wireBytes > this.maxBytes) {
        return {
          accepted: false,
          duplicate: false,
          tooLarge: true,
          dropped: 0,
          snapshot: this.snapshotSync(),
        };
      }

      const item = {
        id: this.idFactory(),
        wirePayload,
        contentHash,
        wireBytes,
        createdAt: this.now(),
        attempts: 0,
        lastAttemptAt: null,
        nextAttemptAt: null,
        state: 'queued',
      };
      this.items.push(item);
      const dropped = this._trimToBounds();

      if (!this.items.some(current => current.id === item.id)) {
        return {
          accepted: false,
          duplicate: false,
          dropped,
          overflow: true,
          snapshot: this.snapshotSync(),
        };
      }

      await this._persist();
      return {
        accepted: true,
        duplicate: false,
        id: item.id,
        dropped,
        snapshot: this.snapshotSync(),
      };
    });
  }

  peek() {
    return this._run(async () => {
      this._requireLoaded();
      this._dropExpired();
      await this._persist();
      return this.items.length > 0 ? { ...this.items[0] } : null;
    });
  }

  markAttempt(id, retryDelayMs = 0) {
    return this._run(async () => {
      this._requireLoaded();
      if (!Number.isFinite(retryDelayMs) || retryDelayMs < 0) {
        throw new RangeError('retryDelayMs must be non-negative');
      }

      const head = this.items[0];
      if (!head || head.id !== id || head.state === 'inflight') return false;

      const attemptedAt = this.now();
      head.state = 'inflight';
      head.attempts += 1;
      head.lastAttemptAt = attemptedAt;
      head.nextAttemptAt = attemptedAt + retryDelayMs;
      await this._persist();
      return true;
    });
  }

  releaseInFlight(id = null) {
    return this._run(async () => {
      this._requireLoaded();
      const head = this.items[0];
      if (!head || head.state !== 'inflight') return false;
      if (id !== null && head.id !== id) return false;

      // Keep nextAttemptAt so reconnects/process restarts respect the backoff.
      head.state = 'queued';
      await this._persist();
      return true;
    });
  }

  acknowledgeEcho(contentHash) {
    return this._run(async () => {
      this._requireLoaded();
      const head = this.items[0];
      if (!head || head.state !== 'inflight' || head.contentHash !== contentHash) {
        return false;
      }

      this.items.shift();
      await this._persist();
      return true;
    });
  }

  clear() {
    return this._run(async () => {
      this._requireLoaded();
      this.items = [];
      await this._persist();
      return this.snapshotSync();
    });
  }

  snapshot() {
    return this._run(async () => {
      this._requireLoaded();
      this._dropExpired();
      await this._persist();
      return this.snapshotSync();
    });
  }

  snapshotSync() {
    const totalBytes = this.items.reduce((sum, item) => sum + item.wireBytes, 0);
    const head = this.items[0] || null;
    return {
      loaded: this.loaded,
      count: this.items.length,
      totalBytes,
      dropped: this.dropped,
      oldestCreatedAt: head ? head.createdAt : null,
      headState: head ? head.state : null,
      headAttempts: head ? head.attempts : 0,
      headLastAttemptAt: head ? head.lastAttemptAt : null,
      headNextAttemptAt: head ? head.nextAttemptAt : null,
    };
  }

  _run(operation) {
    const result = this._serial.then(operation);
    this._serial = result.catch(() => {});
    return result;
  }

  _requireLoaded() {
    if (!this.loaded) throw new Error('P2STextOutbox.load() must complete first');
  }

  _dropExpired() {
    const before = this.items.length;
    const currentTime = this.now();
    this.items = this.items.filter(item => !this._isExpired(item, currentTime));
    const dropped = before - this.items.length;
    this.dropped += dropped;
    return dropped;
  }

  _isExpired(item, currentTime) {
    return currentTime >= item.createdAt && currentTime - item.createdAt > this.maxAgeMs;
  }

  _trimToBounds() {
    let dropped = 0;
    const byteTotal = () => this.items.reduce((sum, item) => sum + item.wireBytes, 0);

    while (this.items.length > this.maxItems || byteTotal() > this.maxBytes) {
      const removableIndex = this.items.findIndex(item => item.state !== 'inflight');
      if (removableIndex < 0) break;
      this.items.splice(removableIndex, 1);
      dropped += 1;
    }
    this.dropped += dropped;
    return dropped;
  }

  async _persist() {
    await this.storage.write(this.storageKey, this.items.map(item => ({ ...item })));
  }
}

function sanitizeItem(item) {
  if (!item || typeof item !== 'object') return null;
  if (typeof item.id !== 'string' || item.id.length === 0) return null;
  if (typeof item.wirePayload !== 'string') return null;
  if (typeof item.contentHash !== 'string' || item.contentHash.length === 0) return null;
  if (!Number.isFinite(item.wireBytes) || item.wireBytes < 0) return null;
  if (!Number.isFinite(item.createdAt) || item.createdAt < 0) return null;

  return {
    id: item.id,
    wirePayload: item.wirePayload,
    contentHash: item.contentHash,
    wireBytes: item.wireBytes,
    createdAt: item.createdAt,
    attempts: Number.isInteger(item.attempts) && item.attempts >= 0 ? item.attempts : 0,
    lastAttemptAt: Number.isFinite(item.lastAttemptAt) ? item.lastAttemptAt : null,
    nextAttemptAt: Number.isFinite(item.nextAttemptAt) ? item.nextAttemptAt : null,
    state: item.state === 'inflight' ? 'inflight' : 'queued',
  };
}

function validateEnqueueInput(wirePayload, contentHash, wireBytes) {
  if (typeof wirePayload !== 'string') throw new TypeError('wirePayload must be a string');
  if (typeof contentHash !== 'string' || contentHash.length === 0) {
    throw new TypeError('contentHash must be a non-empty string');
  }
  if (!Number.isFinite(wireBytes) || wireBytes < 0) {
    throw new RangeError('wireBytes must be non-negative');
  }
}

function defaultIdFactory() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

module.exports = {
  P2STextOutbox,
  DEFAULT_STORAGE_KEY,
};

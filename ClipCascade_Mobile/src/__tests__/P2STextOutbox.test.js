'use strict';

const { P2STextOutbox } = require('../P2STextOutbox');

function makeStorage(initial = null) {
  let value = initial;
  return {
    read: jest.fn(async () => value),
    write: jest.fn(async (_key, next) => {
      value = JSON.parse(JSON.stringify(next));
    }),
    current: () => value,
  };
}

function makeOutbox(options = {}) {
  let now = options.initialNow ?? 1_000;
  let id = 0;
  const storage = options.storage ?? makeStorage();
  const outbox = new P2STextOutbox({
    storage,
    now: () => now,
    idFactory: () => `item-${++id}`,
    maxItems: options.maxItems ?? 3,
    maxBytes: options.maxBytes ?? 100,
    maxAgeMs: options.maxAgeMs ?? 10_000,
  });
  return {
    outbox,
    storage,
    setNow: value => {
      now = value;
    },
  };
}

describe('P2STextOutbox', () => {
  test('requires load before mutation', async () => {
    const { outbox } = makeOutbox();
    await expect(
      outbox.enqueue({ wirePayload: 'x', contentHash: 'h', wireBytes: 1 }),
    ).rejects.toThrow('load');
  });

  test('persists prepared wire payload and exposes payload-free snapshot', async () => {
    const { outbox, storage } = makeOutbox();
    await outbox.load();
    const result = await outbox.enqueue({
      wirePayload: '{ciphertext}',
      contentHash: 'plain-hash',
      wireBytes: 12,
    });

    expect(result.accepted).toBe(true);
    expect(storage.current()).toEqual([
      expect.objectContaining({
        wirePayload: '{ciphertext}',
        contentHash: 'plain-hash',
        state: 'queued',
        nextAttemptAt: null,
      }),
    ]);
    expect(result.snapshot).toEqual(
      expect.objectContaining({
        count: 1,
        totalBytes: 12,
        headState: 'queued',
        headNextAttemptAt: null,
      }),
    );
    expect(result.snapshot).not.toHaveProperty('wirePayload');
    expect(result.snapshot).not.toHaveProperty('contentHash');
  });

  test('deduplicates any still-pending plaintext hash', async () => {
    const { outbox } = makeOutbox();
    await outbox.load();
    await outbox.enqueue({ wirePayload: 'first', contentHash: 'same', wireBytes: 5 });
    const duplicate = await outbox.enqueue({
      wirePayload: 'second',
      contentHash: 'same',
      wireBytes: 6,
    });

    expect(duplicate.accepted).toBe(false);
    expect(duplicate.duplicate).toBe(true);
    expect((await outbox.snapshot()).count).toBe(1);
  });

  test('restores an interrupted inflight item as queued and preserves backoff after restart', async () => {
    const storage = makeStorage([
      {
        id: 'old',
        wirePayload: 'cipher',
        contentHash: 'hash',
        wireBytes: 6,
        createdAt: 900,
        attempts: 1,
        lastAttemptAt: 950,
        nextAttemptAt: 5_000,
        state: 'inflight',
      },
    ]);
    const { outbox } = makeOutbox({ storage });

    const snapshot = await outbox.load();

    expect(snapshot.headState).toBe('queued');
    expect(snapshot.headNextAttemptAt).toBe(5_000);
    expect(storage.current()[0].state).toBe('queued');
    expect(storage.current()[0].nextAttemptAt).toBe(5_000);
  });

  test('loads old records without a retry timestamp', async () => {
    const storage = makeStorage([
      {
        id: 'old-format',
        wirePayload: 'cipher',
        contentHash: 'hash',
        wireBytes: 6,
        createdAt: 900,
        attempts: 1,
        lastAttemptAt: 950,
        state: 'queued',
      },
    ]);
    const { outbox } = makeOutbox({ storage });

    const snapshot = await outbox.load();

    expect(snapshot.headNextAttemptAt).toBeNull();
    expect(storage.current()[0].nextAttemptAt).toBeNull();
  });

  test('only acknowledges the current inflight matching echo', async () => {
    const { outbox } = makeOutbox();
    await outbox.load();
    const first = await outbox.enqueue({ wirePayload: 'a', contentHash: 'ha', wireBytes: 1 });
    await outbox.enqueue({ wirePayload: 'b', contentHash: 'hb', wireBytes: 1 });

    expect(await outbox.acknowledgeEcho('ha')).toBe(false);
    expect(await outbox.markAttempt(first.id, 30_000)).toBe(true);
    expect(await outbox.acknowledgeEcho('hb')).toBe(false);
    expect(await outbox.acknowledgeEcho('ha')).toBe(true);
    expect((await outbox.peek()).contentHash).toBe('hb');
  });

  test('markAttempt stores the next allowed retry time', async () => {
    const { outbox, storage } = makeOutbox({ initialNow: 2_000 });
    await outbox.load();
    const item = await outbox.enqueue({ wirePayload: 'a', contentHash: 'ha', wireBytes: 1 });

    expect(await outbox.markAttempt(item.id, 30_000)).toBe(true);
    expect((await outbox.snapshot()).headNextAttemptAt).toBe(32_000);
    expect(storage.current()[0].nextAttemptAt).toBe(32_000);
    await expect(outbox.markAttempt(item.id, -1)).resolves.toBe(false);
  });

  test('rejects an invalid retry delay before mutating a queued item', async () => {
    const { outbox } = makeOutbox();
    await outbox.load();
    const item = await outbox.enqueue({ wirePayload: 'a', contentHash: 'ha', wireBytes: 1 });

    await expect(outbox.markAttempt(item.id, -1)).rejects.toThrow(RangeError);
    expect((await outbox.snapshot()).headState).toBe('queued');
  });

  test('release keeps the persisted not-before time for reconnect retry', async () => {
    const { outbox, storage } = makeOutbox({ initialNow: 1_000 });
    await outbox.load();
    const item = await outbox.enqueue({ wirePayload: 'a', contentHash: 'ha', wireBytes: 1 });
    await outbox.markAttempt(item.id, 5_000);

    expect((await outbox.snapshot()).headState).toBe('inflight');
    expect(await outbox.releaseInFlight(item.id)).toBe(true);
    const snapshot = await outbox.snapshot();
    expect(snapshot.headState).toBe('queued');
    expect(snapshot.headNextAttemptAt).toBe(6_000);
    expect(storage.current()[0].nextAttemptAt).toBe(6_000);
  });

  test('drops oldest queued items to enforce count and byte bounds', async () => {
    const { outbox } = makeOutbox({ maxItems: 2, maxBytes: 8 });
    await outbox.load();
    await outbox.enqueue({ wirePayload: '1111', contentHash: 'h1', wireBytes: 4 });
    await outbox.enqueue({ wirePayload: '2222', contentHash: 'h2', wireBytes: 4 });
    const result = await outbox.enqueue({
      wirePayload: '3333',
      contentHash: 'h3',
      wireBytes: 4,
    });

    expect(result.dropped).toBe(1);
    expect((await outbox.peek()).contentHash).toBe('h2');
    expect((await outbox.snapshot()).count).toBe(2);
  });

  test('never evicts the inflight head to make space', async () => {
    const { outbox } = makeOutbox({ maxItems: 1, maxBytes: 10 });
    await outbox.load();
    const first = await outbox.enqueue({ wirePayload: 'first', contentHash: 'h1', wireBytes: 5 });
    await outbox.markAttempt(first.id, 30_000);

    const second = await outbox.enqueue({
      wirePayload: 'second',
      contentHash: 'h2',
      wireBytes: 6,
    });

    expect(second.accepted).toBe(false);
    expect(second.overflow).toBe(true);
    expect((await outbox.peek()).contentHash).toBe('h1');
  });

  test('rejects a single item larger than the storage budget', async () => {
    const { outbox } = makeOutbox({ maxBytes: 4 });
    await outbox.load();

    const result = await outbox.enqueue({
      wirePayload: 'oversized',
      contentHash: 'h',
      wireBytes: 5,
    });

    expect(result.accepted).toBe(false);
    expect(result.tooLarge).toBe(true);
    expect((await outbox.snapshot()).count).toBe(0);
  });

  test('drops expired items during load without treating clock rollback as expiry', async () => {
    const storage = makeStorage([
      {
        id: 'expired',
        wirePayload: 'old',
        contentHash: 'old-hash',
        wireBytes: 3,
        createdAt: 1_000,
        attempts: 0,
        lastAttemptAt: null,
        nextAttemptAt: null,
        state: 'queued',
      },
      {
        id: 'future',
        wirePayload: 'future',
        contentHash: 'future-hash',
        wireBytes: 6,
        createdAt: 30_000,
        attempts: 0,
        lastAttemptAt: null,
        nextAttemptAt: null,
        state: 'queued',
      },
    ]);
    const { outbox } = makeOutbox({ storage, initialNow: 20_000, maxAgeMs: 5_000 });

    const snapshot = await outbox.load();

    expect(snapshot.count).toBe(1);
    expect((await outbox.peek()).contentHash).toBe('future-hash');
  });

  test('serializes concurrent enqueues', async () => {
    const { outbox } = makeOutbox({ maxItems: 5, maxBytes: 100 });
    await outbox.load();

    await Promise.all([
      outbox.enqueue({ wirePayload: 'a', contentHash: 'ha', wireBytes: 1 }),
      outbox.enqueue({ wirePayload: 'b', contentHash: 'hb', wireBytes: 1 }),
      outbox.enqueue({ wirePayload: 'c', contentHash: 'hc', wireBytes: 1 }),
    ]);

    expect((await outbox.snapshot()).count).toBe(3);
  });
});

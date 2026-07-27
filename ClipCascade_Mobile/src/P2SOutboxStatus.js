'use strict';

function finiteNonNegativeInteger(value, fallback = 0) {
  return Number.isFinite(value) && value >= 0 ? Math.floor(value) : fallback;
}

function normalizeStatus(rawStatus) {
  if (typeof rawStatus !== 'string') {
    return rawStatus;
  }

  try {
    const parsed = JSON.parse(rawStatus);
    return parsed && typeof parsed === 'object' ? parsed : null;
  } catch (_error) {
    return null;
  }
}

/**
 * Formats only non-payload outbox metadata for the existing connection page.
 * Clipboard contents, hashes, server URLs, and account identifiers are never shown.
 *
 * NativeBridgeModule.getFlagsSync returns AsyncStorage objects as serialized JSON
 * strings, so this boundary accepts either the direct object or that exact string.
 */
function formatP2SOutboxStatus(rawStatus) {
  const status = normalizeStatus(rawStatus);
  if (!status || typeof status !== 'object' || status.loaded !== true) {
    return '';
  }

  const count = finiteNonNegativeInteger(status.count);
  const totalBytes = finiteNonNegativeInteger(status.totalBytes);
  const dropped = finiteNonNegativeInteger(status.dropped);
  const attempts = finiteNonNegativeInteger(status.headAttempts);
  const state = status.headState;

  if (count === 0) {
    return dropped > 0
      ? `📦 Text queue: empty | Dropped: ${dropped}`
      : '📦 Text queue: empty';
  }

  const label = state === 'inflight' ? 'sending' : 'queued';
  const parts = [
    `📦 Text queue: ${count}`,
    `State: ${label}`,
    `Size: ${totalBytes} B`,
  ];

  if (attempts > 0) {
    parts.push(`Attempts: ${attempts}`);
  }
  if (dropped > 0) {
    parts.push(`Dropped: ${dropped}`);
  }

  return parts.join(' | ');
}

module.exports = { formatP2SOutboxStatus, normalizeStatus };

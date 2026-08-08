'use strict';

const DEFAULT_BASE_DELAY_MS = 30 * 1000;
const DEFAULT_MAX_DELAY_MS = 10 * 60 * 1000;
const DEFAULT_JITTER_RATIO = 0.2;

/**
 * Bounded exponential delay for missing P2S server echoes.
 * Attempt 1 starts at 30 seconds, doubles, and caps at 10 minutes.
 * Symmetric jitter avoids every client retrying an outage at the same instant.
 */
function getP2SRetryDelayMs(
  attemptNumber,
  {
    baseDelayMs = DEFAULT_BASE_DELAY_MS,
    maxDelayMs = DEFAULT_MAX_DELAY_MS,
    jitterRatio = DEFAULT_JITTER_RATIO,
    random = Math.random,
  } = {},
) {
  if (!Number.isInteger(attemptNumber) || attemptNumber < 1) {
    throw new RangeError('attemptNumber must be a positive integer');
  }
  if (!Number.isFinite(baseDelayMs) || baseDelayMs < 1) {
    throw new RangeError('baseDelayMs must be positive');
  }
  if (!Number.isFinite(maxDelayMs) || maxDelayMs < baseDelayMs) {
    throw new RangeError('maxDelayMs must be at least baseDelayMs');
  }
  if (!Number.isFinite(jitterRatio) || jitterRatio < 0 || jitterRatio > 1) {
    throw new RangeError('jitterRatio must be between 0 and 1');
  }
  if (typeof random !== 'function') {
    throw new TypeError('random must be a function');
  }

  const exponent = Math.min(attemptNumber - 1, 30);
  const unjittered = Math.min(maxDelayMs, baseDelayMs * 2 ** exponent);
  const randomValue = random();
  if (!Number.isFinite(randomValue) || randomValue < 0 || randomValue > 1) {
    throw new RangeError('random must return a number between 0 and 1');
  }

  const jitter = unjittered * jitterRatio * (randomValue * 2 - 1);
  return Math.max(1, Math.min(maxDelayMs, Math.round(unjittered + jitter)));
}

module.exports = {
  getP2SRetryDelayMs,
  DEFAULT_BASE_DELAY_MS,
  DEFAULT_MAX_DELAY_MS,
  DEFAULT_JITTER_RATIO,
};

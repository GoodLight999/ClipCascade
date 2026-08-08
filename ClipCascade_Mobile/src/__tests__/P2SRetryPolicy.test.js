'use strict';

const {
  getP2SRetryDelayMs,
  DEFAULT_BASE_DELAY_MS,
  DEFAULT_MAX_DELAY_MS,
} = require('../P2SRetryPolicy');

describe('getP2SRetryDelayMs', () => {
  test('starts at 30 seconds and doubles without jitter', () => {
    const options = { jitterRatio: 0, random: () => 0.5 };
    expect(getP2SRetryDelayMs(1, options)).toBe(30_000);
    expect(getP2SRetryDelayMs(2, options)).toBe(60_000);
    expect(getP2SRetryDelayMs(3, options)).toBe(120_000);
    expect(getP2SRetryDelayMs(4, options)).toBe(240_000);
    expect(getP2SRetryDelayMs(5, options)).toBe(480_000);
  });

  test('caps at ten minutes', () => {
    const options = { jitterRatio: 0, random: () => 0.5 };
    expect(getP2SRetryDelayMs(6, options)).toBe(DEFAULT_MAX_DELAY_MS);
    expect(getP2SRetryDelayMs(100, options)).toBe(DEFAULT_MAX_DELAY_MS);
  });

  test('applies symmetric jitter and never exceeds the cap', () => {
    expect(getP2SRetryDelayMs(1, { random: () => 0 })).toBe(24_000);
    expect(getP2SRetryDelayMs(1, { random: () => 1 })).toBe(36_000);
    expect(getP2SRetryDelayMs(6, { random: () => 1 })).toBe(
      DEFAULT_MAX_DELAY_MS,
    );
  });

  test('supports deterministic custom bounds', () => {
    expect(
      getP2SRetryDelayMs(3, {
        baseDelayMs: 100,
        maxDelayMs: 350,
        jitterRatio: 0,
        random: () => 0.5,
      }),
    ).toBe(350);
  });

  test('rejects invalid policy inputs', () => {
    expect(() => getP2SRetryDelayMs(0)).toThrow(RangeError);
    expect(() =>
      getP2SRetryDelayMs(1, { baseDelayMs: DEFAULT_BASE_DELAY_MS, maxDelayMs: 1 }),
    ).toThrow(RangeError);
    expect(() => getP2SRetryDelayMs(1, { jitterRatio: 2 })).toThrow(
      RangeError,
    );
    expect(() => getP2SRetryDelayMs(1, { random: () => 2 })).toThrow(
      RangeError,
    );
  });
});

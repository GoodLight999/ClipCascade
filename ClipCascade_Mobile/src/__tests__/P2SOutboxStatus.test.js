'use strict';

const {
  formatP2SOutboxStatus,
  normalizeStatus,
} = require('../P2SOutboxStatus');

describe('formatP2SOutboxStatus', () => {
  test('hides absent, malformed, or unloaded state', () => {
    expect(formatP2SOutboxStatus(null)).toBe('');
    expect(formatP2SOutboxStatus({ loaded: false })).toBe('');
    expect(formatP2SOutboxStatus('{not json')).toBe('');
    expect(normalizeStatus('null')).toBeNull();
  });

  test('shows an empty queue without payload data', () => {
    expect(
      formatP2SOutboxStatus({
        loaded: true,
        count: 0,
        totalBytes: 0,
        dropped: 0,
      }),
    ).toBe('📦 Text queue: empty');
  });

  test('accepts the serialized object returned by NativeBridge polling', () => {
    expect(
      formatP2SOutboxStatus(
        JSON.stringify({
          loaded: true,
          count: 2,
          totalBytes: 128,
          headState: 'queued',
          headAttempts: 0,
          dropped: 0,
        }),
      ),
    ).toBe('📦 Text queue: 2 | State: queued | Size: 128 B');
  });

  test('shows queued count, bytes, attempts, and drops', () => {
    expect(
      formatP2SOutboxStatus({
        loaded: true,
        count: 3,
        totalBytes: 512,
        headState: 'queued',
        headAttempts: 2,
        dropped: 1,
      }),
    ).toBe(
      '📦 Text queue: 3 | State: queued | Size: 512 B | Attempts: 2 | Dropped: 1',
    );
  });

  test('distinguishes the in-flight head', () => {
    expect(
      formatP2SOutboxStatus({
        loaded: true,
        count: 1,
        totalBytes: 42,
        headState: 'inflight',
        headAttempts: 1,
        dropped: 0,
      }),
    ).toBe('📦 Text queue: 1 | State: sending | Size: 42 B | Attempts: 1');
  });

  test('does not expose unknown object fields', () => {
    const message = formatP2SOutboxStatus({
      loaded: true,
      count: 1,
      totalBytes: 10,
      headState: 'queued',
      payload: 'secret clipboard text',
      contentHash: 'private hash',
      storageKey: 'private scope',
    });

    expect(message).not.toContain('secret clipboard text');
    expect(message).not.toContain('private hash');
    expect(message).not.toContain('private scope');
  });
});

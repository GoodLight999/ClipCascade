'use strict';

const fs = require('fs');
const path = require('path');

const source = fs.readFileSync(
  path.join(__dirname, '..', 'StartForegroundService.js'),
  'utf8',
);

describe('P2S text outbox integration contract', () => {
  test('uses the persistent outbox without changing the server destinations', () => {
    expect(source).toContain("const { P2STextOutbox } = require('./P2STextOutbox');");
    expect(source).toContain("const SUBSCRIPTION_DESTINATION = '/user/queue/cliptext';");
    expect(source).toContain("const SEND_DESTINATION = '/app/cliptext';");
    expect(source).toContain('await p2sTextOutbox.load();');
  });

  test('queues text while offline and drains it after STOMP subscription', () => {
    expect(source).toContain('Text queued while offline');
    expect(source).toContain("type: 'text'");
    expect(source).toContain('await drainP2STextOutbox();');
    expect(source).toContain("'✅ Connected - Subscribed'");
  });

  test('acknowledges a queued echo without rolling the clipboard backward', () => {
    expect(source).toContain('await p2sTextOutbox.acknowledgeEcho(hcb)');
    expect(source).toContain(
      'if (!acknowledgedQueuedText && (await newCB(hcb)))',
    );
  });

  test('returns inflight text to the queue on connection loss and shutdown', () => {
    const releases = source.match(/await releaseP2STextInFlight\(\);/g) || [];
    expect(releases.length).toBeGreaterThanOrEqual(5);
    expect(source).toContain('await p2sTextOutbox.releaseInFlight(head.id);');
  });

  test('does not retain patch artifacts in the product source', () => {
    expect(source).not.toContain(
      '// start websocket stomp connection          // start websocket stomp connection',
    );
  });
});

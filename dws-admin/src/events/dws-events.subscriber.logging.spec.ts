import { Logger } from '@nestjs/common';
import type { Db } from '../store/db.type';
import { JsonLogger } from '../logging/json-logger';
import { DwsEventsSubscriber } from './dws-events.subscriber';
import type { ControllerEventsHandler } from './controller-events.handler';
import type { OrchestratorEventsHandler } from './orchestrator-events.handler';
import type { InstanceEventsService } from './instance-events.service';

// Call-site audit (observability phase 4): the subscriber's log lines interpolate
// producer-controlled event fields and CloudEvents SDK error text. Whatever a
// publisher puts there, a bearer token must not reach the log in JSON mode.

const BEARER = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkd3MifQ.c2lnbmF0dXJl';

function baseEvent(overrides: Record<string, unknown> = {}) {
  return {
    specversion: '1.0',
    id: 'evt-1',
    source: '/dws/test',
    type: 'dws.example.unhandled',
    time: '2026-01-02T03:04:05.678Z',
    data: { ok: true },
    ...overrides,
  };
}

// Just enough of the Drizzle chain for runIdempotent's first-delivery path.
const fakeDb = {
  transaction: (work: (tx: unknown) => Promise<unknown>) =>
    work({
      insert: () => ({
        values: () => ({ onConflictDoNothing: () => ({ returning: async () => [{ eventId: 'evt-1' }] }) }),
      }),
    }),
} as unknown as Db;

describe('DwsEventsSubscriber logging', () => {
  let lines: string[];
  let subscriber: DwsEventsSubscriber;

  beforeEach(() => {
    lines = [];
    Logger.overrideLogger(new JsonLogger((line) => lines.push(line)));
    subscriber = new DwsEventsSubscriber(
      fakeDb,
      { canHandle: () => false } as unknown as ControllerEventsHandler,
      { canHandle: () => false } as unknown as OrchestratorEventsHandler,
      { publish: jest.fn() } as unknown as InstanceEventsService,
    );
  });

  it('never logs a bearer token echoed in a malformed-event error', async () => {
    await subscriber.process(baseEvent({ [`Authorization: Bearer ${BEARER}`]: 'x' }) as never);

    expect(lines).toHaveLength(1);
    expect(lines[0]).not.toContain('eyJhbGciOi');
    expect(JSON.parse(lines[0])).toMatchObject({ severity: 'WARN', logger: 'DwsEventsSubscriber' });
  });

  it('never logs a bearer token carried in an unhandled event type', async () => {
    await subscriber.process(baseEvent({ type: `dws.x Authorization: Bearer ${BEARER}` }) as never);

    expect(lines).toHaveLength(1);
    expect(lines[0]).not.toContain('eyJhbGciOi');
    expect(JSON.parse(lines[0])).toMatchObject({ severity: 'DEBUG' });
  });

  it('caps and flattens an oversized, multi-line reason', async () => {
    await subscriber.process(baseEvent({ type: `${'a'.repeat(500)}\nsecond line` }) as never);

    const { message } = JSON.parse(lines[0]) as { message: string };
    expect(message).not.toContain('second line');
    expect(message.length).toBeLessThan(300);
  });
});

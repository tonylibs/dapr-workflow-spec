import type { AddressInfo } from 'node:net';
import type { NestExpressApplication } from '@nestjs/platform-express';
import { ConfigService } from '@nestjs/config';
import { Test } from '@nestjs/testing';
import { sql } from 'drizzle-orm';
import type { AppConfig } from '../config/configuration';
import { configureAppTokenGuard } from '../http/app-token-guard';
import { configureBodyParsers } from '../http/body-parsers';
import { InstancesController } from '../instances/instances.controller';
import { InstancesService } from '../instances/instances.service';
import { DB } from '../store/store.module';
import { createTestDb, truncateAll } from '../test-support/test-db';
import { ControllerEventsHandler } from './controller-events.handler';
import { DaprSubscriptionController } from './dapr-subscription.controller';
import { DwsEventsSubscriber } from './dws-events.subscriber';
import { EventType } from './event-types';
import { InstanceEventsService } from './instance-events.service';
import { OrchestratorEventsHandler } from './orchestrator-events.handler';

const APP_TOKEN = 'app-token-0123456789abcdef';

// Integration: real Postgres (docker-compose up -d + pnpm db:migrate first).
// The pod-IP bypass this closes: <pod-ip>:3000/dapr/events/dws is reachable by
// any pod-network peer without going through the sidecar, so without the app
// token a forged lifecycle event would write the read model and push to live
// SSE clients. Boots the real subscription controller, subscriber and SSE route
// behind the same guard and parsers `bootstrap` registers.
describe('POST /dapr/events/dws behind the app API token (integration)', () => {
  const { db, close } = createTestDb();
  let app: NestExpressApplication;
  let origin: string;

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({
      controllers: [DaprSubscriptionController, InstancesController],
      providers: [
        { provide: DB, useValue: db },
        { provide: ConfigService, useValue: new ConfigService<AppConfig, true>({ dapr: { pubsubName: 'pubsub', topic: 'dws.events', controllerAppId: 'dws-controller' } }) },
        InstancesService,
        InstanceEventsService,
        ControllerEventsHandler,
        OrchestratorEventsHandler,
        DwsEventsSubscriber,
      ],
    }).compile();

    app = moduleRef.createNestApplication<NestExpressApplication>({ rawBody: true, logger: false });
    configureAppTokenGuard(app, APP_TOKEN);
    configureBodyParsers(app);
    await app.listen(0);
    origin = `http://127.0.0.1:${(app.getHttpServer().address() as AddressInfo).port}`;
  });

  afterEach(() => truncateAll(db));
  afterAll(async () => {
    await app?.close();
    await close();
  });

  let eventSeq = 0;
  /** A `dws.events` delivery exactly as Dapr posts it: the documented CloudEvent as the transport event's `data`. */
  const deliver = (instanceId: string, headers: Record<string, string> = {}) =>
    fetch(`${origin}/dapr/events/dws`, {
      method: 'POST',
      headers: { 'content-type': 'application/cloudevents+json', ...headers },
      body: JSON.stringify({
        specversion: '1.0',
        id: `transport-${instanceId}`,
        source: 'dws-orchestrator',
        type: 'com.dapr.event.sent',
        data: {
          specversion: '1.0',
          id: `evt-${++eventSeq}`,
          source: '/dws-orchestrator',
          type: EventType.InstanceStarted,
          time: '2026-07-24T00:00:00.000Z',
          datacontenttype: 'application/json',
          data: { instanceId, workflow: 'order-flow', version: 'order-flow@v1', appId: 'order-flow', startedAt: '2026-07-24T00:00:00.000Z' },
        },
      }),
    });

  /** Row counts of every read-model table the subscriber can write. */
  const rowCounts = async () => {
    const rows = await db.execute<{ table: string; n: number }>(sql`
      SELECT 'workflow_instances' AS "table", count(*)::int AS n FROM workflow_instances
      UNION ALL SELECT 'task_events', count(*)::int FROM task_events
      UNION ALL SELECT 'processed_events', count(*)::int FROM processed_events
      UNION ALL SELECT 'deployments', count(*)::int FROM deployments
      UNION ALL SELECT 'workflow_definitions', count(*)::int FROM workflow_definitions
      ORDER BY 1`);
    return Object.fromEntries(rows.map((row) => [row.table, row.n]));
  };

  it('rejects a forged event without the token and leaves the read model untouched', async () => {
    const before = await rowCounts();

    expect((await deliver('forged-1')).status).toBe(401);
    expect((await deliver('forged-2', { 'dapr-api-token': 'wrong' })).status).toBe(401);

    expect(await rowCounts()).toEqual(before);
    const listed = await fetch(`${origin}/instances`, { headers: { 'dapr-api-token': APP_TOKEN } });
    expect(JSON.stringify(await listed.json())).not.toContain('forged');
  });

  it('ingests the same event when the token is present', async () => {
    const response = await deliver('genuine-1', { 'dapr-api-token': APP_TOKEN });

    expect(response.status).toBe(201);
    expect(await response.json()).toEqual({ status: 'SUCCESS' });
    expect(await rowCounts()).toMatchObject({ workflow_instances: 1, processed_events: 1 });
    const instance = await fetch(`${origin}/instances/genuine-1`, { headers: { 'dapr-api-token': APP_TOKEN } });
    expect(instance.status).toBe(200);
    expect(await instance.json()).toMatchObject({ instanceId: 'genuine-1', status: 'started' });
  });

  // Ordering carries the proof, so there is no wait-for-silence: the stream is
  // open before the forged post, the genuine post comes after it, and the first
  // frame the client sees must be the genuine one.
  it('emits no SSE frame for a forged event, only for the authenticated one', async () => {
    const controller = new AbortController();
    const stream = await fetch(`${origin}/instances/events`, { headers: { 'dapr-api-token': APP_TOKEN }, signal: controller.signal });
    expect(stream.status).toBe(200);
    const reader = stream.body!.getReader();

    try {
      expect((await deliver('forged-3')).status).toBe(401);
      expect((await deliver('genuine-2', { 'dapr-api-token': APP_TOKEN })).status).toBe(201);

      const frame = await readUntil(reader, 'genuine-2');
      expect(frame).toContain('event: instance');
      expect(frame).not.toContain('forged-3');
    } finally {
      controller.abort();
      await reader.cancel().catch(() => undefined);
    }
  });

  it('serves the SSE stream only with the token', async () => {
    const response = await fetch(`${origin}/instances/events`);

    expect(response.status).toBe(401);
    expect(response.headers.get('content-type')).not.toContain('text/event-stream');
  });
});

/** Accumulates streamed text until it contains `needle`, failing after a bounded wait instead of hanging the suite. */
async function readUntil(reader: ReadableStreamDefaultReader<Uint8Array>, needle: string, timeoutMs = 5000): Promise<string> {
  const decoder = new TextDecoder();
  let text = '';
  const deadline = Date.now() + timeoutMs;
  while (!text.includes(needle)) {
    const remaining = deadline - Date.now();
    if (remaining <= 0) {
      throw new Error(`no SSE frame containing "${needle}" within ${timeoutMs}ms; received: ${JSON.stringify(text)}`);
    }
    const next = await Promise.race([
      reader.read(),
      new Promise<never>((_, reject) => setTimeout(() => reject(new Error(`timed out waiting for "${needle}"; received: ${JSON.stringify(text)}`)), remaining).unref()),
    ]);
    if (next.done) {
      throw new Error(`SSE stream ended before "${needle}"; received: ${JSON.stringify(text)}`);
    }
    text += decoder.decode(next.value, { stream: true });
  }
  return text;
}

import { NotFoundException, type MessageEvent } from '@nestjs/common';
import { CloudEvent } from 'cloudevents';
import { firstValueFrom, toArray, type Observable } from 'rxjs';
import { take } from 'rxjs/operators';
import { ControllerEventsHandler } from '../events/controller-events.handler';
import { DwsEventsSubscriber } from '../events/dws-events.subscriber';
import { EventType } from '../events/event-types';
import { InstanceEventsService } from '../events/instance-events.service';
import { OrchestratorEventsHandler } from '../events/orchestrator-events.handler';
import { workflowInstances } from '../store/schema';
import { createTestDb, truncateAll } from '../test-support/test-db';
import { InstancesController } from './instances.controller';
import { InstancesService } from './instances.service';

// Integration: real Postgres (docker-compose up -d + pnpm db:migrate first).
// Drives the whole ingest → commit → push path, so what these assert is that a
// consumed lifecycle event reaches an SSE subscriber, not just that the Subject
// works in isolation.
describe('Instances SSE push API (integration)', () => {
  const { db, close } = createTestDb();
  const instanceEvents = new InstanceEventsService();
  const service = new InstancesService(db);
  const controller = new InstancesController(service, instanceEvents);
  const subscriber = new DwsEventsSubscriber(db, new ControllerEventsHandler(), new OrchestratorEventsHandler(), instanceEvents);

  afterEach(() => truncateAll(db));
  afterAll(() => close());

  let eventSeq = 0;
  const ingest = (type: string, data: Record<string, unknown>) =>
    subscriber.process(
      new CloudEvent({
        id: `evt-${++eventSeq}`,
        source: '/dws-orchestrator',
        type,
        time: new Date().toISOString(),
        datacontenttype: 'application/json',
        data,
      }),
    );

  const instancePayload = (over: Record<string, unknown> = {}) => ({
    instanceId: 'inst-1',
    workflow: 'order-flow',
    version: 'order-flow@v1',
    appId: 'order-flow',
    startedAt: '2026-07-24T00:00:00.000Z',
    ...over,
  });

  const seedRunningInstance = () =>
    db.insert(workflowInstances).values({
      instanceId: 'inst-1',
      workflow: 'order-flow',
      version: 'order-flow@v1',
      appId: 'order-flow',
      status: 'started',
      startedAt: new Date('2026-07-24T00:00:00.000Z'),
      endedAt: null,
    });

  describe('GET /instances/:id/events', () => {
    it('pushes a task event ingested for that instance', async () => {
      await seedRunningInstance();
      const stream = await controller.streamInstance('inst-1');
      const received = collect(stream, 1);

      await ingest(EventType.TaskStarted, {
        instanceId: 'inst-1',
        taskName: 'checkInventory',
        taskType: 'call',
        timestamp: '2026-07-24T00:01:00.000Z',
      });

      const [message] = await received;
      expect(message.type).toBe('task');
      expect(message.data).toMatchObject({
        instanceId: 'inst-1',
        taskName: 'checkInventory',
        type: 'call',
        status: 'started',
        error: null,
      });
    });

    it('pushes the terminal status and then ends the stream', async () => {
      await seedRunningInstance();
      const stream = await controller.streamInstance('inst-1');
      // toArray resolves only on completion — so this also asserts the stream ends.
      const received = firstValueFrom(stream.pipe(toArray()));

      await ingest(EventType.TaskCompleted, {
        instanceId: 'inst-1',
        taskName: 'checkInventory',
        taskType: 'call',
        timestamp: '2026-07-24T00:02:00.000Z',
      });
      await ingest(EventType.InstanceCompleted, instancePayload({ endedAt: '2026-07-24T00:05:00.000Z' }));

      const messages = await received;
      expect(messages.map((m) => m.type)).toEqual(['task', 'instance']);
      expect(messages[1].data).toMatchObject({ instanceId: 'inst-1', status: 'completed' });
    });

    it('does not push events belonging to another instance', async () => {
      await seedRunningInstance();
      const stream = await controller.streamInstance('inst-1');
      const received = collect(stream, 1);

      await ingest(EventType.TaskStarted, {
        instanceId: 'inst-other',
        taskName: 'shipOrder',
        taskType: 'call',
        timestamp: '2026-07-24T00:01:00.000Z',
      });
      await ingest(EventType.TaskStarted, {
        instanceId: 'inst-1',
        taskName: 'checkInventory',
        taskType: 'call',
        timestamp: '2026-07-24T00:01:30.000Z',
      });

      const [message] = await received;
      expect(message.data).toMatchObject({ instanceId: 'inst-1', taskName: 'checkInventory' });
    });

    it('404s for an unknown instance id without opening a stream', async () => {
      await expect(controller.streamInstance('nope')).rejects.toBeInstanceOf(NotFoundException);
    });
  });

  describe('GET /instances/events', () => {
    it('pushes a status delta for any instance', async () => {
      const received = collect(controller.streamInstances(), 2);

      await ingest(EventType.InstanceStarted, instancePayload({ instanceId: 'inst-a' }));
      await ingest(EventType.InstanceCompleted, instancePayload({ instanceId: 'inst-b', endedAt: '2026-07-24T00:05:00.000Z' }));

      const messages = await received;
      expect(messages.map((m) => m.data)).toEqual([
        { instanceId: 'inst-a', status: 'started', endedAt: null },
        { instanceId: 'inst-b', status: 'completed', endedAt: new Date('2026-07-24T00:05:00.000Z') },
      ]);
    });

    it('does not push task events', async () => {
      const received = collect(controller.streamInstances(), 1);

      await ingest(EventType.TaskStarted, {
        instanceId: 'inst-a',
        taskName: 'checkInventory',
        taskType: 'call',
        timestamp: '2026-07-24T00:01:00.000Z',
      });
      await ingest(EventType.InstanceStarted, instancePayload({ instanceId: 'inst-a' }));

      const [message] = await received;
      expect(message.data).toMatchObject({ instanceId: 'inst-a', status: 'started' });
    });

    it('pushes nothing when the terminal ratchet suppresses a late started', async () => {
      await ingest(EventType.InstanceCompleted, instancePayload({ endedAt: '2026-07-24T00:05:00.000Z' }));

      const received = collect(controller.streamInstances(), 1);
      // Late 'started' for an already-completed instance: the upsert is a
      // no-op, so no client should be told the status went backwards.
      await ingest(EventType.InstanceStarted, instancePayload());
      await ingest(EventType.InstanceStarted, instancePayload({ instanceId: 'inst-later' }));

      const [message] = await received;
      expect(message.data).toMatchObject({ instanceId: 'inst-later', status: 'started' });
    });
  });
});

/** Resolves with the next `count` messages the stream emits. */
function collect(stream: Observable<MessageEvent>, count: number): Promise<MessageEvent[]> {
  return firstValueFrom(stream.pipe(take(count), toArray()));
}

import { eq } from 'drizzle-orm';
import { deployments, taskEvents, workflowDefinitions, workflowInstances } from '../store/schema';
import { createTestDb, truncateAll } from '../test-support/test-db';
import { runIdempotent } from './idempotent-handler';
import { insertTaskEvent, upsertDeploymentApplyOutcome, upsertWorkflowDefinition, upsertWorkflowInstance } from './upserts';
import type { DefinitionPayload, DeploymentPayload, InstancePayload, TaskPayload } from './event-types';

describe('read-model upserts', () => {
  const { db, close } = createTestDb();

  afterEach(async () => {
    await truncateAll(db);
  });

  afterAll(async () => {
    await close();
  });

  describe('upsertWorkflowDefinition', () => {
    it('is idempotent when the same event is replayed', async () => {
      const payload: DefinitionPayload = { workflow: 'order', version: 'v1', createdAt: '2026-07-24T00:00:00.000Z' };

      await runIdempotent(db, 'evt-def-1', (tx) => upsertWorkflowDefinition(tx, 'created', payload));
      await runIdempotent(db, 'evt-def-1', (tx) => upsertWorkflowDefinition(tx, 'created', payload));

      const rows = await db.select().from(workflowDefinitions).where(eq(workflowDefinitions.name, 'order'));
      expect(rows).toHaveLength(1);
    });
  });

  describe('upsertDeploymentApplyOutcome', () => {
    it('records the error field on a failed apply', async () => {
      const payload: DeploymentPayload = {
        workflow: 'order',
        version: 'v1',
        stepServices: ['check-inventory'],
        orchestratorAppId: 'order',
        error: 'boom: connection refused',
      };

      await runIdempotent(db, 'evt-dep-1', (tx) => upsertDeploymentApplyOutcome(tx, 'failed', payload));

      const [row] = await db.select().from(deployments).where(eq(deployments.workflow, 'order'));
      expect(row.status).toBe('failed');
      expect(row.orchestratorAppId).toBe('order');
    });
  });

  describe('upsertWorkflowInstance — idempotency and out-of-order delivery', () => {
    it('is idempotent when the same instance.started event is replayed', async () => {
      const payload: InstancePayload = {
        instanceId: 'inst-1',
        workflow: 'order',
        version: 'v1',
        appId: 'order',
        startedAt: '2026-07-24T00:00:00.000Z',
      };

      await runIdempotent(db, 'evt-inst-1', (tx) => upsertWorkflowInstance(tx, 'started', payload));
      await runIdempotent(db, 'evt-inst-1', (tx) => upsertWorkflowInstance(tx, 'started', payload));

      const rows = await db.select().from(workflowInstances).where(eq(workflowInstances.instanceId, 'inst-1'));
      expect(rows).toHaveLength(1);
    });

    it('creates a completed row when instance.completed arrives before instance.started', async () => {
      const completedPayload: InstancePayload = {
        instanceId: 'inst-2',
        workflow: 'order',
        version: 'v1',
        appId: 'order',
        startedAt: '2026-07-24T00:00:00.000Z',
        endedAt: '2026-07-24T00:05:00.000Z',
      };

      await runIdempotent(db, 'evt-inst-2-completed', (tx) => upsertWorkflowInstance(tx, 'completed', completedPayload));

      const [row] = await db.select().from(workflowInstances).where(eq(workflowInstances.instanceId, 'inst-2'));
      expect(row.status).toBe('completed');
      expect(row.endedAt).not.toBeNull();
    });

    it('does not regress status when instance.started arrives after instance.completed, and backfills startedAt', async () => {
      const startedPayload: InstancePayload = {
        instanceId: 'inst-3',
        workflow: 'order',
        version: 'v1',
        appId: 'order',
        startedAt: '2026-07-24T00:00:00.000Z',
      };
      const completedPayload: InstancePayload = {
        ...startedPayload,
        endedAt: '2026-07-24T00:05:00.000Z',
      };

      // completed arrives first
      await runIdempotent(db, 'evt-inst-3-completed', (tx) => upsertWorkflowInstance(tx, 'completed', completedPayload));
      // started arrives late
      await runIdempotent(db, 'evt-inst-3-started', (tx) => upsertWorkflowInstance(tx, 'started', startedPayload));

      const [row] = await db.select().from(workflowInstances).where(eq(workflowInstances.instanceId, 'inst-3'));
      expect(row.status).toBe('completed');
      expect(row.startedAt?.toISOString()).toBe('2026-07-24T00:00:00.000Z');
      expect(row.endedAt).not.toBeNull();
    });

    it('returns the resulting state for a write that moved the row, for live publication', async () => {
      const payload: InstancePayload = {
        instanceId: 'inst-live-1',
        workflow: 'order',
        version: 'v1',
        appId: 'order',
        startedAt: '2026-07-24T00:00:00.000Z',
        endedAt: '2026-07-24T00:05:00.000Z',
      };

      let emitted: Awaited<ReturnType<typeof upsertWorkflowInstance>> = null;
      await runIdempotent(db, 'evt-live-1', async (tx) => {
        emitted = await upsertWorkflowInstance(tx, 'completed', payload);
      });

      expect(emitted).toEqual({
        instanceId: 'inst-live-1',
        status: 'completed',
        startedAt: new Date('2026-07-24T00:00:00.000Z'),
        endedAt: new Date('2026-07-24T00:05:00.000Z'),
      });
    });

    it('returns null when the terminal ratchet suppresses a late started, so nothing is published', async () => {
      const startedPayload: InstancePayload = {
        instanceId: 'inst-live-2',
        workflow: 'order',
        version: 'v1',
        appId: 'order',
        startedAt: '2026-07-24T00:00:00.000Z',
      };
      const completedPayload: InstancePayload = { ...startedPayload, endedAt: '2026-07-24T00:05:00.000Z' };

      await runIdempotent(db, 'evt-live-2-completed', (tx) => upsertWorkflowInstance(tx, 'completed', completedPayload));

      let emitted: Awaited<ReturnType<typeof upsertWorkflowInstance>> = null;
      await runIdempotent(db, 'evt-live-2-started', async (tx) => {
        emitted = await upsertWorkflowInstance(tx, 'started', startedPayload);
      });

      expect(emitted).toBeNull();
    });

    it('never clears an already-known endedAt', async () => {
      const completedPayload: InstancePayload = {
        instanceId: 'inst-4',
        workflow: 'order',
        version: 'v1',
        appId: 'order',
        startedAt: '2026-07-24T00:00:00.000Z',
        endedAt: '2026-07-24T00:05:00.000Z',
      };
      const startedOnlyPayload: InstancePayload = {
        instanceId: 'inst-4',
        workflow: 'order',
        version: 'v1',
        appId: 'order',
        startedAt: '2026-07-24T00:00:00.000Z',
      };

      await runIdempotent(db, 'evt-inst-4-completed', (tx) => upsertWorkflowInstance(tx, 'completed', completedPayload));
      await runIdempotent(db, 'evt-inst-4-started', (tx) => upsertWorkflowInstance(tx, 'started', startedOnlyPayload));

      const [row] = await db.select().from(workflowInstances).where(eq(workflowInstances.instanceId, 'inst-4'));
      expect(row.endedAt?.toISOString()).toBe('2026-07-24T00:05:00.000Z');
    });
  });

  describe('insertTaskEvent', () => {
    it('returns the inserted row once, and null for a replay of the same event id', async () => {
      const payload: TaskPayload = {
        instanceId: 'inst-live-3',
        taskName: 'checkInventory',
        taskType: 'call',
        timestamp: '2026-07-24T00:00:00.000Z',
      };

      // Two distinct guard ids so both calls actually reach insertTaskEvent;
      // the conflict under test is on the task-event row id, not the
      // processed-events guard.
      let first: Awaited<ReturnType<typeof insertTaskEvent>> = null;
      let replay: Awaited<ReturnType<typeof insertTaskEvent>> = null;
      await runIdempotent(db, 'guard-task-live-1', async (tx) => {
        first = await insertTaskEvent(tx, 'evt-task-live', 'started', payload);
      });
      await runIdempotent(db, 'guard-task-live-2', async (tx) => {
        replay = await insertTaskEvent(tx, 'evt-task-live', 'started', payload);
      });

      expect(first).toEqual({
        instanceId: 'inst-live-3',
        id: 'evt-task-live',
        taskName: 'checkInventory',
        type: 'call',
        status: 'started',
        timestamp: new Date('2026-07-24T00:00:00.000Z'),
        error: null,
      });
      expect(replay).toBeNull();
    });

    it('records the error field on a failed task', async () => {
      const payload: TaskPayload = {
        instanceId: 'inst-5',
        taskName: 'checkInventory',
        taskType: 'call',
        timestamp: '2026-07-24T00:00:00.000Z',
        error: 'upstream 502',
      };

      await runIdempotent(db, 'evt-task-1', (tx) => insertTaskEvent(tx, 'evt-task-1', 'failed', payload));

      const rows = await db.select().from(taskEvents).where(eq(taskEvents.instanceId, 'inst-5'));
      expect(rows).toHaveLength(1);
      expect(rows[0].status).toBe('failed');
      expect(rows[0].error).toBe('upstream 502');
      expect(rows[0].type).toBe('call');
    });
  });
});

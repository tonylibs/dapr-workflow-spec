import { NotFoundException } from '@nestjs/common';
import { taskEvents, workflowInstances } from '../store/schema';
import { createTestDb, truncateAll } from '../test-support/test-db';
import { InstancesController } from './instances.controller';
import { InstancesService } from './instances.service';

// Integration: real Postgres (docker-compose up -d + pnpm db:migrate first).
describe('Instances read API (integration)', () => {
  const { db, close } = createTestDb();
  const service = new InstancesService(db);
  const controller = new InstancesController(service);

  afterEach(() => truncateAll(db));
  afterAll(() => close());

  const at = (iso: string) => new Date(iso);

  const seedInstance = (over: Partial<typeof workflowInstances.$inferInsert> & { instanceId: string }) =>
    db.insert(workflowInstances).values({
      workflow: 'order-flow',
      version: 'order-flow@v1',
      appId: 'order-flow',
      status: 'running',
      startedAt: at('2026-07-24T00:00:00Z'),
      endedAt: null,
      ...over,
    });

  describe('GET /instances', () => {
    it('lists instances most-recent first', async () => {
      await seedInstance({ instanceId: 'inst-old', startedAt: at('2026-07-24T00:00:00Z') });
      await seedInstance({ instanceId: 'inst-new', startedAt: at('2026-07-26T00:00:00Z') });

      const page = await controller.listInstances({ limit: 20 });
      expect(page.items.map((i) => i.instanceId)).toEqual(['inst-new', 'inst-old']);
    });

    it('filters by workflow and status combined', async () => {
      await seedInstance({ instanceId: 'a', workflow: 'order-flow', status: 'running' });
      await seedInstance({ instanceId: 'b', workflow: 'order-flow', status: 'completed' });
      await seedInstance({ instanceId: 'c', workflow: 'ship-flow', status: 'running' });

      const page = await controller.listInstances({ limit: 20, workflow: 'order-flow', status: 'running' });
      expect(page.items.map((i) => i.instanceId)).toEqual(['a']);
    });

    it('pages across the started_at null/non-null boundary exactly once', async () => {
      await seedInstance({ instanceId: 'dated-1', startedAt: at('2026-07-26T00:00:00Z') });
      await seedInstance({ instanceId: 'dated-2', startedAt: at('2026-07-25T00:00:00Z') });
      await seedInstance({ instanceId: 'null-1', startedAt: null });
      await seedInstance({ instanceId: 'null-2', startedAt: null });

      const seen: string[] = [];
      let cursor: string | undefined;
      for (let i = 0; i < 10; i++) {
        const page = await controller.listInstances({ limit: 1, cursor });
        seen.push(...page.items.map((x) => x.instanceId));
        if (!page.nextCursor) break;
        cursor = page.nextCursor;
      }

      expect(seen).toEqual(['dated-1', 'dated-2', 'null-1', 'null-2']);
      expect(new Set(seen).size).toBe(4);
    });
  });

  describe('GET /instances/:id', () => {
    it('returns the instance detail including appId', async () => {
      await seedInstance({ instanceId: 'inst-1', appId: 'order-flow' });
      const detail = await controller.getInstance('inst-1');
      expect(detail.instanceId).toBe('inst-1');
      expect(detail.appId).toBe('order-flow');
    });

    it('404s for an unknown instance id', async () => {
      await expect(controller.getInstance('nope')).rejects.toBeInstanceOf(NotFoundException);
    });
  });

  describe('GET /instances/:id/tasks', () => {
    it('returns task events ordered by timestamp ascending', async () => {
      await seedInstance({ instanceId: 'inst-1' });
      await db.insert(taskEvents).values([
        { id: 'evt-2', instanceId: 'inst-1', taskName: 'checkInventory', type: 'call', status: 'completed', timestamp: at('2026-07-24T00:02:00Z'), error: null },
        { id: 'evt-1', instanceId: 'inst-1', taskName: 'checkInventory', type: 'call', status: 'started', timestamp: at('2026-07-24T00:01:00Z'), error: null },
      ]);

      const page = await controller.listTasks('inst-1', { limit: 20 });
      expect(page.items.map((t) => t.id)).toEqual(['evt-1', 'evt-2']);
    });

    it('404s for an unknown instance id', async () => {
      await expect(controller.listTasks('nope', { limit: 20 })).rejects.toBeInstanceOf(NotFoundException);
    });
  });
});

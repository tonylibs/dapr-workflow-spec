import { NotFoundException } from '@nestjs/common';
import { deployments, workflowDefinitions } from '../store/schema';
import { createTestDb, truncateAll } from '../test-support/test-db';
import { WorkflowsController } from './workflows.controller';
import { WorkflowsService } from './workflows.service';

// Integration: real Postgres (docker-compose up -d + pnpm db:migrate first),
// per the test-support/test-db.ts convention.
describe('Workflows read API (integration)', () => {
  const { db, close } = createTestDb();
  const service = new WorkflowsService(db);
  const controller = new WorkflowsController(service);

  afterEach(() => truncateAll(db));
  afterAll(() => close());

  const at = (iso: string) => new Date(iso);

  describe('GET /workflows', () => {
    it('returns one row per name with its latest version by created_at', async () => {
      await db.insert(workflowDefinitions).values([
        { name: 'order-flow', version: 'order-flow@v1', status: 'active', createdAt: at('2026-07-24T00:00:00Z') },
        { name: 'order-flow', version: 'order-flow@v2', status: 'active', createdAt: at('2026-07-25T00:00:00Z') },
        { name: 'ship-flow', version: 'ship-flow@v1', status: 'active', createdAt: at('2026-07-24T00:00:00Z') },
      ]);

      const page = await controller.listWorkflows({ limit: 20 });

      expect(page.items).toHaveLength(2);
      const order = page.items.find((i) => i.name === 'order-flow');
      expect(order?.latestVersion).toBe('order-flow@v2');
      expect(page.nextCursor).toBeNull();
    });

    it('returns an empty page when there are no definitions', async () => {
      const page = await controller.listWorkflows({ limit: 20 });
      expect(page.items).toEqual([]);
      expect(page.nextCursor).toBeNull();
    });

    it('paginates by name across pages with no overlap', async () => {
      await db.insert(workflowDefinitions).values([
        { name: 'a-flow', version: 'a@v1', status: 'active', createdAt: at('2026-07-24T00:00:00Z') },
        { name: 'b-flow', version: 'b@v1', status: 'active', createdAt: at('2026-07-24T00:00:00Z') },
        { name: 'c-flow', version: 'c@v1', status: 'active', createdAt: at('2026-07-24T00:00:00Z') },
      ]);

      const first = await controller.listWorkflows({ limit: 2 });
      expect(first.items.map((i) => i.name)).toEqual(['a-flow', 'b-flow']);
      expect(first.nextCursor).not.toBeNull();

      const second = await controller.listWorkflows({ limit: 2, cursor: first.nextCursor! });
      expect(second.items.map((i) => i.name)).toEqual(['c-flow']);
      expect(second.nextCursor).toBeNull();
    });
  });

  describe('GET /workflows/:name', () => {
    it('returns the full version history newest first', async () => {
      await db.insert(workflowDefinitions).values([
        { name: 'order-flow', version: 'order-flow@v1', status: 'active', createdAt: at('2026-07-24T00:00:00Z') },
        { name: 'order-flow', version: 'order-flow@v2', status: 'active', createdAt: at('2026-07-25T00:00:00Z') },
        { name: 'order-flow', version: 'order-flow@v3', status: 'active', createdAt: at('2026-07-26T00:00:00Z') },
      ]);

      const page = await controller.listVersions('order-flow', { limit: 20 });
      expect(page.items.map((i) => i.version)).toEqual(['order-flow@v3', 'order-flow@v2', 'order-flow@v1']);
    });

    it('404s for an unknown workflow name', async () => {
      await expect(controller.listVersions('nope', { limit: 20 })).rejects.toBeInstanceOf(NotFoundException);
    });
  });

  describe('GET /workflows/:name/deployments', () => {
    it('returns deployments for the workflow', async () => {
      await db.insert(workflowDefinitions).values({
        name: 'order-flow',
        version: 'order-flow@v1',
        status: 'active',
        createdAt: at('2026-07-24T00:00:00Z'),
      });
      await db.insert(deployments).values({
        workflow: 'order-flow',
        version: 'order-flow@v1',
        stepServices: ['check-inventory'],
        orchestratorAppId: 'order-flow',
        status: 'applied',
        drainedAt: null,
      });

      const page = await controller.listDeployments('order-flow', { limit: 20 });
      expect(page.items).toHaveLength(1);
      expect(page.items[0].status).toBe('applied');
      expect(page.items[0].stepServices).toEqual(['check-inventory']);
    });

    it('returns an empty page for a known workflow with no deployments', async () => {
      await db.insert(workflowDefinitions).values({
        name: 'order-flow',
        version: 'order-flow@v1',
        status: 'active',
        createdAt: at('2026-07-24T00:00:00Z'),
      });
      const page = await controller.listDeployments('order-flow', { limit: 20 });
      expect(page.items).toEqual([]);
    });

    it('404s for an unknown workflow name', async () => {
      await expect(controller.listDeployments('nope', { limit: 20 })).rejects.toBeInstanceOf(NotFoundException);
    });
  });
});

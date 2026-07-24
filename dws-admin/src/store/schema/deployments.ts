import { jsonb, pgTable, text, timestamp, unique } from 'drizzle-orm/pg-core';

export const deployments = pgTable(
  'deployments',
  {
    workflow: text('workflow').notNull(),
    version: text('version').notNull(),
    stepServices: jsonb('step_services').$type<string[]>().notNull(),
    orchestratorAppId: text('orchestrator_app_id').notNull(),
    status: text('status').notNull(),
    drainedAt: timestamp('drained_at', { withTimezone: true }),
  },
  (table) => [unique('deployments_workflow_version').on(table.workflow, table.version)],
);

export type Deployment = typeof deployments.$inferSelect;
export type NewDeployment = typeof deployments.$inferInsert;

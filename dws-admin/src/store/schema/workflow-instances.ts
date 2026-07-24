import { pgTable, text, timestamp } from 'drizzle-orm/pg-core';

export const workflowInstances = pgTable('workflow_instances', {
  instanceId: text('instance_id').primaryKey(),
  workflow: text('workflow').notNull(),
  version: text('version').notNull(),
  appId: text('app_id').notNull(),
  status: text('status').notNull(),
  startedAt: timestamp('started_at', { withTimezone: true }),
  endedAt: timestamp('ended_at', { withTimezone: true }),
});

export type WorkflowInstance = typeof workflowInstances.$inferSelect;
export type NewWorkflowInstance = typeof workflowInstances.$inferInsert;

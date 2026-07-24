import { pgTable, text, timestamp, unique } from 'drizzle-orm/pg-core';

export const workflowDefinitions = pgTable(
  'workflow_definitions',
  {
    name: text('name').notNull(),
    version: text('version').notNull(),
    status: text('status').notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull(),
  },
  (table) => [unique('workflow_definitions_name_version').on(table.name, table.version)],
);

export type WorkflowDefinition = typeof workflowDefinitions.$inferSelect;
export type NewWorkflowDefinition = typeof workflowDefinitions.$inferInsert;

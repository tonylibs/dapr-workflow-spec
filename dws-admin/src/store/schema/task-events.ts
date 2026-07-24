import { index, pgTable, text, timestamp } from 'drizzle-orm/pg-core';

// instanceId intentionally has no DB-enforced foreign key: task.* events can be
// delivered before the corresponding instance.* event under at-least-once,
// unordered Dapr pub/sub delivery, and a hard FK would fail that insert. The
// relationship to workflow_instances is logical/eventual, not DB-enforced.
export const taskEvents = pgTable(
  'task_events',
  {
    id: text('id').primaryKey(),
    instanceId: text('instance_id').notNull(),
    taskName: text('task_name').notNull(),
    type: text('type').notNull(),
    status: text('status').notNull(),
    timestamp: timestamp('timestamp', { withTimezone: true }).notNull(),
    error: text('error'),
  },
  (table) => [index('task_events_instance_id_idx').on(table.instanceId)],
);

export type TaskEvent = typeof taskEvents.$inferSelect;
export type NewTaskEvent = typeof taskEvents.$inferInsert;

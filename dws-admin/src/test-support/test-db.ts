import { sql } from 'drizzle-orm';
import { drizzle } from 'drizzle-orm/postgres-js';
import postgres from 'postgres';
import * as schema from '../store/schema';
import type { Db } from '../store/db.type';

// Defaults to the docker-compose Postgres (see ../../docker-compose.yml); override
// with TEST_DATABASE_URL to point at a different instance (e.g. this repo's CI).
// These specs run migrations-applied schema assumptions only, no seed data, so
// reusing the local dev database is safe — it's expected to be disposable.
const TEST_DATABASE_URL = process.env.TEST_DATABASE_URL ?? process.env.DATABASE_URL ?? 'postgres://dws:dws@localhost:5433/dws_admin';

export function createTestDb(): { db: Db; close: () => Promise<void> } {
  const client = postgres(TEST_DATABASE_URL, { max: 1 });
  const db = drizzle(client, { schema }) as unknown as Db;
  return { db, close: () => client.end() };
}

export async function truncateAll(db: Db): Promise<void> {
  await db.execute(
    sql`TRUNCATE TABLE processed_events, task_events, workflow_instances, deployments, workflow_definitions RESTART IDENTITY CASCADE`,
  );
}

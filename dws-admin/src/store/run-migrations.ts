import path from 'node:path';
import postgres from 'postgres';
import { drizzle } from 'drizzle-orm/postgres-js';
import { migrate } from 'drizzle-orm/postgres-js/migrator';

export async function runMigrations(databaseUrl: string): Promise<void> {
  // postgres.js console.logs server notices (drizzle's `CREATE SCHEMA IF NOT
  // EXISTS` emits one on every boot) as a multi-line object dump, which would
  // break the one-JSON-object-per-line stdout contract under DWS_LOG_FORMAT=json.
  const migrationClient = postgres(databaseUrl, { max: 1, onnotice: () => undefined });
  try {
    const db = drizzle(migrationClient);
    await migrate(db, { migrationsFolder: path.resolve(__dirname, '../../drizzle') });
  } finally {
    await migrationClient.end();
  }
}

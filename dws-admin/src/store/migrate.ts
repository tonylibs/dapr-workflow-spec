import { runMigrations } from './run-migrations';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) {
  throw new Error('Missing required environment variable: DATABASE_URL');
}

runMigrations(databaseUrl)
  .then(() => {
    console.log('Migrations applied.');
    process.exit(0);
  })
  .catch((err) => {
    console.error('Migration failed:', err);
    process.exit(1);
  });

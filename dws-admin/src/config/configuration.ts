import { parseCorsOrigins } from './cors';

export interface AppConfig {
  port: number;
  databaseUrl: string;
  runMigrationsOnBoot: boolean;
  // Origins allowed to call the read API from a browser. See config/cors.ts.
  corsOrigins: string[];
  dapr: {
    pubsubName: string;
    topic: string;
    serverHost?: string;
    // The port @dbc-tech/nest-dapr's DaprServer listens on for Dapr sidecar
    // callbacks (pubsub delivery, bindings). This is a *separate* HTTP
    // listener from Nest's own Express app (`port` above) — it must be a
    // different port, and `dapr run --app-port` must target this one, not
    // `port`. See design.md D3/Risks for why the two can't be merged.
    appPort: string;
    daprHost?: string;
    daprPort?: string;
    // Dapr app-id the compile/submit relay invokes on this pod's sidecar. In
    // cluster this is `<release>-controller`; it must match the app-id the
    // controller Pod carries via `dapr.io/app-id`.
    controllerAppId: string;
  };
}

function requireEnv(env: NodeJS.ProcessEnv, key: string): string {
  const value = env[key];
  if (!value) {
    throw new Error(`Missing required environment variable: ${key}`);
  }
  return value;
}

export default function configuration(): AppConfig {
  const env = process.env;
  return {
    port: Number(env.PORT ?? 3000),
    databaseUrl: requireEnv(env, 'DATABASE_URL'),
    runMigrationsOnBoot: (env.RUN_MIGRATIONS_ON_BOOT ?? 'true') !== 'false',
    corsOrigins: parseCorsOrigins(env.CORS_ORIGINS),
    dapr: {
      pubsubName: env.DAPR_PUBSUB_NAME ?? 'pubsub',
      topic: env.DAPR_PUBSUB_TOPIC ?? 'dws.events',
      serverHost: env.DAPR_SERVER_HOST,
      appPort: env.DAPR_APP_PORT ?? '3001',
      daprHost: env.DAPR_HOST,
      daprPort: env.DAPR_HTTP_PORT,
      controllerAppId: env.DAPR_CONTROLLER_APP_ID ?? 'dws-controller',
    },
  };
}

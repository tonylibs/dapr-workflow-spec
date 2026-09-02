export interface AppConfig {
  port: number;
  databaseUrl: string;
  runMigrationsOnBoot: boolean;
  dapr: {
    pubsubName: string;
    topic: string;
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
    dapr: {
      pubsubName: env.DAPR_PUBSUB_NAME ?? 'pubsub',
      topic: env.DAPR_PUBSUB_TOPIC ?? 'dws.events',
      daprHost: env.DAPR_HOST,
      daprPort: env.DAPR_HTTP_PORT,
      controllerAppId: env.DAPR_CONTROLLER_APP_ID ?? 'dws-controller',
    },
  };
}

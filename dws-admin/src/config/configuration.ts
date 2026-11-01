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
  // Dapr's two machine-auth tokens (auth roadmap Phase 10). Each is undefined
  // when unset or blank, which switches that half of the feature off.
  // `apiToken` (DAPR_API_TOKEN): presented to this pod's own sidecar.
  apiToken?: string;
  // `appToken` (APP_API_TOKEN): the value the sidecar attaches as
  // `dapr-api-token` on every call into this app; the app port rejects the rest.
  appToken?: string;
}

function requireEnv(env: NodeJS.ProcessEnv, key: string): string {
  const value = env[key];
  if (!value) {
    throw new Error(`Missing required environment variable: ${key}`);
  }
  return value;
}

/** Unset, empty and whitespace-only all mean "feature off"; a real value is kept verbatim. */
function optionalToken(env: NodeJS.ProcessEnv, key: string): string | undefined {
  const value = env[key];
  return value !== undefined && value.trim() !== '' ? value : undefined;
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
    apiToken: optionalToken(env, 'DAPR_API_TOKEN'),
    appToken: optionalToken(env, 'APP_API_TOKEN'),
  };
}

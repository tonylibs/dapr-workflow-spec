/**
 * Entrypoint for dws-call-openapi. Builds the app, performs full initialization
 * (fail fast: any config or document error exits non-zero), then starts
 * listening. Invoked by dws-orchestrator via Dapr service invocation; runs as a
 * scale-to-zero Knative service alongside a Dapr sidecar.
 */
import { buildApp } from './app.js';

async function main(): Promise<void> {
  const app = buildApp();

  try {
    await app.ready();
  } catch (err) {
    app.log.error({ err }, 'initialization failed');
    process.exit(1);
  }

  const port = app.config.port;
  try {
    await app.listen({ host: '0.0.0.0', port });
  } catch (err) {
    app.log.error({ err }, 'server failed to start');
    process.exit(1);
  }
}

void main();

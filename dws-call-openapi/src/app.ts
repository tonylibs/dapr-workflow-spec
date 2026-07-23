/**
 * Builds the Fastify application: registers the config, openapi, and runner
 * plugins (in dependency order) plus the routes. Awaiting `ready()` on the
 * returned instance performs full initialization and throws on any
 * misconfiguration, which the entrypoint turns into a non-zero exit.
 */
import Fastify, { type FastifyInstance, type FastifyServerOptions } from 'fastify';
import configPlugin from './plugins/config.js';
import openapiPlugin from './plugins/openapi.js';
import runnerPlugin from './plugins/runner.js';
import { routes } from './routes.js';

export interface BuildAppOptions {
  readonly env?: Record<string, string | undefined>;
  readonly logger?: FastifyServerOptions['logger'];
}

/** Constructs the app. The caller must `await app.ready()` (or `listen`). */
export function buildApp(opts: BuildAppOptions = {}): FastifyInstance {
  const env = opts.env ?? process.env;
  const app = Fastify({ logger: opts.logger ?? { level: process.env.LOG_LEVEL ?? 'info' } });

  // Treat an empty JSON body as empty workflow data rather than a parse error.
  app.removeContentTypeParser('application/json');
  app.addContentTypeParser('application/json', { parseAs: 'string' }, (_req, body, done) => {
    const text = typeof body === 'string' ? body.trim() : '';
    if (text === '') {
      done(null, {});
      return;
    }
    try {
      done(null, JSON.parse(text));
    } catch (err) {
      const error = err as Error & { statusCode?: number };
      error.statusCode = 400;
      done(error, undefined);
    }
  });

  app.register(configPlugin, { env });
  app.register(openapiPlugin);
  app.register(runnerPlugin);
  app.register(routes);

  return app;
}

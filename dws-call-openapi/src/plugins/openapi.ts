/**
 * Fastify plugin: builds the request-time engine from `config` (fetch, verify,
 * dereference, resolve operation, compile validators, resolve auth, build the
 * client) and decorates the instance with `engine`. If anything fails, the
 * plugin rejects and `ready()` throws, so startup fails fast.
 */
import fp from 'fastify-plugin';
import type { FastifyInstance } from 'fastify';
import { buildEngine, type Engine } from '../openapi/engine.js';

declare module 'fastify' {
  interface FastifyInstance {
    readonly engine: Engine;
  }
}

async function openapiPlugin(fastify: FastifyInstance): Promise<void> {
  const engine = await buildEngine(fastify.config);
  fastify.decorate('engine', engine);
  fastify.log.info(
    {
      task: engine.config.task,
      operationId: engine.template.operationId,
      method: engine.template.method,
      path: engine.template.path,
      output: engine.config.output,
      authType: engine.config.auth.type,
      timeoutMs: engine.config.timeoutMs,
    },
    'engine initialized',
  );
}

export default fp(openapiPlugin, { name: 'openapi', dependencies: ['config'] });

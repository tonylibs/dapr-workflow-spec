/**
 * Fastify plugin: builds the request-time engine from `config` (fetch, verify,
 * validate, parse, resolve operation, compile the payload validator) and
 * decorates the instance with `engine`. If anything fails, the plugin rejects
 * and `ready()` throws, so startup fails fast.
 */
import fp from 'fastify-plugin';
import type { FastifyInstance } from 'fastify';
import { buildEngine, type Engine } from '../asyncapi/engine.js';

declare module 'fastify' {
  interface FastifyInstance {
    readonly engine: Engine;
  }
}

async function asyncapiPlugin(fastify: FastifyInstance): Promise<void> {
  const engine = await buildEngine(fastify.config);
  fastify.decorate('engine', engine);
  fastify.log.info(
    {
      task: engine.config.task,
      operationId: engine.template.operationId,
      action: engine.template.action,
      channel: engine.template.channelName,
      address: engine.template.address,
      bindingName: engine.config.bindingName,
      operation: engine.config.operation,
      output: engine.config.output,
      timeoutMs: engine.config.timeoutMs,
    },
    'engine initialized',
  );
}

export default fp(asyncapiPlugin, { name: 'asyncapi', dependencies: ['config'] });

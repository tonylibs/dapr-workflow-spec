/**
 * Fastify plugin: decorates the instance with `runOperation`, bound to the
 * initialized engine. Keeps the route handler thin and the runner testable in
 * isolation.
 */
import fp from 'fastify-plugin';
import type { FastifyInstance } from 'fastify';
import { runOperation } from '../runner.js';

declare module 'fastify' {
  interface FastifyInstance {
    runOperation(input: Record<string, unknown>): Promise<unknown>;
  }
}

async function runnerPlugin(fastify: FastifyInstance): Promise<void> {
  fastify.decorate('runOperation', (input: Record<string, unknown>) => runOperation(fastify.engine, input));
}

export default fp(runnerPlugin, { name: 'runner', dependencies: ['openapi'] });

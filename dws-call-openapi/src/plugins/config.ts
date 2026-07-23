/**
 * Fastify plugin: loads and validates step configuration from the provided
 * environment and decorates the instance with `config`. Registered first so
 * later plugins can depend on it.
 */
import fp from 'fastify-plugin';
import type { FastifyInstance } from 'fastify';
import { loadConfig, type Config } from '../config/config.js';

export interface ConfigPluginOptions {
  readonly env: Record<string, string | undefined>;
}

declare module 'fastify' {
  interface FastifyInstance {
    readonly config: Config;
  }
}

async function configPlugin(fastify: FastifyInstance, opts: ConfigPluginOptions): Promise<void> {
  const config = loadConfig(opts.env);
  fastify.decorate('config', config);
}

export default fp(configPlugin, { name: 'config' });

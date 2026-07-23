/**
 * HTTP surface for the step: POST /run and GET /healthz. /run executes the
 * operation and shapes the response per OUTPUT; failures map to the platform
 * error contract (400 for bad input, 502 for upstream/transport, 500 otherwise).
 * /healthz reports readiness — it is only reachable once all plugins (and thus
 * full initialization) have completed.
 */
import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { BindingError, TransportError, UpstreamError } from './runner.js';

export async function routes(fastify: FastifyInstance): Promise<void> {
  const task = fastify.config.task;

  fastify.get('/healthz', async () => ({ status: 'ok', task }));

  fastify.post('/run', async (request: FastifyRequest, reply: FastifyReply) => {
    const input = asInputObject(request.body);
    if (input === undefined) {
      request.log.warn({ task }, 'invalid request body: not a JSON object');
      return reply.code(400).send({ task, error: 'request body must be a JSON object' });
    }

    const start = process.hrtime.bigint();
    try {
      const output = await fastify.runOperation(input);
      logRun(request, task, 200, start);
      return output;
    } catch (err) {
      return handleRunError(request, reply, task, start, err);
    }
  });
}

function handleRunError(
  request: FastifyRequest,
  reply: FastifyReply,
  task: string,
  start: bigint,
  err: unknown,
): FastifyReply {
  if (err instanceof BindingError) {
    logRun(request, task, 400, start);
    return reply.code(400).send({ task, error: err.message, details: err.issues });
  }
  if (err instanceof UpstreamError) {
    logRun(request, task, 502, start, { upstreamStatus: err.status });
    return reply.code(502).send({ task, status: err.status, body: err.body });
  }
  if (err instanceof TransportError) {
    logRun(request, task, 502, start);
    return reply.code(502).send({ task, error: err.message });
  }
  request.log.error({ task, err }, 'run failed');
  logRun(request, task, 500, start);
  return reply.code(500).send({ task, error: err instanceof Error ? err.message : String(err) });
}

/** Reads the request body as a JSON object; `{}` for empty, `undefined` if not an object. */
function asInputObject(body: unknown): Record<string, unknown> | undefined {
  if (body === undefined || body === null) return {};
  if (typeof body !== 'object' || Array.isArray(body)) return undefined;
  return body as Record<string, unknown>;
}

function logRun(
  request: FastifyRequest,
  task: string,
  status: number,
  start: bigint,
  extra: Record<string, unknown> = {},
): void {
  const durationMs = Number(process.hrtime.bigint() - start) / 1e6;
  request.log.info({ task, status, durationMs, ...extra }, 'run');
}

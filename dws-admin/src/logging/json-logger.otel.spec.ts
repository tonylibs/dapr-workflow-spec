import { Logger } from '@nestjs/common';
import { JsonLogger } from './json-logger';

// Integration-style check for the trace-correlation path. The OpenTelemetry
// Node agent (injected by the Operator via `--require`) registers its tracer
// provider and context manager on a process-global keyed by
// `Symbol.for('opentelemetry.js.api.1')`; `JsonLogger` reads the active span
// through its own `@opentelemetry/api` import and must see that global.
//
// To prove that and not just "same module instance", the "agent" side is
// loaded in an isolated module registry, so it holds a *different* copy of
// `@opentelemetry/api` than the one `json-logger.ts` imported at the top.

type Api = typeof import('@opentelemetry/api');

describe('JsonLogger with an agent-registered tracer provider', () => {
  let agentApi: Api;
  let shutdown: () => Promise<void>;
  let lines: string[];
  let log: Logger;

  const records = () => lines.map((line) => JSON.parse(line) as Record<string, unknown>);

  beforeAll(async () => {
    await jest.isolateModulesAsync(async () => {
      agentApi = await import('@opentelemetry/api');
      const { TracerProvider, SimpleSpanProcessor, InMemorySpanExporter } = await import('@opentelemetry/sdk-trace');
      const { AsyncLocalStorageContextManager } = await import('@opentelemetry/context-async-hooks');

      const provider = new TracerProvider({ spanProcessors: [new SimpleSpanProcessor({ exporter: new InMemorySpanExporter() })] });
      agentApi.context.setGlobalContextManager(new AsyncLocalStorageContextManager().enable());
      agentApi.trace.setGlobalTracerProvider(provider);
      shutdown = () => provider.shutdown();
    });
  });

  afterAll(async () => {
    await shutdown();
    agentApi.trace.disable();
    agentApi.context.disable();
  });

  beforeEach(() => {
    lines = [];
    Logger.overrideLogger(new JsonLogger((line) => lines.push(line)));
    log = new Logger('dws.otel');
  });

  it('registers through a different copy of the api than the logger imports', async () => {
    const ownApi = await import('@opentelemetry/api');
    expect(ownApi.trace).not.toBe(agentApi.trace);
  });

  it('stamps lines logged inside startActiveSpan, including after an await', async () => {
    const tracer = agentApi.trace.getTracer('test');
    let expected: { traceId: string; spanId: string } | undefined;

    log.log('before span');
    await tracer.startActiveSpan('GET /instances', async (span) => {
      expected = span.spanContext();
      log.log('start of handler');
      await new Promise((resolve) => setTimeout(resolve, 5));
      await Promise.resolve();
      log.log('after await');
      span.end();
    });
    log.log('after span');

    expect(expected?.traceId).toMatch(/^[0-9a-f]{32}$/);
    const [before, start, afterAwait, after] = records();
    expect(before).not.toHaveProperty('trace_id');
    expect(start).toMatchObject({ trace_id: expected?.traceId, span_id: expected?.spanId });
    expect(afterAwait).toMatchObject({ trace_id: expected?.traceId, span_id: expected?.spanId, message: 'after await' });
    expect(after).not.toHaveProperty('trace_id');
    expect(after).not.toHaveProperty('span_id');
  });

  it('reports the innermost span for nested spans', async () => {
    const tracer = agentApi.trace.getTracer('test');
    await tracer.startActiveSpan('outer', async (outer) => {
      await tracer.startActiveSpan('inner', async (inner) => {
        log.log('inside inner');
        inner.end();
        expect(records()[0]).toMatchObject({
          trace_id: outer.spanContext().traceId,
          span_id: inner.spanContext().spanId,
        });
        expect(inner.spanContext().spanId).not.toBe(outer.spanContext().spanId);
      });
      outer.end();
    });
  });
});

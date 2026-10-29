import { Logger } from '@nestjs/common';
import { context, ROOT_CONTEXT, trace, TraceFlags } from '@opentelemetry/api';
import { AsyncLocalStorageContextManager } from '@opentelemetry/context-async-hooks';
import { isJsonLogFormat, JsonLogger, scrub } from './json-logger';

// Conformance test for docs/log-format.md: the 12 golden cases, run against the
// NestJS logging front door (`new Logger('dws.golden')` -> JsonLogger).

const TRACE_ID = '4bf92f3577b34da6a3ce929d0e0e4736';
const SPAN_ID = '00f067aa0ba902b7';
const TIMESTAMP = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$/;
const BEARER = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkd3MifQ.c2lnbmF0dXJl';

class IllegalStateError extends Error {}

type Line = Record<string, unknown>;

describe('JsonLogger golden cases', () => {
  let lines: string[];
  let log: Logger;

  const only = (): { raw: string; record: Line } => {
    expect(lines).toHaveLength(1);
    const raw = lines[0];
    // Exactly one newline-terminated line, and it parses as one JSON object.
    expect(raw.endsWith('\n')).toBe(true);
    expect(raw.slice(0, -1)).not.toContain('\n');
    return { raw, record: JSON.parse(raw) as Line };
  };

  const inSpan = (traceId: string, spanId: string, fn: () => void) => {
    const span = trace.wrapSpanContext({ traceId, spanId, traceFlags: TraceFlags.SAMPLED });
    context.with(trace.setSpan(ROOT_CONTEXT, span), fn);
  };

  beforeAll(() => {
    context.setGlobalContextManager(new AsyncLocalStorageContextManager().enable());
  });

  afterAll(() => {
    context.disable();
  });

  beforeEach(() => {
    lines = [];
    Logger.overrideLogger(new JsonLogger((line) => lines.push(line)));
    log = new Logger('dws.golden');
  });

  it('1 plain: four required keys, no trace context', () => {
    log.log('Starting Dapr workflow runtime');
    const { record } = only();
    expect(Object.keys(record)).toEqual(['timestamp', 'severity', 'logger', 'message']);
    expect(record.timestamp).toMatch(TIMESTAMP);
    expect(record).toMatchObject({ severity: 'INFO', logger: 'dws.golden', message: 'Starting Dapr workflow runtime' });
    expect(record).not.toHaveProperty('trace_id');
    expect(record).not.toHaveProperty('span_id');
  });

  it('2 in-span: carries the active span ids', () => {
    inSpan(TRACE_ID, SPAN_ID, () => log.log('Invoking step'));
    const { record } = only();
    expect(record).toMatchObject({ severity: 'INFO', message: 'Invoking step', trace_id: TRACE_ID, span_id: SPAN_ID });
  });

  it('3 out-of-span: WARN without ids', () => {
    log.warn('Definition store not reachable, retrying');
    const { record } = only();
    expect(record).toMatchObject({ severity: 'WARN', message: 'Definition store not reachable, retrying' });
    expect(record).not.toHaveProperty('trace_id');
    expect(record).not.toHaveProperty('span_id');
  });

  it('4 invalid-context: all-zero ids are treated as absent', () => {
    inSpan('0'.repeat(32), '0'.repeat(16), () => log.log('Invalid context'));
    const { record } = only();
    expect(record).toMatchObject({ severity: 'INFO', message: 'Invalid context' });
    expect(record).not.toHaveProperty('trace_id');
    expect(record).not.toHaveProperty('span_id');
  });

  it('5 exception: exception.* keys on one line (stack string form)', () => {
    const err = new IllegalStateError('boom');
    log.error('Activity failed', err.stack);
    const { record } = only();
    expect(record).toMatchObject({ severity: 'ERROR', logger: 'dws.golden', message: 'Activity failed' });
    // A bare stack string only knows the `name` V8 printed on its first line.
    expect(record['exception.type']).toBe('Error');
    expect(record['exception.message']).toBe('boom');
    expect(typeof record['exception.stacktrace']).toBe('string');
    expect(record['exception.stacktrace']).toContain('boom');
  });

  it('5 exception: an Error object gets the same keys', () => {
    log.error('Activity failed', new IllegalStateError('boom'));
    const { record } = only();
    expect(record).toMatchObject({ severity: 'ERROR', message: 'Activity failed' });
    expect(record['exception.type']).toBe('IllegalStateError');
    expect(record['exception.message']).toBe('boom');
    expect(record['exception.stacktrace']).toContain('boom');
  });

  it('5 exception: a stack with no registered type is read from its first line', () => {
    log.error('Activity failed', 'TypeError: boom\n    at run (/app/dist/x.js:1:1)');
    const { record } = only();
    expect(record['exception.type']).toBe('TypeError');
    expect(record['exception.message']).toBe('boom');
  });

  it('5 exception: error() without a stack carries no exception keys', () => {
    log.error('Just an error');
    const { record } = only();
    expect(record).toMatchObject({ severity: 'ERROR', message: 'Just an error' });
    expect(Object.keys(record).some((key) => key.startsWith('exception.'))).toBe(false);
  });

  it('6 multi-line: newline stays inside message, one line', () => {
    log.log('line one\nline two');
    const { record } = only();
    expect(record.message).toBe('line one\nline two');
  });

  it('7 bearer: Authorization header value never appears', () => {
    log.log(`calling admin with Authorization: Bearer ${BEARER}`);
    const { raw, record } = only();
    expect(raw).not.toContain('eyJhbGciOi');
    expect(record.message).toBe('calling admin with Authorization: [REDACTED]');
  });

  it('8 bare-bearer: a bare Bearer credential is redacted', () => {
    log.warn('upstream rejected Bearer eyJhbGciOiJIUzI1NiJ9.e30.sig12345');
    const { raw, record } = only();
    expect(raw).not.toContain('eyJhbGciOi');
    expect(record).toMatchObject({ severity: 'WARN', message: 'upstream rejected Bearer [REDACTED]' });
  });

  it('9 password: credential-named key value is redacted', () => {
    log.log('connecting with user=dws password=hunter2');
    const { raw, record } = only();
    expect(raw).not.toContain('hunter2');
    expect(record.message).toBe('connecting with user=dws password=[REDACTED]');
  });

  it('10 json-secret: quoted JSON value is redacted and the line stays valid JSON', () => {
    log.log('payload {"client_secret":"s3cr3t-value","scope":"read"}');
    const { raw, record } = only();
    expect(raw).not.toContain('s3cr3t-value');
    expect(record.message).toBe('payload {"client_secret":"[REDACTED]","scope":"read"}');
  });

  it('11 exception-secret: message and stacktrace are scrubbed', () => {
    log.error('Call failed', new IllegalStateError('401 for token=abc12345'));
    const { raw, record } = only();
    expect(raw).not.toContain('abc12345');
    expect(record['exception.message']).toBe('401 for token=[REDACTED]');
    expect(record['exception.stacktrace']).toContain('token=[REDACTED]');
  });

  it('12 fatal: severity FATAL', () => {
    log.fatal('Runtime terminated');
    const { record } = only();
    expect(record).toMatchObject({ severity: 'FATAL', message: 'Runtime terminated' });
  });

  it.each([
    ['verbose', 'TRACE'],
    ['debug', 'DEBUG'],
    ['log', 'INFO'],
    ['warn', 'WARN'],
    ['error', 'ERROR'],
    ['fatal', 'FATAL'],
  ] as const)('maps Nest %s to %s', (level, severity) => {
    log[level]('level check');
    expect(only().record.severity).toBe(severity);
  });

  it('uses a fallback logger name when Nest passes no context', () => {
    new JsonLogger((line) => lines.push(line)).log('no context');
    expect(only().record.logger).toBe('nest');
  });

  it('renders object messages as JSON and never throws on circular ones', () => {
    const circular: Record<string, unknown> = { a: 1 };
    circular.self = circular;
    log.log({ a: 1 });
    log.log(circular);
    expect(JSON.parse(lines[0]).message).toBe('{"a":1}');
    expect(JSON.parse(lines[1]).message).toContain('a: 1');
  });
});

describe('JsonLogger default sink', () => {
  it('writes the record to process.stdout', () => {
    const spy = jest.spyOn(process.stdout, 'write').mockImplementation(() => true);
    try {
      new JsonLogger().log('to stdout', 'dws.golden');
      expect(spy).toHaveBeenCalledTimes(1);
      expect(JSON.parse(String(spy.mock.calls[0][0]))).toMatchObject({ message: 'to stdout', logger: 'dws.golden' });
    } finally {
      spy.mockRestore();
    }
  });
});

describe('scrub', () => {
  it('over-redacts a credential-named key, as the contract documents', () => {
    expect(scrub('invalid token: expired')).toBe('invalid token: [REDACTED]');
  });

  it('leaves text without credentials alone', () => {
    expect(scrub('Nest application successfully started')).toBe('Nest application successfully started');
  });
});

describe('isJsonLogFormat', () => {
  it.each([
    ['json', true],
    [' JSON ', true],
    ['Json\n', true],
    [undefined, false],
    ['', false],
    ['text', false],
    ['yaml', false],
  ])('%j -> %s', (value, expected) => {
    expect(isJsonLogFormat(value)).toBe(expected);
  });
});

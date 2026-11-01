import type { LoggerService } from '@nestjs/common';
import { isSpanContextValid, trace } from '@opentelemetry/api';
import { inspect } from 'node:util';

/**
 * JSON-lines logger implementing the shared DWS log contract
 * (docs/log-format.md, spec `structured-log-correlation`). Installed by
 * `main.ts` only when `DWS_LOG_FORMAT=json`; otherwise Nest's own console
 * logger keeps running, unchanged.
 *
 * Trace context comes from `trace.getActiveSpan()`, which resolves to the
 * global the OpenTelemetry Node agent registers (the Operator injects it via
 * `--require`). That global is shared through `Symbol.for('opentelemetry.js.api.1')`,
 * but an API only uses a registered global whose minor version is >= its own.
 * `@opentelemetry/api` is therefore pinned to 1.9.x, not the newest 1.x: a
 * newer pin here could silently fall back to the no-op API next to an older agent.
 */

type NestLevel = 'verbose' | 'debug' | 'log' | 'warn' | 'error' | 'fatal';
type Severity = 'TRACE' | 'DEBUG' | 'INFO' | 'WARN' | 'ERROR' | 'FATAL';

const SEVERITY: Record<NestLevel, Severity> = {
  verbose: 'TRACE',
  debug: 'DEBUG',
  log: 'INFO',
  warn: 'WARN',
  error: 'ERROR',
  fatal: 'FATAL',
};

const REDACTED = '[REDACTED]';

// The two scrubber expressions from docs/log-format.md, applied in this order.
const CREDENTIAL_KEY_VALUE =
  /([\w.-]*(?:authorization|password|passwd|secret|token|api[-_]?key)[\w.-]*)("?\s*[:=]\s*"?)(?:(?:bearer|basic)\s+)?[^\s"',;&}]+/gi;
const BARE_CREDENTIAL = /\b(bearer|basic)\s+[A-Za-z0-9._~+/-]{8,}=*/gi;

export function scrub(text: string): string {
  return text.replace(CREDENTIAL_KEY_VALUE, `$1$2${REDACTED}`).replace(BARE_CREDENTIAL, `$1 ${REDACTED}`);
}

/** `DWS_LOG_FORMAT` selects JSON mode only for `json` (trimmed, case-insensitive). */
export function isJsonLogFormat(value: string | undefined): boolean {
  return value?.trim().toLowerCase() === 'json';
}

interface ParsedException {
  type: string;
  message: string;
  stacktrace: string;
}

// Same test Nest's ConsoleLogger uses to tell a stack trace from a context.
function isStackFormat(value: unknown): value is string {
  return typeof value === 'string' && /^(.)+\n\s+at .+:\d+:\d+/.test(value);
}

function renderMessage(message: unknown): string {
  if (typeof message === 'string') {
    return message;
  }
  if (typeof message === 'function') {
    return /^class\s/.test(Function.prototype.toString.call(message)) ? message.name : renderMessage(message());
  }
  if (message instanceof Error) {
    return message.message;
  }
  if (message !== null && typeof message === 'object') {
    try {
      return JSON.stringify(message);
    } catch {
      return inspect(message, { breakLength: Infinity, depth: 3 });
    }
  }
  return String(message);
}

function fromError(err: Error): ParsedException {
  return {
    type: err.constructor?.name || err.name || 'Error',
    message: err.message,
    stacktrace: err.stack ?? `${err.name}: ${err.message}`,
  };
}

// A bare stack string (what Nest's `error(message, stack)` carries) has no
// type of its own; read it from the first line ("TypeError: boom").
function fromStack(stack: string, fallbackMessage: string): ParsedException {
  const head = /^([A-Za-z_$][\w$.]*):\s?(.*)$/m.exec(stack);
  return { type: head?.[1] ?? 'Error', message: head ? head[2] : fallbackMessage, stacktrace: stack };
}

interface ParsedRecord {
  message: string;
  context?: string;
  exception?: ParsedException;
}

// Mirrors how ConsoleLogger splits `(message, ...params, context?)` and, for
// error/fatal, `(message, stack?, context?)`.
function parseArgs(level: NestLevel, args: unknown[]): ParsedRecord {
  let context: string | undefined;
  let rest = args;
  if (rest.length === 2 && isStackFormat(rest[1])) {
    // (message, stack) with no context
  } else if (rest.length > 1 && typeof rest[rest.length - 1] === 'string') {
    context = rest[rest.length - 1] as string;
    rest = rest.slice(0, -1);
  }

  let stack: string | undefined;
  if (
    (level === 'error' || level === 'fatal') &&
    rest.length > 1 &&
    (typeof rest[rest.length - 1] === 'string' || rest[rest.length - 1] === undefined)
  ) {
    stack = rest[rest.length - 1] as string | undefined;
    rest = rest.slice(0, -1);
  }

  // An Error passed after the message is carried by `exception.*`, not repeated in the body.
  const message = rest
    .filter((part, i) => i === 0 || !(part instanceof Error))
    .map(renderMessage)
    .join(' ');
  const err = rest.find((part): part is Error => part instanceof Error);
  let exception: ParsedException | undefined;
  if (err) {
    exception = fromError(err);
  } else if (stack) {
    exception = fromStack(stack, message);
  }
  return { message, context, exception };
}

export class JsonLogger implements LoggerService {
  constructor(private readonly write: (line: string) => void = (line) => void process.stdout.write(line)) {}

  log(message: unknown, ...optionalParams: unknown[]): void {
    this.emit('log', message, optionalParams);
  }

  error(message: unknown, ...optionalParams: unknown[]): void {
    this.emit('error', message, optionalParams);
  }

  warn(message: unknown, ...optionalParams: unknown[]): void {
    this.emit('warn', message, optionalParams);
  }

  debug(message: unknown, ...optionalParams: unknown[]): void {
    this.emit('debug', message, optionalParams);
  }

  verbose(message: unknown, ...optionalParams: unknown[]): void {
    this.emit('verbose', message, optionalParams);
  }

  fatal(message: unknown, ...optionalParams: unknown[]): void {
    this.emit('fatal', message, optionalParams);
  }

  private emit(level: NestLevel, message: unknown, params: unknown[]): void {
    const parsed = parseArgs(level, [message, ...params]);
    const record: Record<string, string> = {
      timestamp: new Date().toISOString(),
      severity: SEVERITY[level],
      logger: parsed.context ?? 'nest',
      message: scrub(parsed.message),
    };

    const spanContext = trace.getActiveSpan()?.spanContext();
    if (spanContext && isSpanContextValid(spanContext)) {
      record.trace_id = spanContext.traceId;
      record.span_id = spanContext.spanId;
    }

    if (parsed.exception) {
      record['exception.type'] = parsed.exception.type;
      record['exception.message'] = scrub(parsed.exception.message);
      record['exception.stacktrace'] = scrub(parsed.exception.stacktrace);
    }

    // JSON.stringify escapes every newline inside values: one record, one line.
    this.write(`${JSON.stringify(record)}\n`);
  }
}

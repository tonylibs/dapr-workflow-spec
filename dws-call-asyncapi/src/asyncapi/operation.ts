/**
 * Resolves an OPERATION_ID from an AsyncAPI 3.0 document into a cached operation
 * template: the action, the channel address, and the operation message's
 * `payload` schema needed to validate and dispatch a request. Works against the
 * plain parsed document object (internal `$ref` JSON pointers are resolved here)
 * so it stays pure and unit-testable from fixtures.
 */

/** A JSON-schema-ish object. Kept loose because it comes straight from the doc. */
export type Schema = Record<string, unknown>;

/** The cached, dispatch-ready shape of one AsyncAPI `send` operation. */
export interface OperationTemplate {
  readonly operationId: string;
  readonly action: string;
  readonly channelName: string;
  /** The channel address (destination); `undefined` for a dynamic address. */
  readonly address: string | undefined;
  /** The resolved message `payload` schema, or `undefined` when none is declared. */
  readonly payloadSchema: Schema | undefined;
}

/** Resolves OPERATION_ID and returns its template; throws on any resolution failure. */
export function resolveOperation(doc: Record<string, unknown>, operationId: string): OperationTemplate {
  const operations = asObject(doc.operations);
  const operation = operations === undefined ? undefined : asObject(operations[operationId]);
  if (operation === undefined) {
    throw new Error(`operation "${operationId}" not found in the AsyncAPI document`);
  }

  const action = typeof operation.action === 'string' ? operation.action : undefined;
  if (action !== 'send') {
    throw new Error(
      `operation "${operationId}" has action "${action ?? 'undefined'}"; call: asyncapi requires action "send"`,
    );
  }

  const { channelName, channel } = resolveChannel(doc, operation, operationId);
  const address = typeof channel.address === 'string' ? channel.address : undefined;
  const payloadSchema = resolvePayloadSchema(doc, operation, channel, operationId);

  return { operationId, action, channelName, address, payloadSchema };
}

/** Resolves the operation's `channel.$ref` to a channel object and its name. */
function resolveChannel(
  doc: Record<string, unknown>,
  operation: Record<string, unknown>,
  operationId: string,
): { channelName: string; channel: Record<string, unknown> } {
  const ref = refOf(operation.channel);
  if (ref === undefined) {
    throw new Error(`operation "${operationId}" must reference a channel via $ref`);
  }
  const channel = asObject(resolveRef(doc, ref));
  if (channel === undefined) {
    throw new Error(`operation "${operationId}" channel $ref "${ref}" does not resolve to a channel`);
  }
  const segments = ref.split('/');
  return { channelName: segments[segments.length - 1] ?? ref, channel };
}

/**
 * Resolves the operation message's `payload` schema: the operation's first
 * `messages[].$ref` if declared, else the channel's sole message. Returns
 * `undefined` when no single message can be identified — dispatch then accepts
 * any payload rather than failing.
 */
function resolvePayloadSchema(
  doc: Record<string, unknown>,
  operation: Record<string, unknown>,
  channel: Record<string, unknown>,
  operationId: string,
): Schema | undefined {
  const message = resolveMessage(doc, operation, channel, operationId);
  if (message === undefined) return undefined;
  const payload = asObject(message.payload);
  return payload;
}

function resolveMessage(
  doc: Record<string, unknown>,
  operation: Record<string, unknown>,
  channel: Record<string, unknown>,
  operationId: string,
): Record<string, unknown> | undefined {
  const opMessages = operation.messages;
  if (Array.isArray(opMessages) && opMessages.length > 0) {
    const ref = refOf(opMessages[0]);
    const resolved = ref === undefined ? asObject(opMessages[0]) : asObject(resolveRef(doc, ref));
    if (resolved === undefined) {
      throw new Error(`operation "${operationId}" message could not be resolved`);
    }
    return resolved;
  }

  const channelMessages = asObject(channel.messages);
  if (channelMessages === undefined) return undefined;
  const entries = Object.values(channelMessages);
  if (entries.length === 0) return undefined;
  if (entries.length > 1) {
    throw new Error(
      `operation "${operationId}" does not select a message and its channel declares ${entries.length}; ` +
        'declare the operation message explicitly',
    );
  }
  return asObject(entries[0]);
}

/** Resolves an internal JSON-pointer `$ref` (`#/a/b/c`) against the document. */
export function resolveRef(doc: Record<string, unknown>, ref: string): unknown {
  if (!ref.startsWith('#/')) {
    throw new Error(`only internal document references are supported, got "${ref}"`);
  }
  const segments = ref
    .slice(2)
    .split('/')
    .map((s) => s.replace(/~1/g, '/').replace(/~0/g, '~'));
  let current: unknown = doc;
  for (const segment of segments) {
    const obj = asObject(current);
    if (obj === undefined || !(segment in obj)) {
      throw new Error(`reference "${ref}" does not resolve`);
    }
    current = obj[segment];
  }
  return current;
}

function refOf(value: unknown): string | undefined {
  const obj = asObject(value);
  return obj !== undefined && typeof obj.$ref === 'string' ? obj.$ref : undefined;
}

function asObject(value: unknown): Record<string, unknown> | undefined {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) return undefined;
  return value as Record<string, unknown>;
}

import { describe, expect, it } from 'vitest';
import { resolveOperation, resolveRef } from '../src/asyncapi/operation.js';
import { fixtureDoc } from './helpers.js';

describe('resolveOperation', () => {
  it('resolves a send operation to its channel address and payload schema', () => {
    const template = resolveOperation(fixtureDoc('kafka-orders.json'), 'publishOrder');
    expect(template.action).toBe('send');
    expect(template.channelName).toBe('orders');
    expect(template.address).toBe('orders');
    expect(template.payloadSchema).toMatchObject({ type: 'object', required: ['orderId', 'amount'] });
  });

  it('falls back to a channel sole message when the operation omits messages', () => {
    const template = resolveOperation(fixtureDoc('sole-message.json'), 'sendNotification');
    expect(template.address).toBe('notifications');
    expect(template.payloadSchema).toMatchObject({ required: ['text'] });
  });

  it('rejects a non-send operation', () => {
    expect(() => resolveOperation(fixtureDoc('receive-only.json'), 'receivePing')).toThrow(
      /requires action "send"/,
    );
  });

  it('rejects an unknown operation', () => {
    expect(() => resolveOperation(fixtureDoc('kafka-orders.json'), 'nope')).toThrow(/not found/);
  });

  it('rejects an operation with no channel reference', () => {
    const doc = { operations: { op: { action: 'send' } } } as Record<string, unknown>;
    expect(() => resolveOperation(doc, 'op')).toThrow(/reference a channel/);
  });

  it('errors when a channel has multiple messages and the operation selects none', () => {
    const doc = {
      operations: { op: { action: 'send', channel: { $ref: '#/channels/c' } } },
      channels: { c: { address: 'c', messages: { a: { payload: {} }, b: { payload: {} } } } },
    } as Record<string, unknown>;
    expect(() => resolveOperation(doc, 'op')).toThrow(/does not select a message/);
  });

  it('treats a channel with no message as an unconstrained payload', () => {
    const doc = {
      operations: { op: { action: 'send', channel: { $ref: '#/channels/c' } } },
      channels: { c: { address: 'c' } },
    } as Record<string, unknown>;
    expect(resolveOperation(doc, 'op').payloadSchema).toBeUndefined();
  });
});

describe('resolveRef', () => {
  it('resolves an internal JSON pointer', () => {
    const doc = { channels: { orders: { address: 'orders' } } } as Record<string, unknown>;
    expect(resolveRef(doc, '#/channels/orders')).toEqual({ address: 'orders' });
  });

  it('rejects an external reference', () => {
    expect(() => resolveRef({}, 'other.json#/x')).toThrow(/only internal/);
  });

  it('throws on an unresolved pointer', () => {
    expect(() => resolveRef({}, '#/nope')).toThrow(/does not resolve/);
  });
});

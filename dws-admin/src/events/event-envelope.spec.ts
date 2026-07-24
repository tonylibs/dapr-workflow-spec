import { decodeEventEnvelope, InvalidEventEnvelopeError } from './event-envelope';

describe('decodeEventEnvelope', () => {
  it('decodes a definition.created example from docs/events.md', () => {
    const raw = {
      id: 'order@vab12cd34-1',
      source: 'dws-controller',
      type: 'io.dws.definition.created',
      time: '2026-07-24T15:37:12.851Z',
      datacontenttype: 'application/json',
      data: { workflow: 'order', version: 'vab12cd34', createdAt: '2026-07-24T15:37:12.851Z' },
    };

    const envelope = decodeEventEnvelope(raw);

    expect(envelope.id).toBe('order@vab12cd34-1');
    expect(envelope.type).toBe('io.dws.definition.created');
    expect(envelope.data).toEqual({ workflow: 'order', version: 'vab12cd34', createdAt: '2026-07-24T15:37:12.851Z' });
  });

  it('decodes an instance.started example from docs/events.md', () => {
    const raw = {
      id: 'order@vab12cd34-1',
      source: 'dws-orchestrator/order',
      type: 'io.dws.instance.started',
      time: '2026-07-24T15:37:12.851Z',
      datacontenttype: 'application/json',
      data: {
        instanceId: 'abc-123',
        workflow: 'order',
        version: 'v3',
        appId: 'order',
        startedAt: '2026-07-24T15:37:12.851Z',
      },
    };

    const envelope = decodeEventEnvelope(raw);

    expect(envelope.type).toBe('io.dws.instance.started');
    expect(envelope.data).toMatchObject({ instanceId: 'abc-123', appId: 'order' });
  });

  it('rejects a non-object payload', () => {
    expect(() => decodeEventEnvelope('not-an-object')).toThrow(InvalidEventEnvelopeError);
  });

  it('rejects a payload missing id', () => {
    expect(() => decodeEventEnvelope({ type: 'io.dws.instance.started', source: 's', time: 't', data: {} })).toThrow(
      InvalidEventEnvelopeError,
    );
  });

  it('rejects a payload missing data', () => {
    expect(() => decodeEventEnvelope({ id: '1', type: 't', source: 's', time: 't' })).toThrow(InvalidEventEnvelopeError);
  });

  it('defaults datacontenttype when absent', () => {
    const envelope = decodeEventEnvelope({ id: '1', type: 't', source: 's', time: 't', data: {} });
    expect(envelope.datacontenttype).toBe('application/json');
  });

  it('parses a raw JSON string payload (per @dapr/dapr\'s documented "string or object" callback type)', () => {
    const raw = JSON.stringify({ id: '1', type: 'io.dws.instance.started', source: 's', time: 't', data: { instanceId: 'x' } });

    const envelope = decodeEventEnvelope(raw);

    expect(envelope.id).toBe('1');
    expect(envelope.data).toEqual({ instanceId: 'x' });
  });

  it('rejects a string payload that is not valid JSON', () => {
    expect(() => decodeEventEnvelope('not json')).toThrow(InvalidEventEnvelopeError);
  });
});

import { Injectable, Logger } from '@nestjs/common';
import { EventType } from './event-types';
import type { EventEnvelope } from './event-envelope';
import type { InstancePayload, TaskPayload } from './event-types';
import type { Transaction } from './idempotent-handler';
import { insertTaskEvent, upsertWorkflowInstance } from './upserts';

const HANDLED_TYPES: readonly string[] = [
  EventType.InstanceStarted,
  EventType.InstanceCompleted,
  EventType.InstanceFailed,
  EventType.TaskStarted,
  EventType.TaskCompleted,
  EventType.TaskFailed,
];

/**
 * Processes dws-orchestrator's instance/task events into the read model.
 * Not itself a @DaprPubSub target — see DwsEventsSubscriber, which owns the
 * single subscription to the shared dws.events topic (Dapr's SDK rejects two
 * separate subscriptions to the same pubsub+topic pair) and dispatches here
 * for orchestrator-originated event types.
 */
@Injectable()
export class OrchestratorEventsHandler {
  private readonly logger = new Logger(OrchestratorEventsHandler.name);

  canHandle(type: string): boolean {
    return HANDLED_TYPES.includes(type);
  }

  async process(tx: Transaction, envelope: EventEnvelope): Promise<void> {
    switch (envelope.type) {
      case EventType.InstanceStarted:
        await upsertWorkflowInstance(tx, 'started', envelope.data as InstancePayload);
        break;
      case EventType.InstanceCompleted:
        await upsertWorkflowInstance(tx, 'completed', envelope.data as InstancePayload);
        break;
      case EventType.InstanceFailed:
        await upsertWorkflowInstance(tx, 'failed', envelope.data as InstancePayload);
        break;
      case EventType.TaskStarted:
        await insertTaskEvent(tx, envelope.id, 'started', envelope.data as TaskPayload);
        break;
      case EventType.TaskCompleted:
        await insertTaskEvent(tx, envelope.id, 'completed', envelope.data as TaskPayload);
        break;
      case EventType.TaskFailed:
        await insertTaskEvent(tx, envelope.id, 'failed', envelope.data as TaskPayload);
        break;
      default:
        this.logger.warn(`process() called for an unhandled type: ${envelope.type}`);
    }
  }
}

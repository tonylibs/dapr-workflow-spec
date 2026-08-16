import { Injectable, Logger } from '@nestjs/common';
import { EventType } from './event-types';
import type { DwsEvent } from './event-envelope';
import type { InstancePayload, TaskPayload } from './event-types';
import type { Transaction } from './idempotent-handler';
import type { LiveEvent } from './instance-events.service';
import { insertTaskEvent, upsertWorkflowInstance } from './upserts';

function asInstanceEvent(instance: Awaited<ReturnType<typeof upsertWorkflowInstance>>): LiveEvent | null {
  return instance ? { kind: 'instance', instance } : null;
}

function asTaskEvent(task: Awaited<ReturnType<typeof insertTaskEvent>>): LiveEvent | null {
  return task ? { kind: 'task', task } : null;
}

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

  /**
   * Returns the live event this write produced, for the caller to publish once
   * the transaction has committed, or null when the write changed nothing
   * observable (a duplicate task event, or a status the terminal ratchet
   * suppressed).
   */
  async process(tx: Transaction, envelope: DwsEvent): Promise<LiveEvent | null> {
    switch (envelope.type) {
      case EventType.InstanceStarted:
        return asInstanceEvent(await upsertWorkflowInstance(tx, 'started', envelope.data as InstancePayload));
      case EventType.InstanceCompleted:
        return asInstanceEvent(await upsertWorkflowInstance(tx, 'completed', envelope.data as InstancePayload));
      case EventType.InstanceFailed:
        return asInstanceEvent(await upsertWorkflowInstance(tx, 'failed', envelope.data as InstancePayload));
      case EventType.TaskStarted:
        return asTaskEvent(await insertTaskEvent(tx, envelope.id, 'started', envelope.data as TaskPayload));
      case EventType.TaskCompleted:
        return asTaskEvent(await insertTaskEvent(tx, envelope.id, 'completed', envelope.data as TaskPayload));
      case EventType.TaskFailed:
        return asTaskEvent(await insertTaskEvent(tx, envelope.id, 'failed', envelope.data as TaskPayload));
      default:
        this.logger.warn(`process() called for an unhandled type: ${envelope.type}`);
        return null;
    }
  }
}

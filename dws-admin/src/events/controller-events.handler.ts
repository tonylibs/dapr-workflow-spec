import { Injectable, Logger } from '@nestjs/common';
import { EventType } from './event-types';
import type { DwsEvent } from './event-envelope';
import type { DeploymentDrainPayload, DeploymentPayload, DefinitionPayload } from './event-types';
import type { Transaction } from './idempotent-handler';
import { upsertDeploymentApplyOutcome, upsertDeploymentDrainState, upsertWorkflowDefinition } from './upserts';

const HANDLED_TYPES: readonly string[] = [
  EventType.DefinitionCreated,
  EventType.DefinitionUpdated,
  EventType.DeploymentApplied,
  EventType.DeploymentFailed,
  EventType.DeploymentDrained,
  EventType.DeploymentCollected,
];

/**
 * Processes dws-controller's definition/deployment events into the read
 * model. Not itself a Dapr subscription target — see DwsEventsSubscriber,
 * which owns the single subscription to the shared dws.events topic (Dapr's
 * SDK rejects two separate subscriptions to the same pubsub+topic pair) and
 * dispatches here for controller-originated event types.
 */
@Injectable()
export class ControllerEventsHandler {
  private readonly logger = new Logger(ControllerEventsHandler.name);

  canHandle(type: string): boolean {
    return HANDLED_TYPES.includes(type);
  }

  async process(tx: Transaction, envelope: DwsEvent): Promise<void> {
    switch (envelope.type) {
      case EventType.DefinitionCreated:
        await upsertWorkflowDefinition(tx, 'created', envelope.data as DefinitionPayload);
        break;
      case EventType.DefinitionUpdated:
        await upsertWorkflowDefinition(tx, 'updated', envelope.data as DefinitionPayload);
        break;
      case EventType.DeploymentApplied:
        await upsertDeploymentApplyOutcome(tx, 'applied', envelope.data as DeploymentPayload);
        break;
      case EventType.DeploymentFailed:
        await upsertDeploymentApplyOutcome(tx, 'failed', envelope.data as DeploymentPayload);
        break;
      case EventType.DeploymentDrained:
        await upsertDeploymentDrainState(tx, 'drained', envelope.data as DeploymentDrainPayload, new Date(envelope.time));
        break;
      case EventType.DeploymentCollected:
        await upsertDeploymentDrainState(tx, 'collected', envelope.data as DeploymentDrainPayload);
        break;
      default:
        this.logger.warn(`process() called for an unhandled type: ${envelope.type}`);
    }
  }
}

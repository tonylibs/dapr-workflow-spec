import { Inject, Injectable, Logger } from '@nestjs/common';
import type { CloudEventV1 } from 'cloudevents';
import { DB } from '../store/store.module';
import type { Db } from '../store/db.type';
import { decodeEventEnvelope, InvalidEventEnvelopeError } from './event-envelope';
import { runIdempotent } from './idempotent-handler';
import { ControllerEventsHandler } from './controller-events.handler';
import { OrchestratorEventsHandler } from './orchestrator-events.handler';
import { InstanceEventsService, type LiveEvent } from './instance-events.service';

/**
 * Owns processing of the shared dws.events topic. Every event type funnels
 * through this one entry point and is dispatched by envelope.type to
 * ControllerEventsHandler or OrchestratorEventsHandler (design D3). This
 * class is a decorator-free injectable domain service: the HTTP adapter that
 * calls it (`DaprSubscriptionController`) owns Dapr's programmatic
 * subscription contract (`GET /dapr/subscribe`, `POST /dapr/events/dws`).
 */
@Injectable()
export class DwsEventsSubscriber {
  private readonly logger = new Logger(DwsEventsSubscriber.name);

  constructor(
    @Inject(DB) private readonly db: Db,
    private readonly controllerEvents: ControllerEventsHandler,
    private readonly orchestratorEvents: OrchestratorEventsHandler,
    private readonly instanceEvents: InstanceEventsService,
  ) {}

  /**
   * The message Dapr delivers is our documented CloudEvent (docs/events.md),
   * carried as the `data` of Dapr's own transport CloudEvent. It arrives here
   * as an already-parsed object, or — per Dapr's pubsub delivery contract
   * ("typically string or object") — as its JSON text; `decodeEventEnvelope`
   * accepts either and validates it with the CloudEvents SDK. The declared
   * type states that expectation; it is not a runtime guarantee, so a
   * non-conforming payload is still rejected below rather than trusted.
   */
  async process(message: CloudEventV1<unknown> | string): Promise<void> {
    let envelope;
    try {
      envelope = decodeEventEnvelope(message);
    } catch (err) {
      if (err instanceof InvalidEventEnvelopeError) {
        this.logger.warn(`Discarding malformed event: ${err.message}`);
        return;
      }
      throw err;
    }

    const { type } = envelope;
    // Held aside rather than published inline: a live push must describe a
    // committed write, and `work` still runs inside the transaction.
    const pending: { event: LiveEvent | null } = { event: null };

    await runIdempotent(this.db, envelope.id, async (tx) => {
      if (this.controllerEvents.canHandle(type)) {
        await this.controllerEvents.process(tx, envelope);
      } else if (this.orchestratorEvents.canHandle(type)) {
        pending.event = await this.orchestratorEvents.process(tx, envelope);
      } else {
        this.logger.debug(`Ignoring unhandled event type: ${type}`);
      }
    });

    if (pending.event) {
      this.instanceEvents.publish(pending.event);
    }
  }
}

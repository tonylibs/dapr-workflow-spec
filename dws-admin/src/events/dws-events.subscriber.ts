import { Inject, Injectable, Logger } from '@nestjs/common';
import { DaprPubSub } from '@dbc-tech/nest-dapr';
import type { CloudEventV1 } from 'cloudevents';
import { DB } from '../store/store.module';
import type { Db } from '../store/db.type';
import { decodeEventEnvelope, InvalidEventEnvelopeError } from './event-envelope';
import { runIdempotent } from './idempotent-handler';
import { ControllerEventsHandler } from './controller-events.handler';
import { OrchestratorEventsHandler } from './orchestrator-events.handler';

// pubsubName/topic must be statically resolvable at class-definition time
// (the decorator's arguments), so this reads process.env directly rather
// than going through Nest's DI-resolved ConfigService (see design D3).
const PUBSUB_NAME = process.env.DAPR_PUBSUB_NAME ?? 'pubsub';
const TOPIC = process.env.DAPR_PUBSUB_TOPIC ?? 'dws.events';

/**
 * Owns the single Dapr subscription to the shared dws.events topic. Dapr's
 * SDK allows only one subscription per (pubsubName, topic) pair with no
 * matching route, so every event type funnels through this one entry point
 * and is dispatched by envelope.type to ControllerEventsHandler or
 * OrchestratorEventsHandler (design D3).
 */
@Injectable()
export class DwsEventsSubscriber {
  private readonly logger = new Logger(DwsEventsSubscriber.name);

  constructor(
    @Inject(DB) private readonly db: Db,
    private readonly controllerEvents: ControllerEventsHandler,
    private readonly orchestratorEvents: OrchestratorEventsHandler,
  ) {}

  /**
   * The message Dapr delivers is our documented CloudEvent (docs/events.md),
   * carried as the `data` of Dapr's own transport CloudEvent. It arrives here
   * as an already-parsed object, or — per @dapr/dapr's callback contract
   * ("typically string or object") — as its JSON text; `decodeEventEnvelope`
   * accepts either and validates it with the CloudEvents SDK. The declared
   * type states that expectation; it is not a runtime guarantee, so a
   * non-conforming payload is still rejected below rather than trusted.
   */
  @DaprPubSub(PUBSUB_NAME, TOPIC)
  async onMessage(message: CloudEventV1<unknown> | string): Promise<void> {
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
    await runIdempotent(this.db, envelope.id, async (tx) => {
      if (this.controllerEvents.canHandle(type)) {
        await this.controllerEvents.process(tx, envelope);
      } else if (this.orchestratorEvents.canHandle(type)) {
        await this.orchestratorEvents.process(tx, envelope);
      } else {
        this.logger.debug(`Ignoring unhandled event type: ${type}`);
      }
    });
  }
}

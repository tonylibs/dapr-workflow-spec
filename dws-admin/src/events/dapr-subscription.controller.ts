import { Body, Controller, Get, Post } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { CloudEventV1 } from 'cloudevents';
import type { AppConfig } from '../config/configuration';
import { DwsEventsSubscriber } from './dws-events.subscriber';

/** Dapr's transport CloudEvent for a pubsub delivery. Only `data` is consumed here — the rest of the envelope is Dapr's own transport metadata. */
interface DaprTransportEvent {
  data?: unknown;
}

/** One entry of Dapr's programmatic-subscription response shape. */
interface Subscription {
  pubsubname: string;
  topic: string;
  routes: { default: string };
}

/**
 * Implements Dapr's programmatic subscription HTTP contract
 * (`GET /dapr/subscribe` discovery, `POST` delivery) as a thin adapter on
 * Nest's own port 3000 — no separate Dapr application server/port is
 * required (design D1). All database/idempotency/dispatch work stays in
 * `DwsEventsSubscriber`; this controller only unwraps Dapr's transport
 * envelope and translates the outcome into Dapr's success/retry contract.
 */
@Controller('dapr')
export class DaprSubscriptionController {
  constructor(
    private readonly config: ConfigService<AppConfig, true>,
    private readonly subscriber: DwsEventsSubscriber,
  ) {}

  @Get('subscribe')
  listSubscriptions(): Subscription[] {
    const dapr = this.config.get('dapr', { infer: true });
    return [
      {
        pubsubname: dapr.pubsubName,
        topic: dapr.topic,
        routes: { default: '/dapr/events/dws' },
      },
    ];
  }

  @Post('events/dws')
  async deliver(@Body() transport: DaprTransportEvent): Promise<{ status: 'SUCCESS' }> {
    // A thrown error here (e.g. an unexpected database failure) propagates
    // as a non-2xx response so Dapr retries the delivery; deliberately
    // discarded/malformed events are already resolved (not thrown) inside
    // DwsEventsSubscriber.process.
    // Dapr's transport `data` is `unknown` here at the HTTP boundary;
    // DwsEventsSubscriber.process validates its actual shape before trusting it.
    await this.subscriber.process(transport.data as CloudEventV1<unknown> | string);
    return { status: 'SUCCESS' };
  }
}

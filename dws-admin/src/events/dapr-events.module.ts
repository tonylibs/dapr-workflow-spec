import { Module } from '@nestjs/common';
import { StoreModule } from '../store/store.module';
import { ControllerEventsHandler } from './controller-events.handler';
import { OrchestratorEventsHandler } from './orchestrator-events.handler';
import { DwsEventsSubscriber } from './dws-events.subscriber';
import { InstanceEventsModule } from './instance-events.module';
import { DaprSubscriptionController } from './dapr-subscription.controller';

// Dapr's programmatic subscription HTTP contract (GET /dapr/subscribe,
// POST /dapr/events/dws) is served by DaprSubscriptionController on Nest's
// own listener — no separate Dapr application server/module (design D1).
@Module({
  imports: [StoreModule, InstanceEventsModule],
  controllers: [DaprSubscriptionController],
  providers: [ControllerEventsHandler, OrchestratorEventsHandler, DwsEventsSubscriber],
})
export class DaprEventsModule {}

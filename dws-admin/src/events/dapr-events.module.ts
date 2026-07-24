import { Module } from '@nestjs/common';
import { DaprModule } from '../dapr/dapr.module';
import { StoreModule } from '../store/store.module';
import { ControllerEventsHandler } from './controller-events.handler';
import { OrchestratorEventsHandler } from './orchestrator-events.handler';
import { DwsEventsSubscriber } from './dws-events.subscriber';

@Module({
  imports: [DaprModule, StoreModule],
  providers: [ControllerEventsHandler, OrchestratorEventsHandler, DwsEventsSubscriber],
})
export class DaprEventsModule {}

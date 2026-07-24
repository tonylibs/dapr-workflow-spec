import { Module } from '@nestjs/common';
import { ConfigModule } from './config/config.module';
import { StoreModule } from './store/store.module';
import { DaprEventsModule } from './events/dapr-events.module';
import { WorkflowsModule } from './workflows/workflows.module';
import { InstancesModule } from './instances/instances.module';
import { HealthModule } from './health/health.module';

@Module({
  imports: [ConfigModule, StoreModule, DaprEventsModule, WorkflowsModule, InstancesModule, HealthModule],
})
export class AppModule {}

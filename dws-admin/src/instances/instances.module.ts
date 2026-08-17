import { Module } from '@nestjs/common';
import { StoreModule } from '../store/store.module';
import { InstanceEventsModule } from '../events/instance-events.module';
import { InstancesController } from './instances.controller';
import { InstancesService } from './instances.service';

@Module({
  imports: [StoreModule, InstanceEventsModule],
  controllers: [InstancesController],
  providers: [InstancesService],
})
export class InstancesModule {}

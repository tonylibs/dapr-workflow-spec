import { Module } from '@nestjs/common';
import { StoreModule } from '../store/store.module';
import { InstancesController } from './instances.controller';
import { InstancesService } from './instances.service';

@Module({
  imports: [StoreModule],
  controllers: [InstancesController],
  providers: [InstancesService],
})
export class InstancesModule {}

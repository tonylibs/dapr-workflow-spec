import { Module } from '@nestjs/common';
import { StoreModule } from '../store/store.module';
import { InstancesController } from './instances.controller';

@Module({
  imports: [StoreModule],
  controllers: [InstancesController],
})
export class InstancesModule {}

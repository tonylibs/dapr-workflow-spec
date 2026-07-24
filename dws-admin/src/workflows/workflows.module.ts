import { Module } from '@nestjs/common';
import { StoreModule } from '../store/store.module';
import { WorkflowsController } from './workflows.controller';

@Module({
  imports: [StoreModule],
  controllers: [WorkflowsController],
})
export class WorkflowsModule {}

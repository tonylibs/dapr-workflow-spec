import { Module } from '@nestjs/common';
import { TerminusModule } from '@nestjs/terminus';
import { StoreModule } from '../store/store.module';
import { DbHealthIndicator } from './db-health.indicator';
import { HealthController } from './health.controller';

@Module({
  imports: [TerminusModule, StoreModule],
  controllers: [HealthController],
  providers: [DbHealthIndicator],
})
export class HealthModule {}

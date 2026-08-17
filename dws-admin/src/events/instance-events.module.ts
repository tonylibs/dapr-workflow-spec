import { Module } from '@nestjs/common';
import { InstanceEventsService } from './instance-events.service';

/**
 * Holds the single in-process live-event bus. Imported by both the ingestion
 * side (DaprEventsModule, which publishes) and the read side (InstancesModule,
 * whose SSE routes subscribe), so both share one Subject instance.
 */
@Module({
  providers: [InstanceEventsService],
  exports: [InstanceEventsService],
})
export class InstanceEventsModule {}

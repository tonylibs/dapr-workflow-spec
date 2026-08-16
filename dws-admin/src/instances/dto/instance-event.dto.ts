import { ApiProperty } from '@nestjs/swagger';

// Payload of an `instance` SSE message on GET /instances/:id/events. Mirrors
// the mutable fields of GET /instances/:id — an instance's workflow, version
// and appId never change, so a live update does not repeat them.
export class InstanceStatusEventDto {
  @ApiProperty({ type: String })
  instanceId: string;

  @ApiProperty({ type: String, description: 'started/completed/failed.' })
  status: string;

  @ApiProperty({ type: String, format: 'date-time', nullable: true })
  startedAt: Date | null;

  @ApiProperty({ type: String, format: 'date-time', nullable: true })
  endedAt: Date | null;
}

// Payload of a `task` SSE message on GET /instances/:id/events. Same fields as
// one row of GET /instances/:id/tasks.
export class TaskEventStreamDto {
  @ApiProperty({ type: String })
  instanceId: string;

  @ApiProperty({ type: String })
  id: string;

  @ApiProperty({ type: String })
  taskName: string;

  @ApiProperty({ type: String, description: 'Task type (call/switch/set/…).' })
  type: string;

  @ApiProperty({ type: String, description: 'Lifecycle phase (started/completed/failed).' })
  status: string;

  @ApiProperty({ type: String, format: 'date-time' })
  timestamp: Date;

  @ApiProperty({ type: String, nullable: true })
  error: string | null;
}

// Payload of a message on the fleet-wide GET /instances/events. Deliberately
// narrower than InstanceStatusEventDto: the instance list only patches a
// loaded row's status and end time.
export class InstanceStatusDeltaDto {
  @ApiProperty({ type: String })
  instanceId: string;

  @ApiProperty({ type: String, description: 'started/completed/failed.' })
  status: string;

  @ApiProperty({ type: String, format: 'date-time', nullable: true })
  endedAt: Date | null;
}

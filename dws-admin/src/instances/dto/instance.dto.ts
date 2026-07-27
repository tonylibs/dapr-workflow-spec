import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsOptional, IsString } from 'class-validator';
import { Paginated } from '../../common/pagination';
import { PaginationQueryDto } from '../../common/pagination-query.dto';

// GET /instances query filters, on top of the shared pagination params. Both
// filters are optional and combinable.
export class ListInstancesQueryDto extends PaginationQueryDto {
  @ApiPropertyOptional({ description: 'Filter to instances of this workflow name.' })
  @IsOptional()
  @IsString()
  workflow?: string;

  @ApiPropertyOptional({ description: 'Filter to instances with this status.' })
  @IsOptional()
  @IsString()
  status?: string;
}

// One row in GET /instances. Lifecycle-derived timestamps are nullable because
// the read model is eventually consistent (an instance may be recorded before
// its started/ended events land) — see design.md Risks.
export class InstanceSummaryDto {
  @ApiProperty({ type: String })
  instanceId: string;

  @ApiProperty({ type: String })
  workflow: string;

  @ApiProperty({ type: String })
  version: string;

  @ApiProperty({ type: String })
  status: string;

  @ApiProperty({ type: String, format: 'date-time', nullable: true })
  startedAt: Date | null;

  @ApiProperty({ type: String, format: 'date-time', nullable: true })
  endedAt: Date | null;
}

// GET /instances/:id — full instance detail.
export class InstanceDetailDto extends InstanceSummaryDto {
  @ApiProperty({ type: String, description: 'Dapr app-id of the orchestrator running this instance.' })
  appId: string;
}

// One task event in GET /instances/:id/tasks.
export class TaskEventDto {
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

export class PaginatedInstanceSummaryDto extends Paginated(InstanceSummaryDto) {}
export class PaginatedTaskEventDto extends Paginated(TaskEventDto) {}

import { ApiProperty } from '@nestjs/swagger';
import { Paginated } from '../../common/pagination';

// One entry in GET /workflows: a workflow name with its latest version and that
// version's status. "Latest" is by created_at (versions are content-addressed,
// not monotonically ordered) — see design.md D3.
export class WorkflowSummaryDto {
  @ApiProperty({ type: String, description: 'Workflow definition name.' })
  name: string;

  @ApiProperty({ type: String, description: 'Latest stored version for this name (by created_at).' })
  latestVersion: string;

  @ApiProperty({ type: String, description: 'Status of the latest version.' })
  status: string;

  @ApiProperty({ type: String, format: 'date-time', description: 'When the latest version was recorded.' })
  createdAt: Date;
}

// One version in GET /workflows/:name history.
export class WorkflowVersionDto {
  @ApiProperty({ type: String })
  version: string;

  @ApiProperty({ type: String })
  status: string;

  @ApiProperty({ type: String, format: 'date-time' })
  createdAt: Date;
}

// One deployment in GET /workflows/:name/deployments.
export class DeploymentDto {
  @ApiProperty({ type: String })
  version: string;

  @ApiProperty({ type: String })
  status: string;

  @ApiProperty({ type: [String], description: 'Dapr app-ids of the step services for this deployment.' })
  stepServices: string[];

  @ApiProperty({ type: String })
  orchestratorAppId: string;

  @ApiProperty({ type: String, format: 'date-time', nullable: true })
  drainedAt: Date | null;
}

export class PaginatedWorkflowSummaryDto extends Paginated(WorkflowSummaryDto) {}
export class PaginatedWorkflowVersionDto extends Paginated(WorkflowVersionDto) {}
export class PaginatedDeploymentDto extends Paginated(DeploymentDto) {}

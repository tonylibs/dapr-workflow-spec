import { Controller, Get, NotFoundException, Param, Query } from '@nestjs/common';
import { ApiOkResponse, ApiTags } from '@nestjs/swagger';
import { PaginationQueryDto } from '../common/pagination-query.dto';
import { WorkflowsService } from './workflows.service';
import { PaginatedDeploymentDto, PaginatedWorkflowSummaryDto, PaginatedWorkflowVersionDto } from './dto/workflow.dto';

@ApiTags('workflows')
@Controller('workflows')
export class WorkflowsController {
  constructor(private readonly workflows: WorkflowsService) {}

  @Get()
  @ApiOkResponse({ type: PaginatedWorkflowSummaryDto })
  listWorkflows(@Query() query: PaginationQueryDto): Promise<PaginatedWorkflowSummaryDto> {
    return this.workflows.listWorkflows(query.limit, query.cursor);
  }

  @Get(':name')
  @ApiOkResponse({ type: PaginatedWorkflowVersionDto })
  async listVersions(@Param('name') name: string, @Query() query: PaginationQueryDto): Promise<PaginatedWorkflowVersionDto> {
    if (!(await this.workflows.exists(name))) {
      throw new NotFoundException(`No workflow named '${name}'`);
    }
    return this.workflows.listVersions(name, query.limit, query.cursor);
  }

  @Get(':name/deployments')
  @ApiOkResponse({ type: PaginatedDeploymentDto })
  async listDeployments(@Param('name') name: string, @Query() query: PaginationQueryDto): Promise<PaginatedDeploymentDto> {
    if (!(await this.workflows.exists(name))) {
      throw new NotFoundException(`No workflow named '${name}'`);
    }
    return this.workflows.listDeployments(name, query.limit, query.cursor);
  }
}

import { Controller, Get, NotFoundException, Param, Query } from '@nestjs/common';
import { ApiOkResponse, ApiTags } from '@nestjs/swagger';
import { PaginationQueryDto } from '../common/pagination-query.dto';
import { InstancesService } from './instances.service';
import {
  InstanceDetailDto,
  ListInstancesQueryDto,
  PaginatedInstanceSummaryDto,
  PaginatedTaskEventDto,
} from './dto/instance.dto';

@ApiTags('instances')
@Controller('instances')
export class InstancesController {
  constructor(private readonly instances: InstancesService) {}

  @Get()
  @ApiOkResponse({ type: PaginatedInstanceSummaryDto })
  listInstances(@Query() query: ListInstancesQueryDto): Promise<PaginatedInstanceSummaryDto> {
    return this.instances.listInstances({ workflow: query.workflow, status: query.status }, query.limit, query.cursor);
  }

  @Get(':id')
  @ApiOkResponse({ type: InstanceDetailDto })
  async getInstance(@Param('id') id: string): Promise<InstanceDetailDto> {
    const instance = await this.instances.getInstance(id);
    if (!instance) {
      throw new NotFoundException(`No instance with id '${id}'`);
    }
    return instance;
  }

  @Get(':id/tasks')
  @ApiOkResponse({ type: PaginatedTaskEventDto })
  async listTasks(@Param('id') id: string, @Query() query: PaginationQueryDto): Promise<PaginatedTaskEventDto> {
    if (!(await this.instances.exists(id))) {
      throw new NotFoundException(`No instance with id '${id}'`);
    }
    return this.instances.listTasks(id, query.limit, query.cursor);
  }
}

import { Controller, Get, MessageEvent, NotFoundException, Param, Query, Sse } from '@nestjs/common';
import { ApiOkResponse, ApiOperation, ApiProduces, ApiTags } from '@nestjs/swagger';
import type { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { PaginationQueryDto } from '../common/pagination-query.dto';
import { InstanceEventsService } from '../events/instance-events.service';
import { InstancesService } from './instances.service';
import {
  InstanceDetailDto,
  ListInstancesQueryDto,
  PaginatedInstanceSummaryDto,
  PaginatedTaskEventDto,
} from './dto/instance.dto';
import { InstanceStatusDeltaDto, InstanceStatusEventDto, TaskEventStreamDto } from './dto/instance-event.dto';

@ApiTags('instances')
@Controller('instances')
export class InstancesController {
  constructor(
    private readonly instances: InstancesService,
    private readonly instanceEvents: InstanceEventsService,
  ) {}

  @Get()
  @ApiOkResponse({ type: PaginatedInstanceSummaryDto })
  listInstances(@Query() query: ListInstancesQueryDto): Promise<PaginatedInstanceSummaryDto> {
    return this.instances.listInstances({ workflow: query.workflow, status: query.status }, query.limit, query.cursor);
  }

  // Declared before the ':id' routes below: Express matches in declaration
  // order, so a later 'events' literal would be swallowed by ':id'.
  @Sse('events')
  @ApiOperation({
    summary: 'Server-sent stream of instance status changes across every instance.',
    description:
      'Emits one `instance` message per status change as it is ingested. Carries no history — a client needing current state reads GET /instances first.',
  })
  @ApiProduces('text/event-stream')
  @ApiOkResponse({ type: InstanceStatusDeltaDto })
  streamInstances(): Observable<MessageEvent> {
    return this.instanceEvents.observeStatusChanges().pipe(
      map((change) => ({
        type: 'instance',
        data: {
          instanceId: change.instanceId,
          status: change.status,
          endedAt: change.endedAt,
        } satisfies InstanceStatusDeltaDto,
      })),
    );
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

  /**
   * Live status and task events for one instance. Rejecting before the
   * Observable is returned surfaces as an ordinary 404 — Nest awaits an async
   * @Sse() handler and only commits stream headers once it resolves.
   *
   * The returned stream completes on a terminal instance status (see
   * InstanceEventsService.observeInstance), which ends the HTTP response.
   */
  @Sse(':id/events')
  @ApiOperation({
    summary: 'Server-sent stream of one instance’s status changes and task events.',
    description:
      'Emits `instance` messages (InstanceStatusEventDto) and `task` messages (TaskEventStreamDto), and ends once the instance reaches a terminal status. Carries no history — a client needing current state reads GET /instances/:id and GET /instances/:id/tasks first.',
  })
  @ApiProduces('text/event-stream')
  @ApiOkResponse({ type: InstanceStatusEventDto })
  async streamInstance(@Param('id') id: string): Promise<Observable<MessageEvent>> {
    if (!(await this.instances.exists(id))) {
      throw new NotFoundException(`No instance with id '${id}'`);
    }

    return this.instanceEvents.observeInstance(id).pipe(
      map((event) =>
        event.kind === 'instance'
          ? {
              type: 'instance',
              data: {
                instanceId: event.instance.instanceId,
                status: event.instance.status,
                startedAt: event.instance.startedAt,
                endedAt: event.instance.endedAt,
              } satisfies InstanceStatusEventDto,
            }
          : {
              type: 'task',
              data: {
                instanceId: event.task.instanceId,
                id: event.task.id,
                taskName: event.task.taskName,
                type: event.task.type,
                status: event.task.status,
                timestamp: event.task.timestamp,
                error: event.task.error,
              } satisfies TaskEventStreamDto,
            },
      ),
    );
  }
}

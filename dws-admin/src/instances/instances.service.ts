import { Inject, Injectable } from '@nestjs/common';
import { and, asc, eq, gt, isNull, lt, or, sql, type SQL } from 'drizzle-orm';
import { DB } from '../store/store.module';
import type { Db } from '../store/db.type';
import { taskEvents, workflowInstances } from '../store/schema';
import { buildPage, decodeCursor, encodeCursor, type Page } from '../common/pagination';
import type { InstanceDetailDto, InstanceSummaryDto, TaskEventDto } from './dto/instance.dto';

export interface InstanceFilters {
  workflow?: string;
  status?: string;
}

@Injectable()
export class InstancesService {
  constructor(@Inject(DB) private readonly db: Db) {}

  async exists(id: string): Promise<boolean> {
    const [row] = await this.db
      .select({ id: workflowInstances.instanceId })
      .from(workflowInstances)
      .where(eq(workflowInstances.instanceId, id))
      .limit(1);
    return row !== undefined;
  }

  // GET /instances — most-recent first, ordered by (started_at desc nulls last,
  // instance_id asc) so the null-started_at rows sort after all dated rows with
  // a stable tiebreak. Keyset comparison handles the null boundary explicitly
  // (design.md D1 / Risks).
  async listInstances(filters: InstanceFilters, limit: number, cursor?: string): Promise<Page<InstanceSummaryDto>> {
    const conditions: (SQL | undefined)[] = [];
    if (filters.workflow) conditions.push(eq(workflowInstances.workflow, filters.workflow));
    if (filters.status) conditions.push(eq(workflowInstances.status, filters.status));

    if (cursor) {
      const [startedAt, instanceId] = decodeCursor(cursor);
      if (startedAt === null) {
        // Anchor is in the null-started_at tail: only later null rows remain.
        conditions.push(and(isNull(workflowInstances.startedAt), gt(workflowInstances.instanceId, instanceId as string)));
      } else {
        const anchor = new Date(startedAt);
        conditions.push(
          or(
            lt(workflowInstances.startedAt, anchor),
            and(eq(workflowInstances.startedAt, anchor), gt(workflowInstances.instanceId, instanceId as string)),
            isNull(workflowInstances.startedAt),
          ),
        );
      }
    }

    const rows = await this.db
      .select({
        instanceId: workflowInstances.instanceId,
        workflow: workflowInstances.workflow,
        version: workflowInstances.version,
        status: workflowInstances.status,
        startedAt: workflowInstances.startedAt,
        endedAt: workflowInstances.endedAt,
      })
      .from(workflowInstances)
      .where(and(...conditions))
      .orderBy(sql`${workflowInstances.startedAt} desc nulls last`, asc(workflowInstances.instanceId))
      .limit(limit + 1);

    return buildPage(rows, limit, (row) => encodeCursor([row.startedAt ? row.startedAt.toISOString() : null, row.instanceId]));
  }

  async getInstance(id: string): Promise<InstanceDetailDto | null> {
    const [row] = await this.db
      .select({
        instanceId: workflowInstances.instanceId,
        workflow: workflowInstances.workflow,
        version: workflowInstances.version,
        status: workflowInstances.status,
        appId: workflowInstances.appId,
        startedAt: workflowInstances.startedAt,
        endedAt: workflowInstances.endedAt,
      })
      .from(workflowInstances)
      .where(eq(workflowInstances.instanceId, id))
      .limit(1);
    return row ?? null;
  }

  // GET /instances/:id/tasks — ordered by (timestamp asc, id asc), keyset
  // paginated on the same tuple.
  async listTasks(id: string, limit: number, cursor?: string): Promise<Page<TaskEventDto>> {
    const after = cursor ? decodeCursor(cursor) : undefined;
    const keyset = after
      ? or(
          gt(taskEvents.timestamp, new Date(after[0] as string)),
          and(eq(taskEvents.timestamp, new Date(after[0] as string)), gt(taskEvents.id, after[1] as string)),
        )
      : undefined;

    const rows = await this.db
      .select({
        id: taskEvents.id,
        taskName: taskEvents.taskName,
        type: taskEvents.type,
        status: taskEvents.status,
        timestamp: taskEvents.timestamp,
        error: taskEvents.error,
      })
      .from(taskEvents)
      .where(and(eq(taskEvents.instanceId, id), keyset))
      .orderBy(asc(taskEvents.timestamp), asc(taskEvents.id))
      .limit(limit + 1);

    return buildPage(rows, limit, (row) => encodeCursor([row.timestamp.toISOString(), row.id]));
  }
}

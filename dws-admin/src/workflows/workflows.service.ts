import { Inject, Injectable } from '@nestjs/common';
import { and, asc, desc, eq, gt, lt, or } from 'drizzle-orm';
import { DB } from '../store/store.module';
import type { Db } from '../store/db.type';
import { deployments, workflowDefinitions } from '../store/schema';
import { buildPage, decodeCursor, encodeCursor, type Page } from '../common/pagination';
import type { DeploymentDto, WorkflowSummaryDto, WorkflowVersionDto } from './dto/workflow.dto';

@Injectable()
export class WorkflowsService {
  constructor(@Inject(DB) private readonly db: Db) {}

  // Does any version of this workflow name exist? Used to distinguish
  // "unknown workflow" (404) from "known workflow, no deployments yet" (200).
  async exists(name: string): Promise<boolean> {
    const [row] = await this.db
      .select({ name: workflowDefinitions.name })
      .from(workflowDefinitions)
      .where(eq(workflowDefinitions.name, name))
      .limit(1);
    return row !== undefined;
  }

  // GET /workflows — one row per name (latest version by created_at), keyset
  // paginated by name ascending. DISTINCT ON picks the newest row per name in a
  // single query (design.md D3).
  async listWorkflows(limit: number, cursor?: string): Promise<Page<WorkflowSummaryDto>> {
    const after = cursor ? decodeCursor(cursor) : undefined;
    const where = after ? gt(workflowDefinitions.name, after[0] as string) : undefined;

    const rows = await this.db
      .selectDistinctOn([workflowDefinitions.name], {
        name: workflowDefinitions.name,
        latestVersion: workflowDefinitions.version,
        status: workflowDefinitions.status,
        createdAt: workflowDefinitions.createdAt,
      })
      .from(workflowDefinitions)
      .where(where)
      .orderBy(workflowDefinitions.name, desc(workflowDefinitions.createdAt))
      .limit(limit + 1);

    return buildPage(rows, limit, (row) => encodeCursor([row.name]));
  }

  // GET /workflows/:name — full version history, newest first, keyset paginated
  // by (created_at desc, version asc).
  async listVersions(name: string, limit: number, cursor?: string): Promise<Page<WorkflowVersionDto>> {
    const after = cursor ? decodeCursor(cursor) : undefined;
    const anchorDate = after ? new Date(after[0] as string) : undefined;
    const keyset = after
      ? or(
          lt(workflowDefinitions.createdAt, anchorDate!),
          and(eq(workflowDefinitions.createdAt, anchorDate!), gt(workflowDefinitions.version, after[1] as string)),
        )
      : undefined;

    const rows = await this.db
      .select({
        version: workflowDefinitions.version,
        status: workflowDefinitions.status,
        createdAt: workflowDefinitions.createdAt,
      })
      .from(workflowDefinitions)
      .where(and(eq(workflowDefinitions.name, name), keyset))
      .orderBy(desc(workflowDefinitions.createdAt), asc(workflowDefinitions.version))
      .limit(limit + 1);

    return buildPage(rows, limit, (row) => encodeCursor([row.createdAt.toISOString(), row.version]));
  }

  // GET /workflows/:name/deployments — keyset paginated by version ascending.
  async listDeployments(name: string, limit: number, cursor?: string): Promise<Page<DeploymentDto>> {
    const after = cursor ? decodeCursor(cursor) : undefined;
    const keyset = after ? gt(deployments.version, after[0] as string) : undefined;

    const rows = await this.db
      .select({
        version: deployments.version,
        status: deployments.status,
        stepServices: deployments.stepServices,
        orchestratorAppId: deployments.orchestratorAppId,
        drainedAt: deployments.drainedAt,
      })
      .from(deployments)
      .where(and(eq(deployments.workflow, name), keyset))
      .orderBy(asc(deployments.version))
      .limit(limit + 1);

    return buildPage(rows, limit, (row) => encodeCursor([row.version]));
  }
}

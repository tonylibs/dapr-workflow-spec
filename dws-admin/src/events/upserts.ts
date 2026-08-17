import { sql } from 'drizzle-orm';
import { deployments, taskEvents, workflowDefinitions, workflowInstances } from '../store/schema';
import type { Transaction } from './idempotent-handler';
import type { DeploymentDrainPayload, DeploymentPayload, DefinitionPayload, InstancePayload, TaskPayload } from './event-types';
import type { InstanceStatusChange, TaskEventRecord } from './instance-events.service';

export async function upsertWorkflowDefinition(tx: Transaction, status: string, payload: DefinitionPayload): Promise<void> {
  await tx
    .insert(workflowDefinitions)
    .values({
      name: payload.workflow,
      version: payload.version,
      status,
      createdAt: new Date(payload.createdAt),
    })
    .onConflictDoUpdate({
      target: [workflowDefinitions.name, workflowDefinitions.version],
      set: {
        status,
        // Preserve the first-known creation time; a re-assert must not shift it.
        createdAt: sql`coalesce(${workflowDefinitions.createdAt}, excluded.created_at)`,
      },
    });
}

export async function upsertDeploymentApplyOutcome(
  tx: Transaction,
  status: 'applied' | 'failed',
  payload: DeploymentPayload,
): Promise<void> {
  await tx
    .insert(deployments)
    .values({
      workflow: payload.workflow,
      version: payload.version,
      stepServices: payload.stepServices,
      orchestratorAppId: payload.orchestratorAppId,
      status,
    })
    .onConflictDoUpdate({
      target: [deployments.workflow, deployments.version],
      set: {
        stepServices: sql`excluded.step_services`,
        orchestratorAppId: sql`excluded.orchestrator_app_id`,
        status: sql`CASE WHEN (${deploymentStatusRank(deployments.status)}) > (${deploymentStatusRank(sql`excluded.status`)})
          THEN ${deployments.status} ELSE excluded.status END`,
      },
    });
}

export async function upsertDeploymentDrainState(
  tx: Transaction,
  status: 'drained' | 'collected',
  payload: DeploymentDrainPayload,
  drainedAt?: Date,
): Promise<void> {
  await tx
    .insert(deployments)
    .values({
      workflow: payload.workflow,
      version: payload.version,
      stepServices: [],
      orchestratorAppId: payload.orchestratorAppId,
      status,
      drainedAt,
    })
    .onConflictDoUpdate({
      target: [deployments.workflow, deployments.version],
      set: {
        orchestratorAppId: sql`excluded.orchestrator_app_id`,
        status: sql`CASE WHEN (${deploymentStatusRank(deployments.status)}) > (${deploymentStatusRank(sql`excluded.status`)})
          THEN ${deployments.status} ELSE excluded.status END`,
        drainedAt: sql`coalesce(excluded.drained_at, ${deployments.drainedAt})`,
      },
    });
}

function deploymentStatusRank(column: ReturnType<typeof sql> | typeof deployments.status) {
  return sql`CASE ${column}
    WHEN 'failed' THEN 0
    WHEN 'applied' THEN 1
    WHEN 'drained' THEN 2
    WHEN 'collected' THEN 3
    ELSE -1
  END`;
}

/**
 * Returns the resulting instance state when this write actually moved the row
 * to `status`, or null when the terminal-status ratchet below suppressed it (a
 * late-arriving 'started' after a terminal event). The caller publishes the
 * non-null result to live SSE clients *after* the transaction commits, so a
 * rolled-back write is never pushed.
 */
export async function upsertWorkflowInstance(
  tx: Transaction,
  status: 'started' | 'completed' | 'failed',
  payload: InstancePayload,
): Promise<InstanceStatusChange | null> {
  const [row] = await tx
    .insert(workflowInstances)
    .values({
      instanceId: payload.instanceId,
      workflow: payload.workflow,
      version: payload.version,
      appId: payload.appId,
      status,
      startedAt: payload.startedAt ? new Date(payload.startedAt) : undefined,
      endedAt: payload.endedAt ? new Date(payload.endedAt) : undefined,
    })
    .onConflictDoUpdate({
      target: workflowInstances.instanceId,
      set: {
        workflow: sql`excluded.workflow`,
        version: sql`excluded.version`,
        appId: sql`excluded.app_id`,
        // A terminal status is never regressed by a later-arriving 'started'.
        status: sql`CASE WHEN ${workflowInstances.status} IN ('completed', 'failed')
          THEN ${workflowInstances.status} ELSE excluded.status END`,
        startedAt: sql`coalesce(${workflowInstances.startedAt}, excluded.started_at)`,
        endedAt: sql`coalesce(excluded.ended_at, ${workflowInstances.endedAt})`,
      },
    })
    .returning({
      instanceId: workflowInstances.instanceId,
      status: workflowInstances.status,
      startedAt: workflowInstances.startedAt,
      endedAt: workflowInstances.endedAt,
    });

  // The ratchet kept an existing terminal status: nothing changed for a live
  // client, and pushing the attempted status would be a false regression.
  if (!row || row.status !== status) {
    return null;
  }
  return row;
}

/**
 * Returns the inserted task event, or null when this event id was already
 * recorded (`onConflictDoNothing` matched) and there is nothing new to push.
 */
export async function insertTaskEvent(
  tx: Transaction,
  eventId: string,
  status: 'started' | 'completed' | 'failed',
  payload: TaskPayload,
): Promise<TaskEventRecord | null> {
  const [row] = await tx
    .insert(taskEvents)
    .values({
      id: eventId,
      instanceId: payload.instanceId,
      taskName: payload.taskName,
      type: payload.taskType,
      status,
      timestamp: new Date(payload.timestamp),
      error: payload.error,
    })
    .onConflictDoNothing({ target: taskEvents.id })
    .returning({
      instanceId: taskEvents.instanceId,
      id: taskEvents.id,
      taskName: taskEvents.taskName,
      type: taskEvents.type,
      status: taskEvents.status,
      timestamp: taskEvents.timestamp,
      error: taskEvents.error,
    });

  return row ?? null;
}

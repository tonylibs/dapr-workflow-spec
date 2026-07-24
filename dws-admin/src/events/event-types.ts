// Event type constants and payload shapes per docs/events.md.

export const EventType = {
  DefinitionCreated: 'io.dws.definition.created',
  DefinitionUpdated: 'io.dws.definition.updated',
  DeploymentApplied: 'io.dws.deployment.applied',
  DeploymentFailed: 'io.dws.deployment.failed',
  DeploymentDrained: 'io.dws.deployment.drained',
  DeploymentCollected: 'io.dws.deployment.collected',
  InstanceStarted: 'io.dws.instance.started',
  InstanceCompleted: 'io.dws.instance.completed',
  InstanceFailed: 'io.dws.instance.failed',
  TaskStarted: 'io.dws.task.started',
  TaskCompleted: 'io.dws.task.completed',
  TaskFailed: 'io.dws.task.failed',
} as const;

export type EventTypeValue = (typeof EventType)[keyof typeof EventType];

export interface DefinitionPayload {
  workflow: string;
  version: string;
  createdAt: string;
}

export interface DeploymentPayload {
  workflow: string;
  version: string;
  stepServices: string[];
  orchestratorAppId: string;
  error?: string;
}

export interface DeploymentDrainPayload {
  workflow: string;
  version: string;
  orchestratorAppId: string;
}

export interface InstancePayload {
  instanceId: string;
  workflow: string;
  version: string;
  appId: string;
  startedAt: string;
  endedAt?: string;
  error?: string;
}

export interface TaskPayload {
  instanceId: string;
  taskName: string;
  taskType: string;
  timestamp: string;
  error?: string;
}

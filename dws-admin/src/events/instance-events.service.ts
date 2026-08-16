import { Injectable, type OnModuleDestroy } from '@nestjs/common';
import { Observable, Subject } from 'rxjs';
import { filter, map, takeWhile } from 'rxjs/operators';

// An instance is running until one of these lands; the read model's upsert SQL
// never regresses out of them (see upserts.ts).
export const TERMINAL_INSTANCE_STATUSES: readonly string[] = ['completed', 'failed'];

export function isTerminalStatus(status: string): boolean {
  return TERMINAL_INSTANCE_STATUSES.includes(status);
}

// Mirrors the instance fields of GET /instances/:id that a status change can
// move. `workflow`/`version`/`appId` are immutable for an instance, so a live
// update never needs to carry them.
export interface InstanceStatusChange {
  instanceId: string;
  status: string;
  startedAt: Date | null;
  endedAt: Date | null;
}

// Mirrors one row of GET /instances/:id/tasks, plus the instance it belongs to.
export interface TaskEventRecord {
  instanceId: string;
  id: string;
  taskName: string;
  type: string;
  status: string;
  timestamp: Date;
  error: string | null;
}

export type LiveEvent =
  | { kind: 'instance'; instance: InstanceStatusChange }
  | { kind: 'task'; task: TaskEventRecord };

function instanceIdOf(event: LiveEvent): string {
  return event.kind === 'instance' ? event.instance.instanceId : event.task.instanceId;
}

/**
 * In-process fan-out from read-model writes to connected SSE clients.
 *
 * Publishers (DwsEventsSubscriber, after its transaction commits) call
 * `publish`; the SSE routes on InstancesController subscribe. A plain RxJS
 * Subject rather than an event-emitter library: rxjs is already a dependency
 * and Nest's `@Sse()` consumes an Observable directly, so nothing has to be
 * bridged between the two.
 *
 * This is deliberately in-process and therefore single-replica only — an event
 * ingested by one dws-admin replica is not seen by clients connected to
 * another. See design.md Non-Goals.
 */
@Injectable()
export class InstanceEventsService implements OnModuleDestroy {
  private readonly events = new Subject<LiveEvent>();

  publish(event: LiveEvent): void {
    this.events.next(event);
  }

  /**
   * Completes every open stream, which ends the SSE responses still attached to
   * them. Called on shutdown so connected clients see a clean end rather than a
   * dropped socket.
   */
  complete(): void {
    this.events.complete();
  }

  onModuleDestroy(): void {
    this.complete();
  }

  /**
   * Every live event for one instance. Completes after emitting a terminal
   * instance status, which is what closes the client's stream — nothing
   * further can happen to that instance.
   */
  observeInstance(instanceId: string): Observable<LiveEvent> {
    return this.events.pipe(
      filter((event) => instanceIdOf(event) === instanceId),
      // `inclusive` — the terminal event is delivered, then the stream completes.
      takeWhile((event) => !(event.kind === 'instance' && isTerminalStatus(event.instance.status)), true),
    );
  }

  /** Status changes across every instance, for the fleet-wide stream. */
  observeStatusChanges(): Observable<InstanceStatusChange> {
    return this.events.pipe(
      filter((event): event is Extract<LiveEvent, { kind: 'instance' }> => event.kind === 'instance'),
      map((event) => event.instance),
    );
  }
}

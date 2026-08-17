import { firstValueFrom, toArray } from 'rxjs';
import { InstanceEventsService, type InstanceStatusChange, type LiveEvent, type TaskEventRecord } from './instance-events.service';

const change = (over: Partial<InstanceStatusChange> & { instanceId: string }): InstanceStatusChange => ({
  status: 'started',
  startedAt: new Date('2026-07-24T00:00:00.000Z'),
  endedAt: null,
  ...over,
});

const task = (over: Partial<TaskEventRecord> & { instanceId: string; id: string }): TaskEventRecord => ({
  taskName: 'checkInventory',
  type: 'call',
  status: 'started',
  timestamp: new Date('2026-07-24T00:01:00.000Z'),
  error: null,
  ...over,
});

describe('InstanceEventsService', () => {
  describe('observeInstance', () => {
    it('emits only events for the requested instance', async () => {
      const service = new InstanceEventsService();
      const collected = firstValueFrom(service.observeInstance('inst-1').pipe(toArray()));

      service.publish({ kind: 'task', task: task({ instanceId: 'inst-2', id: 'evt-other' }) });
      service.publish({ kind: 'task', task: task({ instanceId: 'inst-1', id: 'evt-mine' }) });
      service.publish({ kind: 'instance', instance: change({ instanceId: 'inst-2', status: 'completed' }) });
      service.publish({ kind: 'instance', instance: change({ instanceId: 'inst-1', status: 'completed' }) });

      const events = await collected;
      expect(events.map(describeEvent)).toEqual(['task:evt-mine', 'instance:completed']);
    });

    it('completes after a terminal instance status, delivering it first', async () => {
      const service = new InstanceEventsService();
      const collected = firstValueFrom(service.observeInstance('inst-1').pipe(toArray()));

      service.publish({ kind: 'instance', instance: change({ instanceId: 'inst-1', status: 'started' }) });
      service.publish({
        kind: 'instance',
        instance: change({ instanceId: 'inst-1', status: 'failed', endedAt: new Date('2026-07-24T00:05:00.000Z') }),
      });
      // Anything after the terminal status must not reach a client — the
      // stream has already completed.
      service.publish({ kind: 'task', task: task({ instanceId: 'inst-1', id: 'evt-late' }) });

      const events = await collected;
      expect(events.map(describeEvent)).toEqual(['instance:started', 'instance:failed']);
    });

    it('does not complete on a task event whose status is completed', async () => {
      const service = new InstanceEventsService();
      const collected = firstValueFrom(service.observeInstance('inst-1').pipe(toArray()));

      service.publish({ kind: 'task', task: task({ instanceId: 'inst-1', id: 'evt-1', status: 'completed' }) });
      service.publish({ kind: 'task', task: task({ instanceId: 'inst-1', id: 'evt-2', status: 'started' }) });
      service.publish({ kind: 'instance', instance: change({ instanceId: 'inst-1', status: 'completed' }) });

      const events = await collected;
      expect(events.map(describeEvent)).toEqual(['task:evt-1', 'task:evt-2', 'instance:completed']);
    });
  });

  describe('observeStatusChanges', () => {
    it('emits status changes for every instance and drops task events', async () => {
      const service = new InstanceEventsService();
      const collected = firstValueFrom(service.observeStatusChanges().pipe(toArray()));

      service.publish({ kind: 'task', task: task({ instanceId: 'inst-1', id: 'evt-1' }) });
      service.publish({ kind: 'instance', instance: change({ instanceId: 'inst-1', status: 'started' }) });
      service.publish({ kind: 'instance', instance: change({ instanceId: 'inst-2', status: 'completed' }) });
      service.complete();

      const events = await collected;
      expect(events.map((c) => `${c.instanceId}:${c.status}`)).toEqual(['inst-1:started', 'inst-2:completed']);
    });

    it('keeps running past a terminal status, unlike the per-instance stream', async () => {
      const service = new InstanceEventsService();
      const collected = firstValueFrom(service.observeStatusChanges().pipe(toArray()));

      service.publish({ kind: 'instance', instance: change({ instanceId: 'inst-1', status: 'completed' }) });
      service.publish({ kind: 'instance', instance: change({ instanceId: 'inst-2', status: 'started' }) });
      service.complete();

      const events = await collected;
      expect(events).toHaveLength(2);
    });
  });
});

function describeEvent(event: LiveEvent): string {
  return event.kind === 'instance' ? `instance:${event.instance.status}` : `task:${event.task.id}`;
}

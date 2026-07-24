import { processedEvents } from '../store/schema';
import type { Db } from '../store/db.type';

export type Transaction = Parameters<Db['transaction']>[0] extends (tx: infer T, ...args: unknown[]) => unknown
  ? T
  : never;

/**
 * Runs `work` inside a DB transaction, guarded by an insert into
 * `processed_events` keyed on `eventId`. If that insert conflicts (the event
 * was already processed), `work` is skipped and the transaction commits with
 * no domain write. The insert and `work` share one transaction, so a crash
 * between them rolls back both — a redelivery is then processed as if for the
 * first time.
 *
 * Returns true if `work` ran (first delivery), false if the event was already
 * processed (skipped).
 */
export async function runIdempotent(db: Db, eventId: string, work: (tx: Transaction) => Promise<void>): Promise<boolean> {
  return db.transaction(async (tx) => {
    const inserted = await tx
      .insert(processedEvents)
      .values({ eventId, processedAt: new Date() })
      .onConflictDoNothing({ target: processedEvents.eventId })
      .returning({ eventId: processedEvents.eventId });

    if (inserted.length === 0) {
      return false;
    }

    await work(tx as Transaction);
    return true;
  });
}

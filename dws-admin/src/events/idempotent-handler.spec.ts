import { createTestDb, truncateAll } from '../test-support/test-db';
import { runIdempotent } from './idempotent-handler';

describe('runIdempotent', () => {
  const { db, close } = createTestDb();

  afterEach(async () => {
    await truncateAll(db);
  });

  afterAll(async () => {
    await close();
  });

  it('runs the callback on first delivery', async () => {
    const work = jest.fn().mockResolvedValue(undefined);

    const ran = await runIdempotent(db, 'evt-1', work);

    expect(ran).toBe(true);
    expect(work).toHaveBeenCalledTimes(1);
  });

  it('skips the callback on a replayed event id', async () => {
    const work = jest.fn().mockResolvedValue(undefined);

    await runIdempotent(db, 'evt-2', work);
    const ranAgain = await runIdempotent(db, 'evt-2', work);

    expect(ranAgain).toBe(false);
    expect(work).toHaveBeenCalledTimes(1);
  });

  it('leaves no processed_events row committed if the callback throws', async () => {
    await expect(
      runIdempotent(db, 'evt-3', async () => {
        throw new Error('boom');
      }),
    ).rejects.toThrow('boom');

    // A retry of the same event id must be treated as a first delivery.
    const work = jest.fn().mockResolvedValue(undefined);
    const ran = await runIdempotent(db, 'evt-3', work);

    expect(ran).toBe(true);
    expect(work).toHaveBeenCalledTimes(1);
  });
});

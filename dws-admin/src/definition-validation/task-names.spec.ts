import { duplicateTaskNames } from './task-names';

describe('duplicateTaskNames', () => {
  it('returns nothing when every task name is distinct', () => {
    const doc = { do: [{ a: { set: {} } }, { b: { set: {} } }] };
    expect(duplicateTaskNames(doc)).toEqual([]);
  });

  it('finds a duplicate between the top level and a nested catch body', () => {
    const doc = {
      do: [
        { retryIt: { try: { do: [{ a: { set: {} } }] }, catch: { do: [{ a: { set: {} } }] } } },
      ],
    };
    expect(duplicateTaskNames(doc)).toEqual([
      { name: 'a', path: '/do/0/retryIt/catch/do/0/a' },
    ]);
  });

  it('descends into for bodies and fork branches', () => {
    const doc = {
      do: [
        { each: { for: { each: 'i', in: '${ .xs }' }, do: [{ dup: { set: {} } }] } },
        { split: { fork: { branches: [{ dup: { set: {} } }] } } },
      ],
    };
    expect(duplicateTaskNames(doc)).toEqual([
      { name: 'dup', path: '/do/1/split/fork/branches/0/dup' },
    ]);
  });

  it('tolerates a malformed document without throwing', () => {
    expect(duplicateTaskNames({ do: 'not-a-list' })).toEqual([]);
    expect(duplicateTaskNames(null)).toEqual([]);
  });
});

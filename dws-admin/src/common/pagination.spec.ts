import { BadRequestException } from '@nestjs/common';
import { buildPage, decodeCursor, encodeCursor } from './pagination';

describe('cursor encode/decode', () => {
  it('round-trips a single-part cursor', () => {
    const cursor = encodeCursor(['order-flow']);
    expect(decodeCursor(cursor)).toEqual(['order-flow']);
  });

  it('round-trips a multi-part cursor', () => {
    const cursor = encodeCursor(['2026-07-24T00:00:00.000Z', 'inst-1']);
    expect(decodeCursor(cursor)).toEqual(['2026-07-24T00:00:00.000Z', 'inst-1']);
  });

  it('preserves a null anchor component', () => {
    const cursor = encodeCursor([null, 'inst-9']);
    expect(decodeCursor(cursor)).toEqual([null, 'inst-9']);
  });

  it('rejects a malformed cursor', () => {
    expect(() => decodeCursor('!!!not-base64-json')).toThrow(BadRequestException);
  });

  it('rejects a well-formed but non-array cursor', () => {
    const cursor = Buffer.from(JSON.stringify({ name: 'x' })).toString('base64url');
    expect(() => decodeCursor(cursor)).toThrow(BadRequestException);
  });
});

describe('buildPage', () => {
  const toCursor = (row: { id: string }) => encodeCursor([row.id]);

  it('returns a nextCursor when more rows than the limit came back', () => {
    const rows = [{ id: 'a' }, { id: 'b' }, { id: 'c' }];
    const page = buildPage(rows, 2, toCursor);
    expect(page.items).toEqual([{ id: 'a' }, { id: 'b' }]);
    expect(page.nextCursor).toBe(encodeCursor(['b']));
  });

  it('returns a null nextCursor on the last page', () => {
    const rows = [{ id: 'a' }, { id: 'b' }];
    const page = buildPage(rows, 2, toCursor);
    expect(page.items).toEqual([{ id: 'a' }, { id: 'b' }]);
    expect(page.nextCursor).toBeNull();
  });
});

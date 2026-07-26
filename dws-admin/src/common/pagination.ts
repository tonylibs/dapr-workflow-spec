import { BadRequestException, type Type } from '@nestjs/common';
import { ApiProperty } from '@nestjs/swagger';

// Bounds for the `limit` query parameter on every list endpoint. A client may
// not request an unbounded page; `limit` is validated against these in the
// query DTO (see PaginationQueryDto) so an out-of-range value yields 400.
export const DEFAULT_LIMIT = 20;
export const MAX_LIMIT = 100;

// A page of results plus an opaque cursor to the next page (null on the last
// page). This is the shape every list endpoint returns; see design.md D1.
export interface Page<T> {
  items: T[];
  nextCursor: string | null;
}

// An opaque cursor is the base64url-encoded JSON of the last row's ORDER BY
// tuple. It is intentionally not part of the public contract — clients treat it
// as a token, never as a value to do arithmetic on. `null` components are
// preserved (e.g. a null `started_at` anchor for the instances keyset).
export function encodeCursor(parts: (string | null)[]): string {
  return Buffer.from(JSON.stringify(parts)).toString('base64url');
}

export function decodeCursor(cursor: string): (string | null)[] {
  let parsed: unknown;
  try {
    parsed = JSON.parse(Buffer.from(cursor, 'base64url').toString('utf8'));
  } catch {
    throw new BadRequestException('Invalid cursor');
  }
  if (!Array.isArray(parsed) || parsed.some((p) => p !== null && typeof p !== 'string')) {
    throw new BadRequestException('Invalid cursor');
  }
  return parsed as (string | null)[];
}

// Given the rows fetched with `limit + 1`, split them into the page's items and
// the cursor to the next page. If fewer than `limit + 1` rows came back, this is
// the last page and nextCursor is null.
export function buildPage<T>(rows: T[], limit: number, toCursor: (row: T) => string): Page<T> {
  if (rows.length > limit) {
    const items = rows.slice(0, limit);
    return { items, nextCursor: toCursor(items[items.length - 1]) };
  }
  return { items: rows, nextCursor: null };
}

// Swagger needs a concrete class per response schema. This mixin builds a
// `Paginated<Item>` DTO class so `@nestjs/swagger` can emit the page schema and
// dws-console can generate a typed client from it.
export function Paginated<T>(ItemClass: Type<T>): Type<Page<T>> {
  class PageClass implements Page<T> {
    @ApiProperty({ type: [ItemClass] })
    items: T[];

    @ApiProperty({
      type: String,
      nullable: true,
      description: 'Opaque cursor to the next page, or null on the last page.',
    })
    nextCursor: string | null;
  }
  Object.defineProperty(PageClass, 'name', { value: `Paginated${ItemClass.name}` });
  return PageClass;
}

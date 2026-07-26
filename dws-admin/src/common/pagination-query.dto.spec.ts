import { plainToInstance } from 'class-transformer';
import { validateSync } from 'class-validator';
import { PaginationQueryDto } from './pagination-query.dto';
import { MAX_LIMIT } from './pagination';

// The global ValidationPipe runs plainToInstance + validate; this exercises the
// same path to prove the query DTO's constraints (so an HTTP request with a bad
// `limit` surfaces as 400).
function validate(raw: Record<string, unknown>) {
  const dto = plainToInstance(PaginationQueryDto, raw, { enableImplicitConversion: false });
  return validateSync(dto, { whitelist: true });
}

describe('PaginationQueryDto', () => {
  it('accepts a limit within range', () => {
    expect(validate({ limit: 10 })).toHaveLength(0);
  });

  it('rejects a limit above the max', () => {
    expect(validate({ limit: MAX_LIMIT + 1 }).length).toBeGreaterThan(0);
  });

  it('rejects a limit below 1', () => {
    expect(validate({ limit: 0 }).length).toBeGreaterThan(0);
  });

  it('rejects a non-integer limit', () => {
    expect(validate({ limit: 2.5 }).length).toBeGreaterThan(0);
  });
});

import { Inject, Injectable } from '@nestjs/common';
import { HealthIndicatorService } from '@nestjs/terminus';
import { sql } from 'drizzle-orm';
import { DB } from '../store/store.module';
import type { Db } from '../store/db.type';

@Injectable()
export class DbHealthIndicator {
  constructor(
    @Inject(DB) private readonly db: Db,
    private readonly healthIndicatorService: HealthIndicatorService,
  ) {}

  async isHealthy(key: string) {
    const indicator = this.healthIndicatorService.check(key);
    try {
      await this.db.execute(sql`select 1`);
      return indicator.up();
    } catch (err) {
      return indicator.down({ message: err instanceof Error ? err.message : 'unknown error' });
    }
  }
}

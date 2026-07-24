import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { DrizzlePostgresModule } from '@knaadh/nestjs-drizzle-postgres';
import { ConfigModule } from '../config/config.module';
import type { AppConfig } from '../config/configuration';
import * as schema from './schema';

export const DB = 'DB';

@Module({
  imports: [
    DrizzlePostgresModule.registerAsync({
      tag: DB,
      imports: [ConfigModule],
      inject: [ConfigService],
      useFactory: (config: ConfigService<AppConfig, true>) => ({
        postgres: {
          url: config.get('databaseUrl', { infer: true }),
        },
        config: { schema },
      }),
    }),
  ],
  exports: [DrizzlePostgresModule],
})
export class StoreModule {}

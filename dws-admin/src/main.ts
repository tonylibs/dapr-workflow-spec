import 'reflect-metadata';
import { NestFactory } from '@nestjs/core';
import { ConfigService } from '@nestjs/config';
import { AppModule } from './app.module';
import type { AppConfig } from './config/configuration';
import { runMigrations } from './store/run-migrations';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  const config = app.get(ConfigService<AppConfig, true>);

  if (config.get('runMigrationsOnBoot', { infer: true })) {
    await runMigrations(config.get('databaseUrl', { infer: true }));
  }

  await app.listen(config.get('port', { infer: true }));
}

bootstrap();

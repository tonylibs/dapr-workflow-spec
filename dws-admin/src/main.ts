import 'reflect-metadata';
import { ValidationPipe } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { ConfigService } from '@nestjs/config';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { AppModule } from './app.module';
import type { AppConfig } from './config/configuration';
import { runMigrations } from './store/run-migrations';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  const config = app.get(ConfigService<AppConfig, true>);

  // Reject unknown query params, coerce typed ones (e.g. `limit` from its
  // query-string form), and 400 on out-of-range values.
  app.useGlobalPipes(new ValidationPipe({ whitelist: true, transform: true }));

  // Publish the read API's OpenAPI document at /docs — dws-console generates a
  // typed client from this contract.
  const openApi = new DocumentBuilder()
    .setTitle('dws-admin read API')
    .setDescription('Read-only endpoints over the dws-admin read model.')
    .setVersion('1.0')
    .build();
  SwaggerModule.setup('docs', app, SwaggerModule.createDocument(app, openApi));

  if (config.get('runMigrationsOnBoot', { infer: true })) {
    await runMigrations(config.get('databaseUrl', { infer: true }));
  }

  await app.listen(config.get('port', { infer: true }));
}

bootstrap();

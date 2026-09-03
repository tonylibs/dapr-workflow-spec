import 'reflect-metadata';
import { ValidationPipe } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { ConfigService } from '@nestjs/config';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { raw } from 'express';
import { AppModule } from './app.module';
import type { AppConfig } from './config/configuration';
import { runMigrations } from './store/run-migrations';

async function bootstrap() {
  // rawBody: the compile/submit relay to dws-controller forwards the request
  // body verbatim (YAML or JSON) — Nest's JSON parser would drop YAML and
  // re-serialise JSON, changing the content-hashed version on the far side.
  const app = await NestFactory.create(AppModule, { rawBody: true });
  const config = app.get(ConfigService<AppConfig, true>);

  // Nest captures raw JSON when rawBody is enabled, but it has no built-in parser
  // for YAML. Parse it as bytes so the controller relay can preserve it verbatim.
  app.use(raw({ type: ['application/yaml', 'application/x-yaml', 'text/yaml'] }));

  // Reject unknown query params, coerce typed ones (e.g. `limit` from its
  // query-string form), and 400 on out-of-range values.
  app.useGlobalPipes(new ValidationPipe({ whitelist: true, transform: true }));

  // No CORS bootstrap: the public console/admin path is same-origin through
  // the shared Gateway (Dapr's bearer-gated invoke prefix), and local
  // development proxies dws-console's dev server to this API instead of
  // making a cross-origin browser request. See README's "Local development"
  // section.

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

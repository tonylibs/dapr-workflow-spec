import 'reflect-metadata';
import { ValidationPipe } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { ConfigService } from '@nestjs/config';
import type { NestExpressApplication } from '@nestjs/platform-express';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { raw } from 'express';
import { AppModule } from './app.module';
import type { AppConfig } from './config/configuration';
import { runMigrations } from './store/run-migrations';

/**
 * Largest definition body either definition path accepts — the relay to
 * dws-controller and `POST /definitions/validate` alike.
 *
 * Set explicitly because the body parsers default to 100 kB, which would cap
 * both paths an order of magnitude below what the validation endpoint documents
 * and reject an oversized body before it ever reaches the route enforcing its
 * own limit. Keep in step with `MAX_BODY_BYTES` in
 * `definition-validation.controller.ts`.
 */
const MAX_DEFINITION_BODY = '1mb';

async function bootstrap() {
  // rawBody: the compile/submit relay to dws-controller forwards the request
  // body verbatim (YAML or JSON) — Nest's JSON parser would drop YAML and
  // re-serialise JSON, changing the content-hashed version on the far side.
  const app = await NestFactory.create<NestExpressApplication>(AppModule, { rawBody: true });
  const config = app.get(ConfigService<AppConfig, true>);

  // A JSON definition is captured by Nest's own json parser, so raising the YAML
  // parser's limit alone would leave JSON submissions capped at the default.
  app.useBodyParser('json', { limit: MAX_DEFINITION_BODY });

  // Nest captures raw JSON when rawBody is enabled, but it has no built-in parser
  // for YAML. Parse it as bytes so the controller relay can preserve it verbatim.
  app.use(
    raw({
      type: ['application/yaml', 'application/x-yaml', 'text/yaml'],
      limit: MAX_DEFINITION_BODY,
    }),
  );

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

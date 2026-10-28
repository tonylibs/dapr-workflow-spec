import 'reflect-metadata';
import { ValidationPipe } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { ConfigService } from '@nestjs/config';
import type { NestExpressApplication } from '@nestjs/platform-express';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { AppModule } from './app.module';
import type { AppConfig } from './config/configuration';
import { configureAppTokenGuard } from './http/app-token-guard';
import { configureBodyParsers } from './http/body-parsers';
import { runMigrations } from './store/run-migrations';

async function bootstrap() {
  // rawBody: the compile/submit relay to dws-controller forwards the request
  // body verbatim (YAML or JSON) — Nest's JSON parser would drop YAML and
  // re-serialise JSON, changing the content-hashed version on the far side.
  const app = await NestFactory.create<NestExpressApplication>(AppModule, { rawBody: true });
  const config = app.get(ConfigService<AppConfig, true>);

  // First of all, ahead of the body parsers: with APP_API_TOKEN configured, a
  // peer reaching <pod-ip>:3000 without the sidecar's token is told 401 before
  // any body is buffered or parsed. No-op when the token is unset.
  configureAppTokenGuard(app, config.get('appToken', { infer: true }));

  // Body-parser wiring lives in its own module because its *ordering* is the
  // behaviour, and the HTTP-level tests boot against the same function.
  configureBodyParsers(app);

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

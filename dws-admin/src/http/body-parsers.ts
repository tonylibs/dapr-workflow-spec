import type { NestExpressApplication } from '@nestjs/platform-express';
import { raw } from 'express';
import { MAX_BODY_BYTES } from '../definition-validation/definition-validation.controller';

/** Route that must always receive raw bytes, whatever the content type. */
const VALIDATE_PATH = '/definitions/validate';

/** Content types carrying a definition that Nest has no built-in parser for. */
const YAML_TYPES = ['application/yaml', 'application/x-yaml', 'text/yaml'];

/**
 * Body-parser wiring for the whole app. Extracted from `bootstrap` so the
 * HTTP-level tests exercise the same ordering production runs — the ordering is
 * the behaviour here, and a test that re-declared it would prove nothing.
 *
 * Registration order is middleware order, so the sequence below is load-bearing.
 */
export function configureBodyParsers(app: NestExpressApplication): void {
  // First, and path-scoped: `POST /definitions/validate` answers a question
  // *about* a document, so an unparseable body is a valid answer (200 with a
  // parse error carrying line/column), not a failed request. Nest's json parser
  // would otherwise reach an `application/json` body first and reject malformed
  // JSON with its own 400 — pre-empting both the report contract and the `yaml`
  // parse path that produces the source position.
  //
  // Taking the bytes here also stops the json parser re-reading the stream:
  // body-parser skips a request whose body has already been consumed.
  app.use(VALIDATE_PATH, raw({ type: '*/*', limit: MAX_BODY_BYTES }));

  // Every other route keeps Nest's parsed JSON body — the Dapr subscription and
  // event-delivery endpoints are handed decoded CloudEvents. The limit is raised
  // only because a JSON definition submitted to the relay is parsed here too, and
  // the parsers default to 100 kB.
  app.useBodyParser('json', { limit: MAX_BODY_BYTES });

  // Nest captures raw JSON when rawBody is enabled, but it has no built-in parser
  // for YAML. Parse it as bytes so the controller relay can preserve it verbatim.
  app.use(raw({ type: YAML_TYPES, limit: MAX_BODY_BYTES }));
}

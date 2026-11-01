import { createHash, timingSafeEqual } from 'node:crypto';
import type { NextFunction, Request, Response } from 'express';
import type { NestExpressApplication } from '@nestjs/platform-express';

/** Header Dapr attaches to every call it makes into the app, carrying the app API token. */
const APP_TOKEN_HEADER = 'dapr-api-token';

/**
 * The one route served without the token: the kubelet's liveness/readiness
 * probes dial the pod IP directly and cannot carry it. Matched against the raw
 * request path, never a normalised one — Express would otherwise route
 * `/health/` and `/Health` to the same handler, and each variant must 401.
 */
const HEALTH_PATH = '/health';

/** Nest's default `UnauthorizedException` body, so a missing and a wrong token are indistinguishable. */
const UNAUTHORIZED_BODY = { statusCode: 401, message: 'Unauthorized' };

function digest(value: string): Buffer {
  return createHash('sha256').update(value).digest();
}

/**
 * App-port token check (auth roadmap Phase 10). Extracted from `bootstrap`, like
 * `configureBodyParsers`, so the HTTP-level tests exercise the same function
 * production runs.
 *
 * It is Express middleware, not a Nest guard, because ordering is the behaviour:
 * guards run after the body parsers, so an unauthenticated peer could make the
 * process buffer and parse up to `MAX_BODY_BYTES` of JSON before being told 401.
 * Call this before `configureBodyParsers`.
 *
 * No-op when `token` is unset or blank (`auth.enabled=false`, local development,
 * the existing tests). Otherwise every request whose `dapr-api-token` header is
 * not exactly `token` gets a 401, except `GET /health`. Neither the supplied nor
 * the configured value is ever logged or returned.
 */
export function configureAppTokenGuard(app: NestExpressApplication, token: string | undefined): void {
  if (token === undefined || token.trim() === '') {
    return;
  }

  // Fixed-length digests: `timingSafeEqual` needs equal lengths, and hashing
  // first means a wrong-length token is not distinguishable by timing either.
  const expected = digest(token);

  app.use((req: Request, res: Response, next: NextFunction) => {
    if (req.method === 'GET' && req.path === HEALTH_PATH) {
      next();
      return;
    }

    const supplied = req.headers[APP_TOKEN_HEADER];
    if (typeof supplied === 'string' && timingSafeEqual(digest(supplied), expected)) {
      next();
      return;
    }

    res.status(401).json(UNAUTHORIZED_BODY);
  });
}

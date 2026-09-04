import {
  BadRequestException,
  Controller,
  Headers,
  HttpCode,
  HttpStatus,
  PayloadTooLargeException,
  Post,
  Req,
} from '@nestjs/common';
import { ApiExcludeEndpoint } from '@nestjs/swagger';
import { DefinitionValidationService } from './definition-validation.service';
import type { ValidationReport } from './validation-report';

/** Same minimal raw-request shape the controller relay uses. */
interface RawRequest {
  rawBody?: Buffer;
  body?: unknown;
}

/** Content types a definition may be submitted as — mirrors main.ts's raw parser. */
const ACCEPTED = new Set([
  'application/yaml',
  'application/x-yaml',
  'text/yaml',
  'application/json',
]);

/**
 * CPU-bound endpoint on an operator-supplied body; cap it explicitly.
 *
 * Exported because the body parsers enforce the same cap upstream (see
 * src/http/body-parsers.ts) — sharing the constant is what keeps the parser and
 * the route from disagreeing about the limit.
 */
export const MAX_BODY_BYTES = 1024 * 1024;

/**
 * Spec-conformance check for a raw definition. Local to dws-admin: it never
 * contacts dws-controller and changes nothing. A well-formed request always
 * answers 200 — the document's validity is in the body, not the status.
 */
@Controller('definitions')
export class DefinitionValidationController {
  constructor(private readonly validation: DefinitionValidationService) {}

  @Post('validate')
  // Nest answers 201 for POST by default, but nothing is created here: the
  // contract is 200 with the report, so an invalid document is distinguishable
  // from a bad request by status alone.
  @HttpCode(HttpStatus.OK)
  @ApiExcludeEndpoint()
  validate(
    @Req() req: RawRequest,
    @Headers('content-type') contentType: string | undefined,
  ): ValidationReport {
    const mediaType = (contentType ?? '').split(';')[0].trim().toLowerCase();
    if (!ACCEPTED.has(mediaType)) {
      throw new BadRequestException(`Unsupported content type: ${contentType ?? '(none)'}`);
    }

    const body = req.rawBody ?? (Buffer.isBuffer(req.body) ? req.body : Buffer.alloc(0));
    if (body.byteLength === 0) throw new BadRequestException('Definition body is empty');
    if (body.byteLength > MAX_BODY_BYTES) {
      throw new PayloadTooLargeException(`Definition exceeds ${MAX_BODY_BYTES} bytes`);
    }

    return this.validation.validate(body.toString('utf8'));
  }
}

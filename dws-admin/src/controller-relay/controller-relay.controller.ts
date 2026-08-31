import { Controller, Headers, Post, Query, RawBodyRequest, Req, Res } from '@nestjs/common';
import { ApiExcludeEndpoint } from '@nestjs/swagger';
import { ControllerRelayService } from './controller-relay.service';

// Minimal typings for the pieces of the underlying HTTP adapter we touch —
// avoids pulling in @types/express just to name two callbacks.
interface RawRequest {
  rawBody?: Buffer;
  body?: unknown;
}
interface RawResponse {
  status(code: number): RawResponse;
  setHeader(name: string, value: string): void;
  send(body: Buffer): void;
}

// Write path to dws-controller. Console → admin-gateway → this route → local
// dapr sidecar → dws-controller sidecar (bearer-gated by Phase 2) → controller
// app port. Stateless by design: header + body pass through verbatim, no token
// parsing here.
@Controller('workflows')
export class ControllerRelayController {
  constructor(private readonly relay: ControllerRelayService) {}

  @Post()
  @ApiExcludeEndpoint()
  async deploy(
    @Req() req: RawBodyRequest<RawRequest>,
    @Res() res: RawResponse,
    @Headers('authorization') authorization: string | undefined,
    @Headers('content-type') contentType: string | undefined,
    @Query('dryRun') dryRun?: string,
  ): Promise<void> {
    // rawBody preserves the exact bytes the client sent — the controller
    // accepts YAML *or* JSON on the same endpoint (Consumes WILDCARD), so
    // Nest's JSON body parser would drop YAML and re-serialise JSON.
    const body = req.rawBody ?? (Buffer.isBuffer(req.body) ? req.body : Buffer.alloc(0));
    const relayed = await this.relay.relayDeploy(authorization, contentType, body, dryRun === 'true');
    if (relayed.contentType) {
      res.setHeader('content-type', relayed.contentType);
    }
    res.status(relayed.status).send(relayed.body);
  }
}

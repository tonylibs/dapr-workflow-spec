import { Inject, Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { AppConfig } from '../config/configuration';

// Response shape the controller relay returns — status + headers + body captured
// verbatim from the sidecar-invoke call so the controller decides.
export interface RelayResponse {
  status: number;
  contentType?: string;
  body: Buffer;
}

// Fetcher indirection kept so unit tests can assert exactly what the relay
// puts on the wire (URL, method, headers, body) without a live sidecar.
export type Fetcher = (input: string, init: {
  method: string;
  headers: Record<string, string>;
  body?: Buffer;
}) => Promise<{
  status: number;
  headers: { get(name: string): string | null };
  arrayBuffer(): Promise<ArrayBuffer>;
}>;

export const FETCHER = Symbol('CONTROLLER_RELAY_FETCHER');

@Injectable()
export class ControllerRelayService {
  private readonly sidecarBaseUrl: string;
  private readonly controllerAppId: string;

  constructor(
    config: ConfigService<AppConfig, true>,
    @Inject(FETCHER) private readonly fetcher: Fetcher,
  ) {
    const dapr = config.get('dapr', { infer: true });
    // Sidecar host defaults to loopback because daprd is injected into
    // dws-admin's own pod. Ports/host are the same ones nest-dapr already
    // consumes for pubsub and state.
    const host = dapr.daprHost ?? '127.0.0.1';
    const port = dapr.daprPort ?? '3500';
    this.sidecarBaseUrl = `http://${host}:${port}`;
    this.controllerAppId = dapr.controllerAppId;
  }

  // Forward a compile/submit request to dws-controller via this pod's own
  // Dapr sidecar. Bearer verification is Dapr's job on the controller side
  // (auth roadmap Phase 2's middleware.http.bearer + Configuration on the
  // controller sidecar), so we deliberately do not inspect the token here.
  async relayDeploy(authorization: string | undefined, contentType: string | undefined, body: Buffer, dryRun: boolean): Promise<RelayResponse> {
    const url = `${this.sidecarBaseUrl}/v1.0/invoke/${encodeURIComponent(this.controllerAppId)}/method/workflows${dryRun ? '?dryRun=true' : ''}`;
    const headers: Record<string, string> = {};
    if (authorization !== undefined) {
      headers.authorization = authorization;
    }
    if (contentType !== undefined) {
      headers['content-type'] = contentType;
    }
    const response = await this.fetcher(url, { method: 'POST', headers, body });
    const buf = Buffer.from(await response.arrayBuffer());
    return {
      status: response.status,
      contentType: response.headers.get('content-type') ?? undefined,
      body: buf,
    };
  }
}

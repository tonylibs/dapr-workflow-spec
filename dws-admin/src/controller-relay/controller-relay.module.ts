import { Module } from '@nestjs/common';
import { ConfigModule } from '../config/config.module';
import { ControllerRelayController } from './controller-relay.controller';
import { ControllerRelayService, FETCHER, type Fetcher } from './controller-relay.service';

// Uses Node 24's built-in fetch; nothing to install. Wrapped in a provider so
// tests can inject a stub fetcher and assert the outgoing request shape.
const defaultFetcher: Fetcher = (url, init) =>
  // Buffer is a valid BodyInit at runtime (Node 24's undici fetch), but the DOM
  // BodyInit union doesn't name it — cast rather than copy the bytes.
  fetch(url, { method: init.method, headers: init.headers, body: init.body as unknown as BodyInit });

@Module({
  imports: [ConfigModule],
  controllers: [ControllerRelayController],
  providers: [ControllerRelayService, { provide: FETCHER, useValue: defaultFetcher }],
})
export class ControllerRelayModule {}

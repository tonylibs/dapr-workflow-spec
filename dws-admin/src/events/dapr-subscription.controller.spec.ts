import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import type { AppConfig } from '../config/configuration';
import { DaprSubscriptionController } from './dapr-subscription.controller';
import { DwsEventsSubscriber } from './dws-events.subscriber';

// Request-level contract tests for Dapr's programmatic subscription HTTP
// contract on Nest port 3000 (design D1, spec admin-event-ingestion). Uses a
// real listening HTTP server + fetch, matching this codebase's existing
// request-level test style (see config/cors.spec.ts) rather than adding a
// new test-only HTTP client dependency.
describe('DaprSubscriptionController (request-level)', () => {
  let app: INestApplication;
  let baseUrl: string;
  let processMock: jest.Mock;

  const config = new ConfigService<AppConfig, true>({
    port: 3000,
    databaseUrl: 'postgres://ignored',
    runMigrationsOnBoot: false,
    dapr: {
      pubsubName: 'pubsub',
      topic: 'dws.events',
      daprHost: '127.0.0.1',
      daprPort: '3500',
      controllerAppId: 'dws-controller',
    },
  });

  beforeEach(async () => {
    processMock = jest.fn().mockResolvedValue(undefined);

    const moduleRef = await Test.createTestingModule({
      controllers: [DaprSubscriptionController],
      providers: [
        { provide: ConfigService, useValue: config },
        { provide: DwsEventsSubscriber, useValue: { process: processMock } },
      ],
    }).compile();

    app = moduleRef.createNestApplication();
    await app.init();
    await app.listen(0);
    baseUrl = await app.getUrl();
  });

  afterEach(async () => {
    await app.close();
  });

  it('advertises exactly one subscription for the configured pubsub/topic', async () => {
    const response = await fetch(`${baseUrl}/dapr/subscribe`);

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual([
      {
        pubsubname: 'pubsub',
        topic: 'dws.events',
        routes: { default: '/dapr/events/dws' },
      },
    ]);
  });

  it('unwraps the transport CloudEvent and hands data to the subscriber', async () => {
    const inner = { id: 'evt-1', type: 'io.dws.instance.started', source: 's', time: '2026-07-24T00:00:00.000Z', data: {} };

    const response = await fetch(`${baseUrl}/dapr/events/dws`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ data: inner }),
    });

    expect(response.status).toBeLessThan(300);
    await expect(response.json()).resolves.toEqual({ status: 'SUCCESS' });
    expect(processMock).toHaveBeenCalledWith(inner);
  });

  it('acknowledges an unknown/malformed inner event because the subscriber resolves without throwing', async () => {
    // DwsEventsSubscriber.process already swallows unknown-type/malformed
    // events (logs and resolves); the controller must not turn that
    // resolution into anything but a success acknowledgement.
    const response = await fetch(`${baseUrl}/dapr/events/dws`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ data: { not: 'a valid envelope' } }),
    });

    expect(response.status).toBeLessThan(300);
    await expect(response.json()).resolves.toEqual({ status: 'SUCCESS' });
  });

  it('returns a non-2xx outcome when processing fails unexpectedly, so Dapr retries', async () => {
    processMock.mockRejectedValueOnce(new Error('db unavailable'));

    const response = await fetch(`${baseUrl}/dapr/events/dws`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ data: {} }),
    });

    expect(response.status).toBeGreaterThanOrEqual(500);
  });
});

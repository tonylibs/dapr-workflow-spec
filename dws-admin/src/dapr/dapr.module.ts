import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { DaprModule as NestDaprModule } from '@dbc-tech/nest-dapr';
import { ConfigModule } from '../config/config.module';
import type { AppConfig } from '../config/configuration';

@Module({
  imports: [
    NestDaprModule.registerAsync({
      imports: [ConfigModule],
      inject: [ConfigService],
      useFactory: (config: ConfigService<AppConfig, true>) => {
        const dapr = config.get('dapr', { infer: true });
        return {
          serverHost: dapr.serverHost,
          serverPort: dapr.appPort,
          daprHost: dapr.daprHost,
          daprPort: dapr.daprPort,
        };
      },
    }),
  ],
  exports: [NestDaprModule],
})
export class DaprModule {}

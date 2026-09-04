import { Module } from '@nestjs/common';
import { DefinitionValidationController } from './definition-validation.controller';
import { DefinitionValidationService } from './definition-validation.service';

@Module({
  controllers: [DefinitionValidationController],
  providers: [DefinitionValidationService],
})
export class DefinitionValidationModule {}

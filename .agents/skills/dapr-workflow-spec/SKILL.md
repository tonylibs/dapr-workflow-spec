```markdown
# dapr-workflow-spec Development Patterns

> Auto-generated skill from repository analysis

## Overview
This skill introduces the core development patterns and conventions used in the `dapr-workflow-spec` repository. The codebase is written in TypeScript and focuses on clear, maintainable code without reliance on a specific framework. You'll learn file organization, import/export styles, and testing patterns, enabling you to contribute confidently to this project or similar TypeScript repositories.

## Coding Conventions

### File Naming
- **Style:** kebab-case
- **Example:**  
  `workflow-definition.ts`  
  `state-machine-builder.ts`

### Import Style
- **Relative imports** are used for referencing local modules.
- **Example:**
  ```typescript
  import { WorkflowDefinition } from './workflow-definition';
  ```

### Export Style
- **Named exports** are preferred over default exports.
- **Example:**
  ```typescript
  // In state-machine-builder.ts
  export function buildStateMachine() { ... }
  ```

### Commit Patterns
- **Type:** Freeform messages, no enforced prefix or format.
- **Average Length:** ~63 characters.

## Workflows

_No automated workflows detected in the repository._

## Testing Patterns

- **Test File Naming:** Test files follow the pattern `*.test.*`
  - Example: `workflow-definition.test.ts`
- **Testing Framework:** Not explicitly detected; check for usage of common frameworks like Jest or Mocha.
- **Test Example:**
  ```typescript
  import { buildStateMachine } from './state-machine-builder';

  describe('buildStateMachine', () => {
    it('should create a valid state machine', () => {
      const sm = buildStateMachine();
      expect(sm).toBeDefined();
    });
  });
  ```

## Commands

| Command | Purpose |
|---------|---------|
| /test   | Run all test files matching `*.test.*` |
| /lint   | Lint the codebase for style and errors |
| /build  | Compile the TypeScript code |
```
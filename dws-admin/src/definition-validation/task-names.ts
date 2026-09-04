/**
 * Task names must be unique across the whole definition at every depth: a
 * call/run task's name becomes its Dapr app-id and therefore its deployed
 * Knative Service name, and the orchestrator resolves tasks by name at runtime.
 *
 * JSON Schema cannot express this (it is cross-referential across sibling and
 * nested lists), so ajv will not catch it. Mirrors dws-controller's
 * WorkflowCompiler.duplicateTaskNames/collectTaskNames so both layers give the
 * same verdict — this one just gives it earlier, and with a pointer.
 */
export interface DuplicateTaskName {
  name: string;
  /** JSON pointer to the repeated occurrence (not the first one). */
  path: string;
}

/** Bodies that hold a nested task list, by the key that reaches them. */
const NESTED_LISTS: readonly (readonly string[])[] = [
  ['try', 'do'],
  ['catch', 'do'],
  ['for', 'do'],
  ['do'],
  ['fork', 'branches'],
];

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/** JSON-pointer escaping: `~` → `~0`, `/` → `~1` (RFC 6901). */
function escapePointer(segment: string): string {
  return segment.replace(/~/g, '~0').replace(/\//g, '~1');
}

export function duplicateTaskNames(document: unknown): DuplicateTaskName[] {
  const seen = new Set<string>();
  const duplicates: DuplicateTaskName[] = [];
  if (isRecord(document)) walkList(document.do, '/do', seen, duplicates);
  return duplicates;
}

function walkList(
  list: unknown,
  pointer: string,
  seen: Set<string>,
  duplicates: DuplicateTaskName[],
): void {
  if (!Array.isArray(list)) return;
  list.forEach((entry, index) => {
    if (!isRecord(entry)) return;
    // A task item is a single-key map: { <taskName>: <taskDefinition> }.
    for (const [name, definition] of Object.entries(entry)) {
      const taskPointer = `${pointer}/${index}/${escapePointer(name)}`;
      if (seen.has(name)) duplicates.push({ name, path: taskPointer });
      else seen.add(name);
      walkNested(definition, taskPointer, seen, duplicates);
    }
  });
}

function walkNested(
  definition: unknown,
  pointer: string,
  seen: Set<string>,
  duplicates: DuplicateTaskName[],
): void {
  if (!isRecord(definition)) return;
  for (const keys of NESTED_LISTS) {
    let node: unknown = definition;
    let nested = pointer;
    for (const key of keys) {
      if (!isRecord(node)) {
        node = undefined;
        break;
      }
      node = node[key];
      nested += `/${key}`;
    }
    if (Array.isArray(node)) walkList(node, nested, seen, duplicates);
  }
}

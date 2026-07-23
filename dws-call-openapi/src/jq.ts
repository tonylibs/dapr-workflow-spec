/**
 * Evaluates the configured PARAMETERS jq expressions against the request input.
 * All expressions are combined into a single jq program so one jq process runs
 * per request instead of one per parameter.
 */
import jq from 'node-jq';

/** Raised when a jq expression fails to evaluate against the input. */
export class JqError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'JqError';
  }
}

/**
 * Runs each PARAMETERS expression against `input` and returns a name -> value
 * map. An empty parameter set short-circuits without spawning jq.
 */
export async function evaluateParameters(
  parameters: Readonly<Record<string, string>>,
  input: unknown,
): Promise<Record<string, unknown>> {
  const names = Object.keys(parameters);
  if (names.length === 0) return {};

  // Build `{ "name": (expr), ... }` so a single jq run yields all values.
  const fields = names.map((name) => `${JSON.stringify(name)}: (${parameters[name]})`);
  const program = `{ ${fields.join(', ')} }`;

  let result: unknown;
  try {
    result = await jq.run(program, input as Parameters<typeof jq.run>[1], { input: 'json', output: 'json' });
  } catch (err) {
    throw new JqError(`failed to evaluate PARAMETERS jq expressions: ${(err as Error).message}`);
  }
  if (result === null || typeof result !== 'object' || Array.isArray(result)) {
    throw new JqError('PARAMETERS jq program did not produce an object');
  }
  return result as Record<string, unknown>;
}

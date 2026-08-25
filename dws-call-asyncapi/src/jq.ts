/**
 * Evaluates the configured PAYLOAD jq expression against the request input to
 * build the message payload dispatched to the broker. A single jq program runs
 * per request. The default expression `.` passes the whole workflow-data
 * document through as the payload.
 */
import jq from 'node-jq';

/** Raised when the PAYLOAD jq expression fails to evaluate against the input. */
export class JqError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'JqError';
  }
}

/**
 * Runs the PAYLOAD expression against `input` and returns the resulting value
 * (the message payload). A `null` result is returned as-is; the validator
 * decides whether the resolved payload satisfies the message schema.
 */
export async function evaluatePayload(expression: string, input: unknown): Promise<unknown> {
  try {
    return await jq.run(expression, input as Parameters<typeof jq.run>[1], {
      input: 'json',
      output: 'json',
    });
  } catch (err) {
    throw new JqError(`failed to evaluate PAYLOAD jq expression: ${(err as Error).message}`);
  }
}

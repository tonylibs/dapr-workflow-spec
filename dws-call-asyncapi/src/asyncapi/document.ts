/**
 * Fetches the AsyncAPI document, verifies its SHA-256, and parses it into a
 * plain object. Supports http(s) URLs and local file URLs. Validity is enforced
 * with `@asyncapi/parser` (error-severity diagnostics fail startup); operation
 * resolution runs against the plain parsed object so it stays pure and testable.
 * Any failure throws with a clear message so startup can fail fast.
 */
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { Parser } from '@asyncapi/parser';
import { parse as parseYaml } from 'yaml';

/** Raised when the fetched document does not match the expected SHA-256. */
export class DocumentHashError extends Error {
  constructor(expected: string, actual: string) {
    super(`document SHA-256 mismatch: expected ${expected}, got ${actual}`);
    this.name = 'DocumentHashError';
  }
}

/** Reads the raw document bytes from an http(s) or file URL. */
export async function fetchDocument(url: string): Promise<string> {
  const scheme = new URL(url).protocol;
  if (scheme === 'file:') {
    return readFile(fileURLToPath(url), 'utf8');
  }
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`failed to fetch document ${url}: HTTP ${res.status}`);
  }
  return res.text();
}

/** Verifies the raw document content against the expected lowercase hex digest. */
export function verifySha256(raw: string, expected: string): void {
  const actual = createHash('sha256').update(raw, 'utf8').digest('hex');
  if (actual !== expected.toLowerCase()) {
    throw new DocumentHashError(expected.toLowerCase(), actual);
  }
}

/** Parses a raw AsyncAPI document as JSON, falling back to YAML. */
export function parseDocument(raw: string): Record<string, unknown> {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    try {
      parsed = parseYaml(raw);
    } catch (err) {
      throw new Error(`document is not valid JSON or YAML: ${(err as Error).message}`);
    }
  }
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('document must be a JSON/YAML object');
  }
  return parsed as Record<string, unknown>;
}

/**
 * Validates the raw document with `@asyncapi/parser`. Throws on any
 * error-severity diagnostic (or an unparseable document) so an invalid AsyncAPI
 * definition stops startup rather than surfacing per request.
 */
export async function validateDocument(raw: string): Promise<void> {
  const parser = new Parser();
  const { document, diagnostics } = await parser.parse(raw);
  // Spectral/asyncapi-parser severity: 0 = error, 1 = warn, 2 = info, 3 = hint.
  const errors = diagnostics.filter((d) => d.severity === 0);
  if (document === undefined || errors.length > 0) {
    const detail = errors.map((d) => d.message).join('; ') || 'document could not be parsed';
    throw new Error(`invalid AsyncAPI document: ${detail}`);
  }
}

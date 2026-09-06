// Regenerates the vendored DSL JSON Schema from the exact serverlessworkflow SDK
// jar that dws-controller pins. Run manually (`pnpm vendor:schema`) after a
// controller-side SDK bump — never during `pnpm build`, which must not need
// network access.
import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { inflateRawSync } from 'node:zlib';
import { parse } from 'yaml';

const here = dirname(fileURLToPath(import.meta.url));
const POM = join(here, '..', '..', 'dws-controller', 'pom.xml');
const OUT_DIR = join(here, '..', 'src', 'definition-validation', 'schema');
const ENTRY = 'schema/workflow.yaml';

/** Reads <serverlessworkflow.version> out of dws-controller's pom. */
async function pinnedSdkVersion() {
  const pom = await readFile(POM, 'utf8');
  const match = pom.match(/<serverlessworkflow\.version>([^<]+)<\/serverlessworkflow\.version>/);
  if (!match) throw new Error(`No <serverlessworkflow.version> in ${POM}`);
  return match[1];
}

/** Extracts one entry from a zip buffer via its central directory. */
function readZipEntry(buf, name) {
  // End of central directory record: signature 0x06054b50, scanned from the tail.
  let eocd = buf.length - 22;
  while (eocd >= 0 && buf.readUInt32LE(eocd) !== 0x06054b50) eocd -= 1;
  if (eocd < 0) throw new Error('Not a zip archive: no EOCD record');
  let offset = buf.readUInt32LE(eocd + 16);
  const count = buf.readUInt16LE(eocd + 10);
  for (let i = 0; i < count; i += 1) {
    const nameLen = buf.readUInt16LE(offset + 28);
    const extraLen = buf.readUInt16LE(offset + 30);
    const commentLen = buf.readUInt16LE(offset + 32);
    const localOffset = buf.readUInt32LE(offset + 42);
    const entryName = buf.toString('utf8', offset + 46, offset + 46 + nameLen);
    if (entryName === name) {
      const method = buf.readUInt16LE(localOffset + 8);
      const localNameLen = buf.readUInt16LE(localOffset + 26);
      const localExtraLen = buf.readUInt16LE(localOffset + 28);
      const start = localOffset + 30 + localNameLen + localExtraLen;
      const compressedSize = buf.readUInt32LE(offset + 20);
      const body = buf.subarray(start, start + compressedSize);
      return method === 0 ? body : inflateRawSync(body);
    }
    offset += 46 + nameLen + extraLen + commentLen;
  }
  throw new Error(`Entry ${name} not found in archive`);
}

const sdkVersion = await pinnedSdkVersion();
const sourceJar =
  `https://repo1.maven.org/maven2/io/serverlessworkflow/serverlessworkflow-types/` +
  `${sdkVersion}/serverlessworkflow-types-${sdkVersion}.jar`;

const response = await fetch(sourceJar);
if (!response.ok) throw new Error(`GET ${sourceJar} → ${response.status}`);
const jar = Buffer.from(await response.arrayBuffer());

const yamlText = readZipEntry(jar, ENTRY).toString('utf8');
const schema = parse(yamlText);
if (typeof schema?.$id !== 'string') throw new Error('Extracted schema has no $id');

await mkdir(OUT_DIR, { recursive: true });
await writeFile(join(OUT_DIR, 'workflow-schema.json'), `${JSON.stringify(schema, null, 2)}\n`);
await writeFile(
  join(OUT_DIR, 'provenance.json'),
  `${JSON.stringify(
    {
      sdkVersion,
      schemaId: schema.$id,
      sourceJar,
      sha256: createHash('sha256').update(yamlText).digest('hex'),
    },
    null,
    2,
  )}\n`,
);

console.log(`Vendored ${schema.$id} from ${sdkVersion}`);

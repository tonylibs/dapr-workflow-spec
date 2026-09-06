import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import provenance from './schema/provenance.json';
import schema from './schema/workflow-schema.json';

const POM = join(__dirname, '..', '..', '..', 'dws-controller', 'pom.xml');

describe('vendored DSL schema provenance', () => {
  it('records the schema identity it was generated from', () => {
    expect((schema as { $id: string }).$id).toBe(provenance.schemaId);
  });

  it('matches the SDK version dws-controller pins', () => {
    if (!existsSync(POM)) {
      // dws-admin can be checked out without its sibling components; a packaging
      // choice must not turn into a red build.
      console.warn(`Skipping: ${POM} not present in this checkout`);
      return;
    }
    const pom = readFileSync(POM, 'utf8');
    const pinned = pom.match(/<serverlessworkflow\.version>([^<]+)</)?.[1];
    expect(pinned).toBeDefined();
    expect(provenance.sdkVersion).toBe(pinned);
  });
});

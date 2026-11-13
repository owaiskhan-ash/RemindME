import test from 'node:test';
import assert from 'node:assert/strict';
import { readdir, readFile, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { isReservedDirectory } from '../site/catalog.js';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

test('the fallback snapshot matches the collection files on disk', async () => {
  const tree = [];
  async function walk(relative = '') {
    const entries = await readdir(path.join(root, relative), { withFileTypes: true });
    for (const entry of entries) {
      if (!relative && (!entry.isDirectory() || isReservedDirectory(entry.name))) continue;
      const name = relative ? `${relative}/${entry.name}` : entry.name;
      if (entry.isDirectory()) await walk(name);
      else if (entry.isFile()) tree.push({ path: name, type: 'blob', size: (await stat(path.join(root, name))).size });
    }
  }
  await walk();
  tree.sort((a, b) => a.path.localeCompare(b.path, 'en'));
  const snapshot = JSON.parse(await readFile(path.join(root, 'site/snapshot.json'), 'utf8'));
  assert.deepEqual(snapshot.tree, tree, 'Run npm run snapshot after changing collection files.');
});

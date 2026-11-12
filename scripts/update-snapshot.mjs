import { readdir, stat, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { isReservedDirectory } from '../site/catalog.js';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const tree = [];

async function walk(relative) {
  const directory = path.join(root, relative);
  const entries = await readdir(directory, { withFileTypes: true });
  for (const entry of entries) {
    const name = relative ? `${relative}/${entry.name}` : entry.name;
    if (!relative && (!entry.isDirectory() || isReservedDirectory(entry.name))) continue;
    if (entry.isDirectory()) {
      await walk(name);
    } else if (entry.isFile()) {
      tree.push({ path: name, type: 'blob', size: (await stat(path.join(root, name))).size });
    }
  }
}

await walk('');
tree.sort((a, b) => a.path.localeCompare(b.path, 'en'));
await writeFile(path.join(root, 'site/snapshot.json'), `${JSON.stringify({ tree }, null, 2)}\n`);
console.log(`Saved ${tree.length} files to site/snapshot.json.`);

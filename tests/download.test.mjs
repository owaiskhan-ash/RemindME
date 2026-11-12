import test from 'node:test';
import assert from 'node:assert/strict';
import JSZip from '../site/vendor/jszip.min.js';
import { createFolderZip } from '../site/download.js';

test('the downloaded ZIP contains the complete folder, nested files and original bytes', async () => {
  const collection = {
    name: '9th Nov Day',
    files: [
      { path: '9th Nov Day/one.jpg', relativePath: 'one.jpg' },
      { path: '9th Nov Day/sub/notes.md', relativePath: 'sub/notes.md' },
    ],
  };
  const values = {
    '9th Nov Day/one.jpg': new Uint8Array([0, 255, 7, 12]),
    '9th Nov Day/sub/notes.md': new TextEncoder().encode('notes stay with the images'),
  };
  const progress = [];
  const bytes = await createFolderZip(collection, {
    JSZip,
    fetchFile: async path => new Response(values[path]),
    onProgress: value => progress.push(value),
    outputType: 'uint8array',
  });
  const zip = await JSZip.loadAsync(bytes);
  assert.deepEqual(await zip.file('9th Nov Day/one.jpg').async('uint8array'), values['9th Nov Day/one.jpg']);
  assert.equal(await zip.file('9th Nov Day/sub/notes.md').async('string'), 'notes stay with the images');
  assert.ok(progress.some(item => item.phase === 'fetch' && item.completed === 2));
  assert.ok(progress.some(item => item.phase === 'zip' && item.percent === 100));
});

test('fails rather than producing a partial ZIP if a file cannot be retrieved', async () => {
  const collection = { name: 'Folder', files: [{ path: 'Folder/missing.jpg', relativePath: 'missing.jpg' }] };
  await assert.rejects(createFolderZip(collection, {
    JSZip,
    fetchFile: async () => new Response('missing', { status: 404 }),
    outputType: 'uint8array',
  }), /Could not fetch missing.jpg/);
});

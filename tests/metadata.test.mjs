import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { BRANCH, REPOSITORY } from '../site/catalog.js';

const root = new URL('../', import.meta.url);
const html = await readFile(new URL('index.html', root), 'utf8');
const expectedUrl = 'https://bookstorage.justsimpleapps.online/';

function meta(attribute, name) {
  const tags = html.match(/<meta\b[^>]*>/gi) || [];
  const tag = tags.find(item => item.includes(`${attribute}="${name}"`));
  return tag?.match(/\bcontent="([^"]*)"/)?.[1];
}

function jpegSizeAndMetadata(bytes) {
  assert.equal(bytes.readUInt16BE(0), 0xffd8, 'share image must be a JPEG');
  let offset = 2;
  let dimensions;
  const extraMetadata = [];
  while (offset < bytes.length) {
    assert.equal(bytes[offset], 0xff, 'invalid JPEG marker');
    while (bytes[offset] === 0xff) offset++;
    const marker = bytes[offset++];
    if (marker === 0xda || marker === 0xd9) break;
    const length = bytes.readUInt16BE(offset);
    assert.ok(length >= 2);
    if ((marker >= 0xe1 && marker <= 0xef) || marker === 0xfe) extraMetadata.push(marker);
    if ([0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf].includes(marker)) {
      dimensions = { height: bytes.readUInt16BE(offset + 3), width: bytes.readUInt16BE(offset + 5) };
    }
    offset += length;
  }
  return { dimensions, extraMetadata };
}

test('canonical and share metadata use the provided domain', () => {
  assert.match(html, /<title>Book Storage<\/title>/);
  assert.match(html, /<link rel="canonical" href="https:\/\/bookstorage\.justsimpleapps\.online\/">/);
  assert.equal(meta('property', 'og:type'), 'website');
  assert.equal(meta('property', 'og:title'), 'Book Storage');
  assert.equal(meta('property', 'og:url'), expectedUrl);
  assert.equal(meta('property', 'og:image'), `${expectedUrl}site/share-card.jpg`);
  assert.equal(meta('property', 'og:image:width'), '1200');
  assert.equal(meta('property', 'og:image:height'), '630');
  assert.ok(meta('property', 'og:description'));
  assert.ok(meta('property', 'og:image:alt'));
  assert.equal(meta('name', 'twitter:card'), 'summary_large_image');
});

test('folder discovery and the fallback link use the renamed repository', () => {
  assert.equal(REPOSITORY, 'owaiskhan-ash/Storage-Books');
  assert.ok(html.includes(`href="https://github.com/${REPOSITORY}/tree/${BRANCH}"`));
});

test('share image has the advertised dimensions and no extra JPEG metadata', async () => {
  const image = await readFile(new URL('site/share-card.jpg', root));
  const { dimensions, extraMetadata } = jpegSizeAndMetadata(image);
  assert.deepEqual(dimensions, { width: 1200, height: 630 });
  assert.deepEqual(extraMetadata, []);
  assert.ok(image.length < 1_000_000);
});

test('site text and documentation contain no em dashes', async () => {
  for (const file of ['index.html', 'site/app.js', 'README.md']) {
    const contents = await readFile(new URL(file, root), 'utf8');
    assert.doesNotMatch(contents, /\u2014|&mdash;|&#(?:8212|x2014);/i, file);
  }
});

import test from 'node:test';
import assert from 'node:assert/strict';
import { collectionsFromTree, encodePath, folderTheme, formatFolderDate, parseFolderDate, choosePreviews } from '../site/catalog.js';

test('parses the existing folder convention and dates with an explicit year', () => {
  assert.deepEqual(parseFolderDate('7th Nov Day'), {
    year: 2026, month: 11, day: 7, inferredYear: true, timestamp: Date.UTC(2026, 10, 7),
  });
  assert.equal(formatFolderDate(parseFolderDate('7th Nov Day')), 'NOVEMBER 07');
  assert.equal(formatFolderDate(parseFolderDate('16th November Day')), 'NOVEMBER 16');
  assert.ok(parseFolderDate('16th November Day').timestamp > parseFolderDate('7th Nov Day').timestamp);
  assert.equal(formatFolderDate(parseFolderDate('9th November Day 2027')), 'NOVEMBER 09, 2027');
  assert.equal(formatFolderDate(parseFolderDate('November 9, 2026')), 'NOVEMBER 09, 2026');
  assert.equal(parseFolderDate('2027-01-02').timestamp, Date.UTC(2027, 0, 2));
  assert.equal(parseFolderDate('09-11-2026').timestamp, Date.UTC(2026, 10, 9));
});

test('invalid or undated folder names are not mistaken for dates', () => {
  assert.equal(parseFolderDate('31st Feb 2026'), null);
  assert.equal(parseFolderDate('2026-02-30'), null);
  assert.equal(parseFolderDate('Summer photos'), null);
  assert.equal(formatFolderDate(null), 'UNDATED FOLDER');
  assert.ok(parseFolderDate('29th Feb 2028'));
});

test('sorts collections newest first, keeps nested files, and ignores site infrastructure', () => {
  const tree = [
    { path: '7th Nov Day/01.jpg', type: 'blob', size: 30 },
    { path: '7th Nov Day/notes.md', type: 'blob', size: 10 },
    { path: '9th Nov Day/nested/01.PNG', type: 'blob', size: 50 },
    { path: '2027-01-02/picture.webp', type: 'blob', size: 60 },
    { path: '2025-12-31/photo.jpg', type: 'blob', size: 70 },
    { path: 'miscellaneous/image.gif', type: 'blob', size: 90 },
    { path: 'site/fonts/ignore.png', type: 'blob', size: 200 },
    { path: '.github/ignore.jpg', type: 'blob', size: 200 },
    { path: 'scripts/ignore.jpg', type: 'blob', size: 200 },
    { path: 'README.md', type: 'blob', size: 10 },
  ];
  const collections = collectionsFromTree(tree);
  assert.deepEqual(collections.map(item => item.name), ['2027-01-02', '9th Nov Day', '7th Nov Day', '2025-12-31', 'miscellaneous']);
  assert.equal(collections[2].files.length, 2);
  assert.equal(collections[2].images.length, 1);
  assert.equal(collections[2].bytes, 40);
  assert.equal(collections[1].files[0].relativePath, 'nested/01.PNG');
});

test('URL-encodes paths and chooses lightweight, naturally ordered previews', () => {
  assert.equal(encodePath('9th Nov Day/a #1.jpg'), '9th%20Nov%20Day/a%20%231.jpg');
  const [collection] = collectionsFromTree([
    { path: '9th Nov Day/10.jpg', type: 'blob', size: 200 },
    { path: '9th Nov Day/2.jpg', type: 'blob', size: 100 },
    { path: '9th Nov Day/1.jpg', type: 'blob', size: 2_000_000 },
  ]);
  assert.deepEqual(choosePreviews(collection, 2).map(file => file.relativePath), ['2.jpg', '10.jpg']);
});

test('folder names with night or day get the matching card scene', () => {
  assert.equal(folderTheme('16 November Night'), 'night');
  assert.equal(folderTheme('16th November Day'), 'day');
  assert.equal(folderTheme('7th Nov Day'), 'day');
  assert.equal(folderTheme('Photos - Night'), 'night');
  assert.equal(folderTheme('Nightly reading'), 'plain');
  assert.equal(folderTheme('7th Nov'), 'plain');
  assert.equal(folderTheme('Summer photos'), 'plain');
  assert.equal(folderTheme(undefined), 'plain');
  const collections = collectionsFromTree([
    { path: '16 November Night/01.jpg', type: 'blob', size: 10 },
    { path: '16th November Day/01.jpg', type: 'blob', size: 10 },
  ]);
  assert.deepEqual(collections.map(item => item.theme), ['night', 'day']);
});

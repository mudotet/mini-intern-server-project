import test from 'node:test';
import assert from 'node:assert/strict';
import { buildFileTree, ancestorPaths } from '../src/fileTree.js';

const files = [
  { path: 'game/z.proto', content: 'z' },
  { path: 'root.proto', content: 'root' },
  { path: 'game/model/shop.proto', content: 'model' },
  { path: 'game/app/shop.proto', content: 'app' },
  { path: 'alpha/a.proto', content: 'alpha' },
];

function node(kind, name, path, fileCount, changedCount, children) {
  return { kind, name, path, fileCount, changedCount, ...(children ? { children } : {}) };
}

test('nested folders aggregate descendants and disambiguate duplicate basenames', () => {
  const before = structuredClone(files);
  assert.deepEqual(buildFileTree(files, new Set(['game/model/shop.proto', 'root.proto'])), [
    node('folder', 'alpha', 'alpha', 1, 0, [node('file', 'a.proto', 'alpha/a.proto', 1, 0)]),
    node('folder', 'game', 'game', 3, 1, [
      node('folder', 'app', 'game/app', 1, 0, [node('file', 'shop.proto', 'game/app/shop.proto', 1, 0)]),
      node('folder', 'model', 'game/model', 1, 1, [node('file', 'shop.proto', 'game/model/shop.proto', 1, 1)]),
      node('file', 'z.proto', 'game/z.proto', 1, 0),
    ]),
    node('file', 'root.proto', 'root.proto', 1, 1),
  ]);
  assert.deepEqual(files, before);
});

test('search-filtered files retain only their actual ancestors and counts', () => {
  const result = buildFileTree(files.filter(file => file.path.includes('model')), new Set(['game/app/shop.proto']));
  assert.deepEqual(result, [node('folder', 'game', 'game', 1, 0, [
    node('folder', 'model', 'game/model', 1, 0, [node('file', 'shop.proto', 'game/model/shop.proto', 1, 0)]),
  ])]);
});

test('empty inputs and default changed set do not invent folders or modifications', () => {
  assert.deepEqual(buildFileTree([]), []);
  assert.deepEqual(buildFileTree([{ path: 'schema.proto' }]), [node('file', 'schema.proto', 'schema.proto', 1, 0)]);
});

test('ancestor paths identify every parent in root-to-leaf order', () => {
  assert.deepEqual(ancestorPaths('game/app/shop/shop.proto'), ['game', 'game/app', 'game/app/shop']);
  assert.deepEqual(ancestorPaths('game/shop.proto'), ['game']);
  assert.deepEqual(ancestorPaths('schema.proto'), []);
});

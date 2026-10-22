import test from 'node:test';
import assert from 'node:assert/strict';
import { alignedDiff } from '../src/sourceDiff.js';

test('replacement hunks align unchanged lines and pad the shorter side', () => {
  const result = alignedDiff('first\nold\nlast\n', 'first\nnew\nextra\nlast\n');
  assert.equal(result.left.length, result.right.length);
  assert.deepEqual(result.left.map(row => row.number), [1, 2, undefined, 3]);
  assert.deepEqual(result.right.map(row => row.number), [1, 2, 3, 4]);
  assert.equal(result.left[1].kind, 'removed');
  assert.equal(result.right[1].kind, 'added');
  assert.equal(result.left[2].kind, 'placeholder');
  assert.equal(result.left[3].text, 'last');
  assert.equal(result.right[3].text, 'last');
  assert.equal(result.additions, 2);
  assert.equal(result.removals, 1);
});

test('standalone insertions and deletions preserve line numbers on both sides', () => {
  const inserted = alignedDiff('last\n', 'new\nlast\n');
  assert.deepEqual(inserted.left, [{ kind: 'placeholder' }, { text: 'last', number: 1 }]);
  assert.deepEqual(inserted.right.map(row => row.number), [1, 2]);
  const deleted = alignedDiff('old\nlast\n', 'last\n');
  assert.deepEqual(deleted.right, [{ kind: 'placeholder' }, { text: 'last', number: 1 }]);
  assert.deepEqual(deleted.left.map(row => row.number), [1, 2]);
});

test('empty and unchanged sources have no changes', () => {
  assert.deepEqual(alignedDiff('', ''), { left: [], right: [], additions: 0, removals: 0 });
  const unchanged = alignedDiff('same\n\nlast\n', 'same\n\nlast\n');
  assert.deepEqual(unchanged.left, unchanged.right);
  assert.equal(unchanged.left.length, 3);
  assert.equal(unchanged.additions, 0);
  assert.equal(unchanged.removals, 0);
});

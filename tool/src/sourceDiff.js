import { diffLines } from 'diff';

export function alignedDiff(original, current) {
  const left = [], right = [];
  let oldLine = 0, newLine = 0, additions = 0, removals = 0;
  const parts = diffLines(original, current);
  const lines = value => value ? value.replace(/\n$/, '').split('\n') : [];

  for (let index = 0; index < parts.length;) {
    const part = parts[index];
    if (!part.added && !part.removed) {
      for (const text of lines(part.value)) {
        left.push({ text, number: ++oldLine });
        right.push({ text, number: ++newLine });
      }
      index++;
      continue;
    }

    const removed = [], added = [];
    while (index < parts.length && (parts[index].added || parts[index].removed)) {
      const change = parts[index++];
      (change.removed ? removed : added).push(...lines(change.value));
    }
    removals += removed.length;
    additions += added.length;
    for (let row = 0; row < Math.max(removed.length, added.length); row++) {
      left.push(row < removed.length ? { text: removed[row], number: ++oldLine, kind: 'removed' } : { kind: 'placeholder' });
      right.push(row < added.length ? { text: added[row], number: ++newLine, kind: 'added' } : { kind: 'placeholder' });
    }
  }
  return { left, right, additions, removals };
}

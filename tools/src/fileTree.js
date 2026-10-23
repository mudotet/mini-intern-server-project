export function ancestorPaths(filePath) {
  const parts = filePath.split('/');
  return parts.slice(0, -1).map((_, index) => parts.slice(0, index + 1).join('/'));
}

export function buildFileTree(files, changed = new Set()) {
  const roots = [];
  const folders = new Map();
  const seen = new Set();
  for (const file of files) {
    if (seen.has(file.path)) continue;
    seen.add(file.path);
    let children = roots;
    for (const path of ancestorPaths(file.path)) {
      if (!folders.has(path)) {
        const folder = { kind: 'folder', name: path.split('/').at(-1), path, fileCount: 0, changedCount: 0, children: [] };
        folders.set(path, folder);
        children.push(folder);
      }
      const folder = folders.get(path);
      folder.fileCount++;
      if (changed.has(file.path)) folder.changedCount++;
      children = folder.children;
    }
    children.push({ kind: 'file', name: file.path.split('/').at(-1), path: file.path, fileCount: 1, changedCount: changed.has(file.path) ? 1 : 0 });
  }
  function sort(nodes) {
    nodes.sort((a, b) => a.kind === b.kind ? a.name.localeCompare(b.name, 'en') : a.kind === 'folder' ? -1 : 1);
    for (const node of nodes) if (node.children) sort(node.children);
    return nodes;
  }
  return sort(roots);
}

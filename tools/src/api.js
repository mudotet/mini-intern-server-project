export async function request(path, body) {
  let response;
  try {
    response = await fetch(`/api/${path}`, body === undefined ? {} : {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
    });
  } catch { throw new Error('Cannot reach Proto Studio. Check your local server and try again. Your draft is still saved.'); }
  if (!response.ok) {
    let data;
    try { data = await response.json(); } catch { data = { error: `Server returned HTTP ${response.status}.` }; }
    const error = new Error(data.error || 'The operation could not be completed.');
    error.diagnostics = data.diagnostics;
    throw error;
  }
  return path === 'export' ? response.blob() : response.json();
}

export const storageKey = 'proto-studio-workspace-v1';
export const sameFiles = (a, b) => a.length === b.length && a.every(file => b.some(other => other.path === file.path && other.content === file.content));
export function readSaved() {
  const raw = localStorage.getItem(storageKey);
  if (!raw) return null;
  const saved = JSON.parse(raw);
  const validFiles = files => Array.isArray(files) && files.length > 0 && files.every(file => typeof file.path === 'string' && typeof file.content === 'string');
  if (!validFiles(saved.files) || !validFiles(saved.original) || !saved.revision || typeof saved.revision.repository !== 'string') throw new Error('The saved draft is invalid.');
  return saved;
}

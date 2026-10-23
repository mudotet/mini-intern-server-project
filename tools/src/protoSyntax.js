const keywords = new Set(['syntax', 'edition', 'package', 'import', 'public', 'weak', 'option', 'message', 'enum', 'service', 'rpc', 'returns', 'stream', 'repeated', 'optional', 'required', 'oneof', 'map', 'reserved', 'to', 'max', 'extensions', 'extend', 'group']);
const types = new Set(['double', 'float', 'int32', 'int64', 'uint32', 'uint64', 'sint32', 'sint64', 'fixed32', 'fixed64', 'sfixed32', 'sfixed64', 'bool', 'string', 'bytes']);

// Tokenize the complete source so block comments keep their color across lines.
// Ranges are line-relative UTF-16 offsets, matching JavaScript's diff strings.
export function tokenizeProto(source) {
  const pattern = /\/\*[\s\S]*?(?:\*\/|$)|\/\/[^\n]*|"(?:\\[^\r\n]|[^"\\\r\n])*"?|'(?:\\[^\r\n]|[^'\\\r\n])*'?|[A-Za-z_][\w.]*|-?(?:0[xX][\da-fA-F]+|\d+(?:\.\d*)?(?:[eE][+-]?\d+)?)|[{}()[\]<>;,=:+-]|[^\S\n]+|[\s\S]/g;
  const lines = [[]];
  let column = 0;
  for (const match of source.matchAll(pattern)) {
    const text = match[0];
    const kind = text.startsWith('//') || text.startsWith('/*') ? 'comment'
      : text.startsWith('"') || text.startsWith("'") ? 'string'
      : keywords.has(text) ? 'keyword'
      : types.has(text) || /^(?:\w+\.)*[A-Z]\w*$/.test(text) ? 'type'
      : text === 'true' || text === 'false' ? 'boolean'
      : /^-?\d/.test(text) ? 'number'
      : /^[{}()[\]<>;,=:+-]$/.test(text) ? 'punctuation' : 'plain';
    for (const [index, part] of text.split('\n').entries()) {
      if (index) { lines.push([]); column = 0; }
      if (part) lines.at(-1).push({ text: part, kind, start: column, end: column + part.length });
      column += part.length;
    }
  }
  return lines;
}

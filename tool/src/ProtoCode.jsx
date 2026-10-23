// Render text through React, never HTML. Diff ranges and syntax colors overlap
// without dropping characters or interpreting schema content as markup.
export default function ProtoCode({ tokens = [], changes = [] }) {
  let rangeIndex = 0;
  return tokens.flatMap(token => {
    const pieces = [];
    let position = token.start;
    while (position < token.end) {
      while (rangeIndex < changes.length && changes[rangeIndex].end <= position) rangeIndex++;
      const range = changes[rangeIndex];
      const marked = range && range.start <= position;
      const end = Math.min(token.end, range ? marked ? range.end : range.start : token.end);
      const text = <span className={`proto-${token.kind}`}>{token.text.slice(position - token.start, end - token.start)}</span>;
      pieces.push(marked ? <mark key={position} className={range.kind === 'added' ? 'inline-added' : 'inline-removed'}>{text}</mark> : <span key={position}>{text}</span>);
      position = end;
    }
    return pieces;
  });
}

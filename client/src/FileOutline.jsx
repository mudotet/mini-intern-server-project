import { useMemo } from 'react';
import { BracketsCurly, ListNumbers, Plus, CaretRight } from '@phosphor-icons/react';

export default function FileOutline({ declarations, selected, onSelect, packageName, path, onAdd, busy, changed }) {
  const tree = useMemo(() => {
    const nodes = new Map(declarations.map(item => [item.fullName, { item, children: [] }]));
    const roots = [];
    for (const node of nodes.values()) {
      const parent = node.item.fullName.includes('.') ? node.item.fullName.slice(0, node.item.fullName.lastIndexOf('.')) : null;
      if (nodes.has(parent)) nodes.get(parent).children.push(node);
      else roots.push(node);
    }
    return roots;
  }, [declarations]);
  function renderNodes(nodes, depth = 0) {
    return <ul>{nodes.map(({ item, children }) => <li key={item.fullName}><button className={`outline-item ${selected === item.fullName ? 'active' : ''}`} style={{ '--depth': depth }} aria-label={`Chọn ${item.fullName}`} aria-current={selected === item.fullName ? 'true' : undefined} onClick={() => onSelect(item.fullName)} title={item.fullName}>{item.fields ? <BracketsCurly size={17}/> : <ListNumbers size={17}/>}<span>{item.name}<small>{item.fields ? 'Message' : 'Enum'}</small></span><b>{(item.fields || item.values).length}</b>{selected === item.fullName && <CaretRight size={12}/>}</button>{children.length > 0 && renderNodes(children, depth + 1)}</li>)}</ul>;
  }
  return <aside className="context-aside"><div className="outline-heading"><h2>Cấu trúc file</h2><span>{declarations.length} định nghĩa</span></div><div className="outline-file"><span>{path?.split('/').at(-1)}</span><code>{packageName || '(không có package)'}</code></div><nav className="outline-navigation" aria-label="Cấu trúc file">{tree.length ? renderNodes(tree) : <p className="outline-empty">File chưa có message hoặc enum.</p>}</nav><div className="outline-actions"><span>Thêm định nghĩa</span><div><button className="button small" disabled={busy} onClick={() => onAdd('addMessage')}><Plus size={15}/>Message</button><button className="button small" disabled={busy} onClick={() => onAdd('addEnum')}><Plus size={15}/>Enum</button></div></div><div className="outline-context"><span>{changed ? 'Bản nháp có thay đổi' : 'Khớp với bản gốc'}</span><p>Chọn một định nghĩa để chỉnh sửa trong file này.</p></div></aside>;
}

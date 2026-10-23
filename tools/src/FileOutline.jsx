import { useMemo } from 'react';
import { BracketsCurly, ListNumbers, Plus, CheckCircle, PencilSimple, FileText } from '@phosphor-icons/react';

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

  function renderNodes(nodes, nested = false) {
    return <ul className={nested ? 'outline-children' : 'outline-roots'}>{nodes.map(({ item, children }) => {
      const active = selected === item.fullName;
      const count = (item.fields || item.values).length;
      return <li key={item.fullName}>
        <button className={`outline-item ${active ? 'active' : ''}`} aria-label={`Select ${item.fullName}`} aria-current={active ? 'true' : undefined} onClick={() => onSelect(item.fullName)} title={item.fullName}>
          <span className={`outline-symbol ${item.fields ? '' : 'outline-enum'}`} aria-hidden="true">{item.fields ? <BracketsCurly size={17}/> : <ListNumbers size={17}/>}</span>
          <span className="outline-label"><span className="outline-name">{item.name}</span><span className="outline-meta"><small>{item.fields ? 'Message' : 'Enum'} · {count} {item.fields ? count === 1 ? 'field' : 'fields' : count === 1 ? 'value' : 'values'}</small>{active && <span className="outline-editing"><CheckCircle size={12} weight="fill" aria-hidden="true"/>Editing</span>}</span></span>
        </button>
        {children.length > 0 && renderNodes(children, true)}
      </li>;
    })}</ul>;
  }
  return <aside className="context-aside">
    <div className="outline-heading"><div><h2>File outline</h2><span>{declarations.length} definitions</span></div><p>Select a definition to edit its fields.</p></div>
    <div className="outline-file"><span><FileText size={16} aria-hidden="true"/>{path?.split('/').at(-1)}</span><code>{packageName || '(no package)'}</code></div>
    <nav className="outline-navigation" aria-label="File outline">{tree.length ? renderNodes(tree) : <p className="outline-empty">This file has no messages or enums yet.</p>}</nav>
    <div className="outline-actions"><span>Add definition</span><div><button className="button small" disabled={busy} onClick={() => onAdd('addMessage')} title="A message groups related fields"><Plus size={15} aria-hidden="true"/>Message</button><button className="button small" disabled={busy} onClick={() => onAdd('addEnum')} title="An enum defines a set of named values"><Plus size={15} aria-hidden="true"/>Enum</button></div></div>
    <div className={`outline-file-status ${changed ? 'has-changes' : ''}`} aria-label="File status">{changed ? <PencilSimple size={14} aria-hidden="true"/> : <CheckCircle size={14} aria-hidden="true"/>}<span>{changed ? 'Draft has changes' : 'Matches original'}</span></div>
  </aside>;
}

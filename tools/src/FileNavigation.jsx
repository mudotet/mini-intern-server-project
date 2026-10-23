import { useEffect, useMemo, useState } from 'react';
import { MagnifyingGlass, FileText, FolderSimple, FolderOpen, CaretRight, X, BookOpen, ArrowRight } from '@phosphor-icons/react';
import { ancestorPaths, buildFileTree } from './fileTree.js';

export default function FileNavigation({ files, selected, onSelect, query, setQuery, onHelp, close, changed }) {
  const [expanded, setExpanded] = useState(() => new Set(ancestorPaths(selected || '')));
  useEffect(() => {
    setExpanded(current => new Set([...current, ...ancestorPaths(selected || '')]));
  }, [selected]);
  const tree = useMemo(() => buildFileTree(files.filter(file => file.path.toLowerCase().includes(query.trim().toLowerCase())), changed), [files, query, changed]);
  function toggle(path) {
    setExpanded(current => { const next = new Set(current); if (next.has(path)) next.delete(path); else next.add(path); return next; });
  }
  function renderNodes(nodes, depth = 0) {
    return <ul>{nodes.map(node => {
      if (node.kind === 'file') return <li key={node.path}><button className={`file-item ${selected === node.path ? 'selected' : ''}`} style={{ '--depth': depth }} title={node.path} aria-label={`Open ${node.path}`} aria-current={selected === node.path ? 'page' : undefined} onClick={() => onSelect(node.path)}><FileText size={17}/><span>{node.name}</span>{node.changedCount > 0 && <i className="change-dot" aria-label="Changed"/>}</button></li>;
      const open = !!query.trim() || expanded.has(node.path);
      return <li key={node.path}><button className={`folder-item ${open ? 'expanded' : ''}`} style={{ '--depth': depth }} title={node.path} aria-label={`Folder ${node.path}`} aria-expanded={open} onClick={() => toggle(node.path)}><CaretRight className="folder-caret" size={12}/>{open ? <FolderOpen size={18}/> : <FolderSimple size={18}/>}<span>{node.name}</span>{node.changedCount > 0 && <i className="change-dot" title={`${node.changedCount} changed files`} aria-label={`${node.changedCount} changed files`}/>}<small>{node.fileCount}</small></button>{open && renderNodes(node.children, depth + 1)}</li>;
    })}</ul>;
  }
  return <><div className="sidebar-top"><div><h2>Files</h2><span>{files.length} schemas in workspace</span></div><button className="icon-button mobile-only" aria-label="Close file browser" onClick={close}><X size={20}/></button></div>
    <label className="search"><MagnifyingGlass size={17}/><input disabled={!files.length} aria-label="Search schemas" value={query} onChange={event => setQuery(event.target.value)} placeholder="Search files or folders…"/>{query && <button type="button" className="search-clear" aria-label="Clear search" onClick={() => setQuery('')}><X size={14}/></button>}</label>
    <nav className="file-tree" aria-label="Schema files">{tree.length ? renderNodes(tree) : <div className="search-empty"><MagnifyingGlass size={25}/><p>{files.length ? 'No schemas found' : 'No schemas loaded'}</p>{files.length > 0 && <button className="text-button" onClick={() => setQuery('')}>Clear search</button>}</div>}</nav>
    <div className="sidebar-bottom"><span><i className="change-dot"/>{changed.size} changed {changed.size === 1 ? 'file' : 'files'}</span><button className="sidebar-help" onClick={onHelp}><BookOpen size={17}/><span>Help</span><ArrowRight size={14}/></button></div></>;
}

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
      if (node.kind === 'file') return <li key={node.path}><button className={`file-item ${selected === node.path ? 'selected' : ''}`} style={{ '--depth': depth }} title={node.path} aria-label={`Mở ${node.path}`} aria-current={selected === node.path ? 'page' : undefined} onClick={() => onSelect(node.path)}><FileText size={17}/><span>{node.name}</span>{node.changedCount > 0 && <i className="change-dot" aria-label="Có thay đổi"/>}</button></li>;
      const open = !!query.trim() || expanded.has(node.path);
      return <li key={node.path}><button className={`folder-item ${open ? 'expanded' : ''}`} style={{ '--depth': depth }} title={node.path} aria-label={`Thư mục ${node.path}`} aria-expanded={open} onClick={() => toggle(node.path)}><CaretRight className="folder-caret" size={12}/>{open ? <FolderOpen size={18}/> : <FolderSimple size={18}/>}<span>{node.name}</span>{node.changedCount > 0 && <i className="change-dot" title={`${node.changedCount} file có thay đổi`} aria-label={`${node.changedCount} file có thay đổi`}/>}<small>{node.fileCount}</small></button>{open && renderNodes(node.children, depth + 1)}</li>;
    })}</ul>;
  }
  return <><div className="sidebar-top"><div><h2>Files</h2><span>{files.length} schema trong workspace</span></div><button className="icon-button mobile-only" aria-label="Đóng danh sách file" onClick={close}><X size={20}/></button></div>
    <label className="search"><MagnifyingGlass size={17}/><input aria-label="Tìm schema" value={query} onChange={event => setQuery(event.target.value)} placeholder="Tìm file hoặc thư mục…"/>{query && <button type="button" className="search-clear" aria-label="Xóa tìm kiếm" onClick={() => setQuery('')}><X size={14}/></button>}</label>
    <nav className="file-tree" aria-label="Danh sách schema">{tree.length ? renderNodes(tree) : <div className="search-empty"><MagnifyingGlass size={25}/><p>Không tìm thấy schema</p><button className="text-button" onClick={() => setQuery('')}>Xóa tìm kiếm</button></div>}</nav>
    <div className="sidebar-bottom"><span><i className="change-dot"/>{changed.size} file có thay đổi</span><button className="sidebar-help" onClick={onHelp}><BookOpen size={17}/><span>Hướng dẫn</span><ArrowRight size={14}/></button></div></>;
}

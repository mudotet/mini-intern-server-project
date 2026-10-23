import { useMemo, useState } from 'react';
import { ArrowUpRight, ArrowRight, GitBranch, Files, BracketsCurly, Rows, PencilSimple } from '@phosphor-icons/react';

const numericTypes = new Set(['double', 'float', 'int32', 'int64', 'uint32', 'uint64', 'sint32', 'sint64', 'fixed32', 'fixed64', 'sfixed32', 'sfixed64']);

export default function Dashboard({ workspace, analysis, changed, onOpen, onConfigure }) {
  const [mapFile, setMapFile] = useState('');
  const data = useMemo(() => {
    if (!analysis?.valid) return null;
    const files = analysis.files.map(file => ({ ...file, count: file.messages.reduce((total, message) => total + message.fields.length, 0) }));
    const fields = files.flatMap(file => file.messages.flatMap(message => message.fields));
    const groups = [
      { name: 'Text', key: 'text', count: 0 },
      { name: 'Numbers', key: 'number', count: 0 },
      { name: 'Message / enum', key: 'reference', count: 0 },
      { name: 'Map', key: 'map', count: 0 },
      { name: 'Bool / bytes', key: 'other', count: 0 },
    ];
    for (const field of fields) {
      const key = field.type === 'map' ? 'map' : field.type === 'string' ? 'text' : numericTypes.has(field.type) ? 'number' : ['bool', 'bytes'].includes(field.type) ? 'other' : 'reference';
      groups.find(group => group.key === key).count++;
    }
    return { files, fields: fields.length, messages: files.reduce((n, file) => n + file.messages.length, 0), enums: files.reduce((n, file) => n + file.enums.length, 0), groups };
  }, [analysis]);
  const ranked = data ? [...data.files].sort((a, b) => b.count - a.count || a.path.localeCompare(b.path)).slice(0, 7) : [];
  const max = Math.max(1, ...ranked.map(file => file.count));
  const source = data?.files.find(file => file.path === mapFile) || data?.files.find(file => file.imports?.length) || data?.files[0];
  const imports = source?.imports || [];
  const importedBy = data?.files.filter(file => file.imports?.includes(source?.path)) || [];
  let offset = 0;
  return <section className="dashboard" aria-label="Schema overview">
    <div className="dashboard-heading"><div><p className="page-eyebrow">WORKSPACE / OVERVIEW</p><h1>Workspace overview</h1><p>Explore your schemas, field types, and draft changes.</p></div><button className="button" disabled={!workspace.files.length} onClick={() => onOpen()}><span>Open editor</span><ArrowUpRight size={17} aria-hidden="true"/></button></div>
    <div className="dashboard-stats">
      {[[Files, workspace.files.length, 'Schema files', 'files'], [BracketsCurly, data?.messages, 'Messages', 'messages'], [Rows, data?.fields, 'Fields', 'fields'], [PencilSimple, changed.size, 'Edited files', 'changed']].map(([Icon, count, label, key]) => <div className="dashboard-stat" key={key}><div><span>{label}</span><Icon size={18} aria-hidden="true"/></div><strong data-testid={`stat-${key}`}>{count ?? (workspace.files.length ? '—' : 0)}</strong><small>{key === 'changed' ? 'Compared with original' : key === 'messages' && data ? `${data.enums} enums in workspace` : key === 'fields' ? 'Includes nested messages' : 'In the current revision'}</small></div>)}
    </div>
    {!workspace.files.length ? <section className="workspace-setup" aria-label="Source configuration"><GitBranch size={32} aria-hidden="true"/><h2>Configure your proto source</h2><p>Add a GitHub repository and commit in Source settings.<br/>Your schemas and charts will appear here after loading.</p><button className="button primary" onClick={onConfigure}>Configure source</button></section> : !data ? <div className="empty-state"><h2>Statistics are not available yet</h2><p>Validate and fix your schemas to see accurate charts.</p></div> : <>
      <div className="dashboard-charts">
        <section className="dashboard-panel bar-panel" aria-label="Fields by file"><div className="panel-heading"><div><h2>Fields by file</h2><p>Top 7 files by field count · Select a bar to open</p></div><span className="chart-unit">fields</span></div>
          <div className="bar-chart">{ranked.map(file => <button key={file.path} className="chart-row" aria-label={`Open ${file.path}, ${file.count} fields`} onClick={() => onOpen(file.path)}><span className="chart-path" title={file.path}><strong>{file.path.split('/').at(-1)}</strong><small>{file.path.split('/').slice(0,-1).join('/') || '/'}</small></span><span className="bar-track" aria-hidden="true"><span style={{ width: `${file.count / max * 100}%` }} className={changed.has(file.path) ? 'bar-fill edited' : 'bar-fill'}/></span><b>{file.count}</b></button>)}</div>
          <div className="chart-footer"><span>Each field is counted once</span><span>{data.fields} total</span></div>
        </section>
        <section className="dashboard-panel" aria-label="Field type distribution"><div className="panel-heading"><div><h2>Field types</h2><p>Field types across the workspace</p></div></div>
          <div className="donut-chart"><svg viewBox="0 0 180 180" aria-hidden="true"><circle className="donut-track" cx="90" cy="90" r="69" fill="none" strokeWidth="18"/>{data.groups.filter(group => group.count).map(group => { const size = group.count / data.fields * 100; const start = offset; offset += size; return <circle key={group.key} className={`segment-${group.key}`} cx="90" cy="90" r="69" pathLength="100" fill="none" strokeWidth="18" strokeDasharray={`${size} ${100-size}`} strokeDashoffset={-start} transform="rotate(-90 90 90)"/>; })}</svg><div><strong>{data.fields}</strong><span>fields</span></div></div>
          <ul className="chart-legend">{data.groups.map(group => <li key={group.key}><i className={`segment-${group.key}`} aria-hidden="true"/><span>{group.name}</span><b>{group.count}</b><small>{data.fields ? Math.round(group.count / data.fields * 100) : 0}%</small></li>)}</ul>
        </section>
      </div>
      <section className="dashboard-panel import-panel" aria-label="Import relationships"><div className="panel-heading"><div><h2>Import relationships</h2><p>Explore dependencies and where each schema is used.</p></div><GitBranch size={21} aria-hidden="true"/></div>
        <label className="map-select">File in diagram<select aria-label="File in diagram" value={source?.path || ''} onChange={event => setMapFile(event.target.value)}>{data.files.map(file => <option key={file.path} value={file.path}>{file.path}</option>)}</select></label>
        <div className="import-map"><div className="map-column"><h3>Imported by <span>{importedBy.length}</span></h3>{importedBy.length ? importedBy.map(file => <button className="map-node" key={file.path} onClick={() => setMapFile(file.path)}><code>{file.path}</code><ArrowRight size={15} aria-hidden="true"/></button>) : <p className="map-empty">No files import this schema.</p>}</div><div className="map-column map-selected"><h3>Selected file</h3><button className="map-node selected" onClick={() => onOpen(source.path)}><code>{source?.path}</code><ArrowUpRight size={17} aria-hidden="true"/></button><small>{source?.messages.length} message · {source?.count} fields</small></div><div className="map-column"><h3>Import <span>{imports.length}</span></h3>{imports.length ? imports.map(path => <button className="map-node" key={path} disabled={!data.files.some(file => file.path === path)} onClick={() => setMapFile(path)}><ArrowRight size={15} aria-hidden="true"/><code>{path}</code>{!data.files.some(file => file.path === path) && <small>Library</small>}</button>) : <p className="map-empty">This file has no imports.</p>}</div></div>
      </section>
    </>}
  </section>;
}

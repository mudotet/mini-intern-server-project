import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Plus, PencilSimple, Trash, GithubLogo, DownloadSimple, CheckCircle, WarningCircle, List, BookOpen, ArrowCounterClockwise, CircleNotch, CaretRight, X, Cube, Stack, BracketsCurly, Sun, Moon, ChartBar, } from '@phosphor-icons/react';
import { request, readSaved, sameFiles, storageKey } from './api.js';
import { EditDialog, ErrorNotice, HelpDialog, ImportDialog, ExportDialog } from './Dialogs.jsx';
import { CompareDialog, SourceView } from './SourceViews.jsx';
import FileNavigation from './FileNavigation.jsx';
import FileOutline from './FileOutline.jsx';
import Dashboard from './Dashboard.jsx';
import useTheme from './useTheme.js';

const emptyWorkspace = { files: [], original: [], revision: { repository: '', sha: null, label: 'No commit selected' }, originalAnalysis: null };

function shortName(name) { return name?.split('.').at(-1) || ''; }
function typeLabel(field) { return field.type === 'map' ? `map<${field.keyType}, ${shortName(field.valueType)}>` : shortName(field.type); }
function FileDrawer({ children, onClose }) {
  const ref = useRef(null);
  useEffect(() => {
    const dialog = ref.current;
    const opener = document.activeElement;
    dialog.showModal();
    return () => {
      dialog.close();
      if (opener instanceof HTMLElement && opener.isConnected) opener.focus();
    };
  }, []);
  return <dialog ref={ref} className="file-drawer" aria-label="Schema files" onCancel={event => { event.preventDefault(); onClose(); }} onClick={event => { if (event.target === ref.current) onClose(); }}><aside className="sidebar mobile-sidebar">{children}</aside></dialog>;
}

export default function App() {
  const [theme, toggleTheme] = useTheme();
  const [view, setView] = useState('overview');
  const [workspace, setWorkspace] = useState(emptyWorkspace);
  const hasSchemas = workspace.files.length > 0;
  const [analysis, setAnalysis] = useState(null);
  const [selected, setSelected] = useState('');
  const [declarationName, setDeclarationName] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState('');
  const [storageError, setStorageError] = useState('');
  const [persistenceBlocked, setPersistenceBlocked] = useState(false);
  const [query, setQuery] = useState('');
  const [tab, setTab] = useState('edit');
  const [modal, setModal] = useState(null);
  const [drawer, setDrawer] = useState(false);
  const [valid, setValid] = useState(null);
  const [exportError, setExportError] = useState(null);

  useEffect(() => {
    if (!notice) return;
    const timer = window.setTimeout(() => setNotice(''), 5000);
    return () => window.clearTimeout(timer);
  }, [notice]);

  const adopt = useCallback(data => {
    const defaultFile = data.files[0];
    setWorkspace({ files: data.files, original: data.files, revision: data.revision, originalAnalysis: data.analysis });
    setAnalysis(data.analysis); setValid(data.analysis.valid); setSelected(defaultFile?.path || ''); setDeclarationName(''); setTab('edit'); setError(null); setNotice('');
  }, []);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      let saved;
      try { saved = readSaved(); } catch { setPersistenceBlocked(true); setStorageError('Your saved draft could not be read. Import a commit to open a workspace; the saved draft has not been deleted.'); }
      if (saved?.revision.source === 'github' && /^[a-fA-F0-9]{40}$/.test(saved.revision.sha || '')) {
        setWorkspace(saved); setSelected(saved.selected || saved.files[0]?.path || ''); setDeclarationName(saved.declarationName || '');
        setNotice('Restored your saved workspace.');
        const result = await request('analyze', { files: saved.files }); setAnalysis(result); setValid(result.valid);
      }
    } catch (failure) { setError(failure); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { load(); }, [load]);

  useEffect(() => {
    if (!workspace.files.length || loading || persistenceBlocked) return;
    try { localStorage.setItem(storageKey, JSON.stringify({ ...workspace, selected, declarationName })); }
    catch { setStorageError('Your browser could not save the draft. Export a ZIP before closing this page.'); }
  }, [workspace, selected, declarationName, loading, persistenceBlocked]);

  const changed = useMemo(() => new Set((workspace?.files || []).filter(file => workspace.original.find(original => original.path === file.path)?.content !== file.content).map(file => file.path)), [workspace]);
  const dirty = workspace ? !sameFiles(workspace.files, workspace.original) : false;
  useEffect(() => {
    const warn = event => { if (dirty) { event.preventDefault(); event.returnValue = ''; } };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  const file = workspace?.files.find(item => item.path === selected) || workspace?.files[0];
  const fileAnalysis = analysis?.files.find(item => item.path === file?.path);
  const declarations = [...(fileAnalysis?.messages || []), ...(fileAnalysis?.enums || [])];
  const declaration = declarations.find(item => item.fullName === declarationName) || declarations[0];
  const allDeclarations = analysis?.files.flatMap(item => [...item.messages, ...item.enums]) || [];
  const originalFile = workspace?.original.find(item => item.path === file?.path);
  const originalDeclaration = workspace?.originalAnalysis?.files.find(item => item.path === file?.path);
  const originalItem = [...(originalDeclaration?.messages || []), ...(originalDeclaration?.enums || [])].find(item => item.fullName === declaration?.fullName);
  const isEnum = declaration && !declaration.fields;
  const rows = declaration ? (isEnum ? declaration.values : declaration.fields) : [];
  const rowChanged = row => {
    if (!changed.has(file.path)) return false;
    const old = (isEnum ? originalItem?.values : originalItem?.fields)?.find(value => isEnum ? value.name === row.name : value.number === row.number);
    return JSON.stringify(old) !== JSON.stringify(row);
  };

  async function validate() {
    if (busy || !workspace.files.length) return;
    setBusy('validate'); setError(null); setValid(null);
    try { const result = await request('analyze', { files: workspace.files }); setAnalysis(result); setValid(result.valid); setNotice(result.valid ? 'All schemas checked with protoc.' : 'Fix the schema errors listed below.'); }
    catch (failure) { setError(failure); }
    finally { setBusy(''); }
  }
  useEffect(() => {
    function shortcut(event) { if ((event.metaKey || event.ctrlKey) && event.key === 'Enter' && !modal) { event.preventDefault(); validate(); } }
    window.addEventListener('keydown', shortcut); return () => window.removeEventListener('keydown', shortcut);
  });

  async function mutate(operation) {
    const oldValid = valid;
    setBusy('mutate'); setError(null); setValid(null);
    try {
      const result = await request('mutate', { files: workspace.files, path: file.path, operation });
      setWorkspace(current => ({ ...current, files: result.files })); setAnalysis(result.analysis); setValid(result.analysis.valid); setModal(null); setNotice('Draft saved and schemas validated.');
      if (operation.kind === 'addMessage' || operation.kind === 'addEnum') {
        const prefix = operation.parent || fileAnalysis?.package;
        setDeclarationName(prefix ? `${prefix}.${operation.name}` : operation.name);
      }
    } catch (failure) { setValid(oldValid); throw failure; }
    finally { setBusy(''); }
  }
  async function remove(row) {
    if (!window.confirm(`Delete ${isEnum ? 'value' : 'field'} “${row.name}”? This changes the protocol contract. You can restore the original workspace.`)) return;
    try { await mutate(isEnum ? { kind: 'deleteEnumValue', target: declaration.fullName, oldName: row.name } : { kind: 'deleteField', target: declaration.fullName, fieldNumber: row.number }); }
    catch (failure) { setError(failure); }
  }
  async function importGithub(input) {
    if (dirty && !window.confirm('Replace your current draft with schemas from GitHub? Export a ZIP first to keep your edits.')) return;
    setBusy('import');
    try { const data = await request('github', input); adopt(data); setModal(null); }
    finally { setBusy(''); }
  }
  async function restore() {
    if (!window.confirm('Restore all original files and discard every edit in this workspace?')) return;
    setBusy('restore'); setError(null); setValid(null);
    try { const result = await request('analyze', { files: workspace.original }); setWorkspace(current => ({ ...current, files: current.original })); setAnalysis(result); setValid(result.valid); setNotice('Original files restored.'); }
    catch (failure) { setError(failure); }
    finally { setBusy(''); }
  }
  function replaceBrokenDraft() {
    if (!window.confirm('Replace the unreadable saved draft with this workspace?')) return;
    try { localStorage.removeItem(storageKey); setPersistenceBlocked(false); setStorageError(''); }
    catch { setStorageError('Browser storage is blocked. Export a ZIP to keep your draft.'); }
  }
  async function exportZip() {
    setBusy('export'); setExportError(null);
    try {
      const blob = await request('export', { files: workspace.files });
      const url = URL.createObjectURL(blob); const link = document.createElement('a');
      link.href = url; link.download = 'proto-studio-schemas.zip'; document.body.append(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 10000);
      setModal(null); setValid(true); setNotice('ZIP downloaded. Your draft is still available for review.');
    } catch (failure) { setExportError(failure); if (failure.diagnostics) setValid(false); }
    finally { setBusy(''); }
  }
  function selectFile(path) { setSelected(path); setDeclarationName(''); setDrawer(false); setView('editor'); }
  function openEditor(path) { if (path) selectFile(path); else setView('editor'); setTab('edit'); }


  return <div className="app-shell">
    <a className="skip-link" href="#workspace-main">Skip to workspace</a>
    <header className="global-header"><div className="brand"><div className="brand-mark"><img src="/proto-mark.svg" alt=""/></div><span>Proto Studio</span></div><span className="header-divider"/><div className="workspace-label">Mini game <span>/</span> <strong>Schema workspace</strong></div><nav className="workspace-nav" aria-label="Workspace"><button aria-pressed={view === 'overview'} onClick={() => setView('overview')}><ChartBar size={17} aria-hidden="true"/>Overview</button><button aria-pressed={view === 'editor'} disabled={!hasSchemas} onClick={() => setView('editor')}><BracketsCurly size={17} aria-hidden="true"/>Schema editor</button></nav><div className="header-right"><button className="icon-button theme-toggle" aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'} onClick={toggleTheme}>{theme === 'dark' ? <Sun size={20}/> : <Moon size={20}/>}</button><span className={`environment ${hasSchemas ? '' : 'unconfigured'}`}><i/>{hasSchemas ? 'GitHub' : 'Not configured'}</span><button className="icon-button" aria-label="Help and getting started" onClick={() => setModal({ kind: 'help' })}><BookOpen size={21}/></button></div></header>
    <div className="source-bar"><button className="icon-button mobile-only" aria-label="Open file browser" onClick={() => setDrawer(true)}><List size={22}/></button><GithubLogo size={20}/><span className="repository" title={workspace.revision.repository}>{workspace.revision.repository || 'No repository configured'}</span><span className="bar-divider"/><span className="revision" title={workspace.revision.sha || workspace.revision.label}>{workspace.revision.sha ? <>Commit <code>{workspace.revision.sha.slice(0, 8)}</code></> : workspace.revision.label}</span><div className="source-actions"><button className="text-button" disabled={!!busy} onClick={() => setModal({ kind: 'import' })}>Source settings <CaretRight size={14}/></button></div></div>
    {view === 'editor' && <section className="tracking-bar" aria-label="Workspace changes">
      <span className="editor-location">Schema editor</span>
      <details className="draft-tracker"><summary><PencilSimple size={15} aria-hidden="true"/><b>{changed.size}</b> changed {changed.size === 1 ? 'file' : 'files'}<CaretRight size={12} aria-hidden="true"/></summary><div className="draft-popover"><h2>Draft changes</h2>{changed.size ? <>{[...changed].map(path => <button key={path} onClick={event => { selectFile(path); setTab('diff'); event.currentTarget.closest('details').open = false; }}><PencilSimple size={14} aria-hidden="true"/><code>{path}</code></button>)}<p>Choose a file to compare it with the original.</p></> : <p>Your draft matches the original. Edited files will appear here.</p>}</div></details>
    </section>}
    <div className={`workspace-body ${view === 'overview' ? 'overview-body' : ''}`}><aside className="sidebar desktop-sidebar"><FileNavigation files={workspace.files} selected={file?.path} onSelect={selectFile} query={query} setQuery={setQuery} onHelp={() => setModal({ kind: 'help' })} changed={changed}/></aside>
      <main className="editor" id="workspace-main" tabIndex={-1}>
        {(error || storageError) && <div className="workspace-alerts"><ErrorNotice error={error}/>{error && <button className="text-button" onClick={() => { setError(null); if (!analysis) validate(); }}>{analysis ? 'Dismiss notification' : 'Validate again'}</button>}{storageError && <div className="warning-notice" role="alert">{storageError}{persistenceBlocked && <div><button className="text-button" onClick={replaceBrokenDraft}>Replace unreadable draft</button></div>}</div>}</div>}
        {view === 'overview' ? <Dashboard workspace={workspace} analysis={analysis} changed={changed} onOpen={openEditor} onConfigure={() => setModal({ kind: 'import' })}/> : <>
        <div className="breadcrumb"><span>schemas</span><CaretRight size={13}/><span>{file?.path}</span>{changed.has(file?.path) && <span className="draft-label">Draft</span>}</div>
        <div className="editor-title"><div><div className="declaration-context"><span>{isEnum ? 'Enum' : 'Message'}</span><code>{fileAnalysis?.package || 'No package'}</code></div><h1>{declaration?.name || file?.path.split('/').at(-1)}{changed.has(file?.path) && <span className="title-dot" title="File changed" aria-hidden="true"/>}</h1><p>{declaration ? `${rows.length} ${isEnum ? 'values' : 'fields'} in this definition` : 'Select or add a definition to get started.'}</p></div><button className="icon-button restore-button" title="Restore original workspace" aria-label="Restore original workspace" onClick={restore} disabled={!dirty || !!busy}><ArrowCounterClockwise size={21}/></button></div>
        <div className="editor-tabs" role="tablist" aria-label="Editor views">{[['edit', 'Edit'], ['diff', 'Compare'], ['source', 'Proto source']].map(([value, label]) => <button id={`tab-${value}`} aria-controls={`panel-${value}`} role="tab" aria-selected={tab === value} tabIndex={tab === value ? 0 : -1} key={value} className={tab === value ? 'active' : ''} onClick={() => setTab(value)} onKeyDown={event => { if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') { const options = ['edit', 'diff', 'source']; const next = options[(options.indexOf(value) + (event.key === 'ArrowRight' ? 1 : 2)) % 3]; setTab(next); document.getElementById(`tab-${next}`).focus(); } }}>{label}{value === 'diff' && changed.size > 0 && <span className="count-badge">{changed.size}</span>}</button>)}</div>
        <div role="tabpanel" id={`panel-${tab}`} aria-labelledby={`tab-${tab}`} className="tab-panel">
          {tab === 'edit' && <>
            <div className="compact-definitions"><label>Selected definition<select value={declaration?.fullName || ''} onChange={event => setDeclarationName(event.target.value)}>{declarations.map(item => <option key={item.fullName} value={item.fullName}>{item.fullName.slice(fileAnalysis?.package ? fileAnalysis.package.length + 1 : 0)} · {item.fields ? 'Message' : 'Enum'}</option>)}</select></label><div className="add-definitions"><button className="button small" disabled={!!busy || !analysis} onClick={() => setModal({ kind: 'addMessage' })}><Plus size={16}/>Message</button><button className="button small" disabled={!!busy || !analysis} onClick={() => setModal({ kind: 'addEnum' })}><Plus size={16}/>Enum</button></div></div>
            {declaration ? <><div className="section-heading"><div><h2>{isEnum ? 'Values' : 'Fields'} <span>{rows.length}</span></h2><p>{isEnum ? 'Names and numbers for the choices in this enum.' : 'Fields define the values exchanged between client and server.'}</p></div><button className="button secondary" disabled={!!busy} onClick={() => setModal({ kind: isEnum ? 'addEnumValue' : 'addField' })}><Plus size={18}/>{isEnum ? 'Add value' : 'Add field'}</button></div>
              {rows.length ? <div className="table-scroll"><table role="table" className={`schema-table ${isEnum ? 'enum-table' : ''}`}><thead role="rowgroup"><tr role="row"><th scope="col">{isEnum ? 'Value name' : 'Field name'}</th>{!isEnum && <><th scope="col">Data type</th><th scope="col">Cardinality</th></>}<th scope="col">Number</th><th scope="col"><span className="sr-only">Actions</span></th></tr></thead><tbody role="rowgroup">{rows.map(row => <tr role="row" key={isEnum ? row.name : row.number} className={rowChanged(row) ? 'changed-row' : ''}><td role="cell" className="cell-name" data-label={isEnum ? 'Value name' : 'Field name'}><div className="field-name"><span className={`field-icon ${isEnum ? 'orange' : row.type === 'map' ? 'map-type' : row.type?.includes('.') ? 'message-type' : ''}`}>{isEnum ? <List size={20}/> : row.type === 'map' ? <BracketsCurly size={20}/> : row.type?.includes('.') ? <Cube size={20}/> : <span>Tt</span>}</span><code>{row.name}</code>{rowChanged(row) && <i className="change-dot" aria-label="Changed"/>}</div></td>{!isEnum && <><td role="cell" className="cell-type" data-label="Data type"><code className="type-name">{typeLabel(row)}</code><span className="type-description">{row.type === 'map' ? 'Key-value pairs' : row.type?.includes('.') ? row.type : ({ string: 'Text', bool: 'True / false', bytes: 'Binary data', double: 'Decimal number', float: 'Decimal number' }[row.type] || 'Integer')}</span></td><td role="cell" className="cell-rule rule-cell" data-label="Cardinality">{row.type === 'map' ? 'Map' : { repeated: 'List', optional: 'Optional', single: 'Single value' }[row.rule]}</td></>}<td role="cell" className="cell-number" data-label="Number"><code className="field-number">{row.number}</code></td><td role="cell" className="cell-actions"><div className="row-actions"><button className="icon-button" aria-label={`Edit ${row.name}`} onClick={() => setModal({ kind: isEnum ? 'updateEnumValue' : 'updateField', item: row })} disabled={!!busy}><PencilSimple size={19}/><span className="row-action-label">Edit</span></button><button className="icon-button delete-button" aria-label={`Delete ${row.name}`} onClick={() => remove(row)} disabled={!!busy}><Trash size={18}/><span className="row-action-label">Delete</span></button></div></td></tr>)}</tbody></table></div> : <div className="empty-state"><Stack size={35}/><h3>{isEnum ? 'This enum has no values' : 'This message has no fields'}</h3><p>{isEnum ? 'Add your first value with number 0 using the button above.' : 'Add your first field using the button above.'}</p></div>}
              <div className="editor-footnote"><CheckCircle size={15}/><span>Comments, imports, and unchanged definitions are preserved.</span></div>
            </> : <div className="empty-state"><Cube size={38}/><h3>{analysis ? 'This file has no definitions' : 'Definitions could not be loaded'}</h3><p>{analysis ? 'Add a message or enum to get started.' : 'Validate the schemas to load their definitions.'}</p>{!analysis && <button className="button" disabled={!!busy} onClick={validate}>Validate again</button>}</div>}
          </>}
          {tab === 'diff' && <CompareDialog original={originalFile?.content || ''} current={file?.content || ''} path={file?.path} onClose={() => setTab('edit')}/>}
          {tab === 'source' && <SourceView content={file?.content || ''} path={file?.path}/>}
        </div>
        {analysis?.diagnostics?.length > 0 && <section className="diagnostics" aria-label="Schema diagnostics"><h2>Compiler diagnostics</h2>{analysis.diagnostics.map((item, i) => <p key={i}><code>{item.file}{item.line ? `:${item.line}` : ''}</code> {item.message}</p>)}</section>}
        </>}
      </main>
      {view === 'editor' && <FileOutline declarations={declarations} selected={declaration?.fullName} onSelect={setDeclarationName} packageName={fileAnalysis?.package} path={file?.path} busy={!!busy || !analysis} changed={changed.has(file?.path)} onAdd={kind => setModal({ kind })}/>}
    </div>
    <footer className="bottom-bar"><div className={`validation-status ${valid === false ? 'invalid' : valid === null ? 'pending' : ''}`}>{!hasSchemas ? <Stack size={25}/> : busy === 'validate' || busy === 'mutate' ? <CircleNotch size={25} className="spin"/> : valid ? <CheckCircle size={26} weight="fill"/> : <WarningCircle size={26}/>}<div><strong>{!hasSchemas ? 'No schemas loaded' : busy === 'validate' || busy === 'mutate' ? 'Validating…' : valid ? 'Schemas valid' : valid === false ? 'Schema errors' : 'Validation needed'}</strong><span>{workspace.files.length} files {valid ? 'validated' : 'in workspace'}</span></div></div><span className="footer-shortcut"><kbd>⌘ / Ctrl ↵</kbd> to validate</span><div className="bottom-actions"><button className="button secondary" aria-label="Validate" onClick={validate} disabled={!hasSchemas || !!busy}><CheckCircle size={19}/><span>Validate</span></button><button className="button primary export-button" onClick={() => { setExportError(null); setModal({ kind: 'export' }); }} disabled={!hasSchemas || !!busy || valid === false}>{busy === 'export' ? <CircleNotch className="spin" size={20}/> : <DownloadSimple size={20}/>}<span>{busy === 'export' ? 'Exporting…' : 'Export ZIP'}</span></button></div></footer>
    {notice && <div className="toast" role="status"><CheckCircle size={17}/><span>{notice}</span><button className="icon-button" aria-label="Dismiss notification" onClick={() => setNotice('')}><X size={15}/></button></div>}
    {drawer && <FileDrawer onClose={() => setDrawer(false)}><FileNavigation files={workspace.files} selected={file?.path} onSelect={selectFile} query={query} setQuery={setQuery} onHelp={() => { setDrawer(false); setModal({ kind: 'help' }); }} close={() => setDrawer(false)} changed={changed}/></FileDrawer>}
    {modal?.kind === 'export' && <ExportDialog files={workspace.files} changed={changed} onClose={() => setModal(null)} onExport={exportZip} busy={busy === 'export'} error={exportError}/> }
    {modal?.kind === 'help' && <HelpDialog onClose={() => setModal(null)}/>}
    {modal?.kind === 'import' && <ImportDialog initialRepository={workspace.revision.repository} initialCommit={workspace.revision.sha || ''} onClose={() => setModal(null)} onImport={importGithub} busy={busy === 'import'}/>}
    {modal && !['help', 'import', 'export'].includes(modal.kind) && <EditDialog mode={modal.kind} item={modal.item} target={declaration} declarations={['addMessage', 'addEnum'].includes(modal.kind) ? declarations : allDeclarations} onClose={() => setModal(null)} onSave={mutate} busy={busy === 'mutate'}/>}
  </div>;
}

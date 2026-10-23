import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Plus, PencilSimple, Trash, GithubLogo, DownloadSimple, CheckCircle, WarningCircle, List, BookOpen, ArrowCounterClockwise, CircleNotch, CaretRight, X, Cube, Stack, BracketsCurly,  } from '@phosphor-icons/react';
import { request, readSaved, sameFiles, storageKey } from './api.js';
import { EditDialog, ErrorNotice, HelpDialog, ImportDialog, ExportDialog } from './Dialogs.jsx';
import { DiffView, SourceView } from './SourceViews.jsx';
import FileNavigation from './FileNavigation.jsx';
import FileOutline from './FileOutline.jsx';

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
  return <dialog ref={ref} className="file-drawer" aria-label="Danh sách file schema" onCancel={event => { event.preventDefault(); onClose(); }} onClick={event => { if (event.target === ref.current) onClose(); }}><aside className="sidebar mobile-sidebar">{children}</aside></dialog>;
}

export default function App() {
  const [workspace, setWorkspace] = useState(null);
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

  const adopt = useCallback(data => {
    const shopAnalysis = data.revision.source === 'local' ? data.analysis.files.find(file => file.messages.some(message => message.name === 'ShopPackage' || message.name === 'ShopPackageProto')) : null;
    const defaultFile = data.files.find(file => file.path === shopAnalysis?.path) || data.files[0];
    const defaultDeclaration = shopAnalysis?.messages.find(message => message.name === 'ShopPackage' || message.name === 'ShopPackageProto');
    setWorkspace({ files: data.files, original: data.files, revision: data.revision, originalAnalysis: data.analysis });
    setAnalysis(data.analysis); setValid(data.analysis.valid); setSelected(defaultFile?.path || ''); setDeclarationName(defaultDeclaration?.fullName || ''); setTab('edit'); setError(null); setNotice('');
  }, []);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      let saved;
      try { saved = readSaved(); } catch { setPersistenceBlocked(true); setStorageError('Không đọc được bản nháp trong trình duyệt. Workspace cục bộ sẽ được mở; bản nháp cũ chưa bị xóa.'); }
      if (saved) {
        setWorkspace(saved); setSelected(saved.selected || saved.files[0]?.path || ''); setDeclarationName(saved.declarationName || '');
        setNotice('Đã khôi phục workspace từ trình duyệt.');
        const result = await request('analyze', { files: saved.files }); setAnalysis(result); setValid(result.valid);
      } else adopt(await request('workspace'));
    } catch (failure) { setError(failure); }
    finally { setLoading(false); }
  }, [adopt]);
  useEffect(() => { load(); }, [load]);

  useEffect(() => {
    if (!workspace || loading || persistenceBlocked) return;
    try { localStorage.setItem(storageKey, JSON.stringify({ ...workspace, selected, declarationName })); }
    catch { setStorageError('Không lưu được bản nháp trong trình duyệt (bộ nhớ đầy hoặc bị chặn). Hãy tải ZIP trước khi đóng trang.'); }
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
    if (busy || !workspace) return;
    setBusy('validate'); setError(null); setValid(null);
    try { const result = await request('analyze', { files: workspace.files }); setAnalysis(result); setValid(result.valid); setNotice(result.valid ? 'Đã kiểm tra toàn bộ schema bằng protoc.' : 'Schema cần được sửa theo chẩn đoán bên dưới.'); }
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
      setWorkspace(current => ({ ...current, files: result.files })); setAnalysis(result.analysis); setValid(result.analysis.valid); setModal(null); setNotice('Đã lưu bản nháp và kiểm tra schema.');
      if (operation.kind === 'addMessage' || operation.kind === 'addEnum') {
        const prefix = operation.parent || fileAnalysis?.package;
        setDeclarationName(prefix ? `${prefix}.${operation.name}` : operation.name);
      }
    } catch (failure) { setValid(oldValid); throw failure; }
    finally { setBusy(''); }
  }
  async function remove(row) {
    if (!window.confirm(`Xóa ${isEnum ? 'giá trị' : 'thuộc tính'} “${row.name}”? Thao tác này thay đổi hợp đồng dữ liệu. Bạn có thể khôi phục toàn bộ bản gốc.`)) return;
    try { await mutate(isEnum ? { kind: 'deleteEnumValue', target: declaration.fullName, oldName: row.name } : { kind: 'deleteField', target: declaration.fullName, fieldNumber: row.number }); }
    catch (failure) { setError(failure); }
  }
  async function importGithub(input) {
    if (dirty && !window.confirm('Workspace có thay đổi chưa được đưa vào repository. Thay bản nháp hiện tại bằng schema từ GitHub? Hãy tải ZIP trước nếu cần giữ lại.')) return;
    setBusy('import');
    try { const data = await request('github', input); adopt(data); setModal(null); }
    finally { setBusy(''); }
  }
  async function localDemo() {
    if (dirty && !window.confirm('Mở lại local demo sẽ thay bản nháp hiện tại. Tiếp tục?')) return;
    setBusy('import'); setError(null);
    try { adopt(await request('workspace')); }
    catch (failure) { setError(failure); }
    finally { setBusy(''); }
  }
  async function restore() {
    if (!window.confirm('Khôi phục toàn bộ file về bản gốc và bỏ tất cả thay đổi trong workspace này?')) return;
    setBusy('restore'); setError(null); setValid(null);
    try { const result = await request('analyze', { files: workspace.original }); setWorkspace(current => ({ ...current, files: current.original })); setAnalysis(result); setValid(result.valid); setNotice('Đã khôi phục bản gốc.'); }
    catch (failure) { setError(failure); }
    finally { setBusy(''); }
  }
  function replaceBrokenDraft() {
    if (!window.confirm('Xóa bản nháp không đọc được và lưu workspace hiện tại thay thế?')) return;
    try { localStorage.removeItem(storageKey); setPersistenceBlocked(false); setStorageError(''); }
    catch { setStorageError('Trình duyệt đang chặn lưu trữ. Hãy tải ZIP để giữ bản nháp hiện tại.'); }
  }
  async function exportZip() {
    setBusy('export'); setExportError(null);
    try {
      const blob = await request('export', { files: workspace.files });
      const url = URL.createObjectURL(blob); const link = document.createElement('a');
      link.href = url; link.download = 'proto-studio-schemas.zip'; document.body.append(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 10000);
      setModal(null); setValid(true); setNotice('Đã tải ZIP. Bản nháp vẫn được giữ để bạn tiếp tục đối chiếu.');
    } catch (failure) { setExportError(failure); if (failure.diagnostics) setValid(false); }
    finally { setBusy(''); }
  }
  function selectFile(path) { setSelected(path); setDeclarationName(''); setDrawer(false); }

  if (loading && !workspace) return <main className="loading-screen"><div className="brand-mark"><img src="/proto-mark.svg" alt=""/></div><h1>Proto Studio</h1><CircleNotch className="spin" size={28}/><p>Đang mở schema workspace…</p></main>;
  if (!workspace) return <main className="loading-screen"><WarningCircle size={42}/><h1>Chưa thể mở workspace</h1><ErrorNotice error={error}/><button className="button primary" onClick={load}>Thử tải lại</button></main>;

  return <div className="app-shell">
    <header className="global-header"><div className="brand"><div className="brand-mark"><img src="/proto-mark.svg" alt=""/></div><span>Proto Studio</span></div><span className="header-divider"/><div className="workspace-label">Mini game <span>/</span> <strong>Schema workspace</strong></div><div className="header-right"><span className="environment"><i/>{workspace.revision.source === 'github' ? 'GitHub' : 'Local demo'}</span><button className="icon-button" aria-label="Hướng dẫn sử dụng" onClick={() => setModal({ kind: 'help' })}><BookOpen size={21}/></button></div></header>
    <div className="source-bar"><button className="icon-button mobile-only" aria-label="Mở danh sách file" onClick={() => setDrawer(true)}><List size={22}/></button><GithubLogo size={20}/><span className="repository" title={workspace.revision.repository}>{workspace.revision.repository}</span><span className="bar-divider"/><span className="revision" title={workspace.revision.sha || workspace.revision.label}>{workspace.revision.sha ? <>Commit <code>{workspace.revision.sha.slice(0, 8)}</code></> : workspace.revision.label}</span><div className="source-actions"><button className="text-button" disabled={!!busy} onClick={() => setModal({ kind: 'import' })}>Mở GitHub <CaretRight size={14}/></button>{workspace.revision.source === 'github' && <button className="text-button" disabled={!!busy} onClick={localDemo}>Local demo</button>}</div></div>
    <div className="workspace-body"><aside className="sidebar desktop-sidebar"><FileNavigation files={workspace.files} selected={file?.path} onSelect={selectFile} query={query} setQuery={setQuery} onHelp={() => setModal({ kind: 'help' })} changed={changed}/></aside>
      <main className="editor" id="workspace-main">
        {(error || storageError) && <div className="workspace-alerts"><ErrorNotice error={error}/>{error && <button className="text-button" onClick={() => { setError(null); if (!analysis) validate(); }}>{analysis ? 'Đóng thông báo' : 'Thử kiểm tra lại'}</button>}{storageError && <div className="warning-notice" role="alert">{storageError}{persistenceBlocked && <div><button className="text-button" onClick={replaceBrokenDraft}>Thay bản nháp không đọc được</button></div>}</div>}</div>}
        <div className="breadcrumb"><span>schemas</span><CaretRight size={13}/><span>{file?.path}</span>{changed.has(file?.path) && <span className="draft-label">Bản nháp</span>}</div>
        <div className="editor-title"><div><div className="declaration-context"><span>{isEnum ? 'Enum' : 'Message'}</span><code>{fileAnalysis?.package || 'Không có package'}</code></div><h1>{declaration?.name || file?.path.split('/').at(-1)}{changed.has(file?.path) && <span className="title-dot" title="File đã thay đổi" aria-hidden="true"/>}</h1><p>{declaration ? `${rows.length} ${isEnum ? 'giá trị' : 'thuộc tính'} trong định nghĩa này` : 'Chọn hoặc thêm một định nghĩa để bắt đầu.'}</p></div><button className="icon-button restore-button" title="Khôi phục toàn bộ bản gốc" aria-label="Khôi phục toàn bộ bản gốc" onClick={restore} disabled={!dirty || !!busy}><ArrowCounterClockwise size={21}/></button></div>
        <div className="editor-tabs" role="tablist" aria-label="Chế độ xem">{[['edit', 'Chỉnh sửa'], ['diff', 'Đối chiếu'], ['source', 'Mã proto']].map(([value, label]) => <button id={`tab-${value}`} aria-controls={`panel-${value}`} role="tab" aria-selected={tab === value} tabIndex={tab === value ? 0 : -1} key={value} className={tab === value ? 'active' : ''} onClick={() => setTab(value)} onKeyDown={event => { if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') { const options = ['edit', 'diff', 'source']; const next = options[(options.indexOf(value) + (event.key === 'ArrowRight' ? 1 : 2)) % 3]; setTab(next); document.getElementById(`tab-${next}`).focus(); } }}>{label}{value === 'diff' && changed.size > 0 && <span className="count-badge">{changed.size}</span>}</button>)}</div>
        <div role="tabpanel" id={`panel-${tab}`} aria-labelledby={`tab-${tab}`} className="tab-panel">
          {tab === 'edit' && <>
            <div className="compact-definitions"><label>Định nghĩa đang chọn<select value={declaration?.fullName || ''} onChange={event => setDeclarationName(event.target.value)}>{declarations.map(item => <option key={item.fullName} value={item.fullName}>{item.fullName.slice(fileAnalysis?.package ? fileAnalysis.package.length + 1 : 0)} · {item.fields ? 'Message' : 'Enum'}</option>)}</select></label><div className="add-definitions"><button className="button small" disabled={!!busy || !analysis} onClick={() => setModal({ kind: 'addMessage' })}><Plus size={16}/>Message</button><button className="button small" disabled={!!busy || !analysis} onClick={() => setModal({ kind: 'addEnum' })}><Plus size={16}/>Enum</button></div></div>
            {declaration ? <><div className="section-heading"><div><h2>{isEnum ? 'Các giá trị' : 'Các thuộc tính'} <span>{rows.length}</span></h2><p>{isEnum ? 'Tên và mã số của mỗi giá trị.' : 'Định nghĩa cách client và server trao đổi dữ liệu.'}</p></div><button className="button secondary" disabled={!!busy} onClick={() => setModal({ kind: isEnum ? 'addEnumValue' : 'addField' })}><Plus size={18}/>{isEnum ? 'Thêm giá trị' : 'Thêm thuộc tính'}</button></div>
              {rows.length ? <div className="table-scroll"><table role="table" className={`schema-table ${isEnum ? 'enum-table' : ''}`}><thead role="rowgroup"><tr role="row"><th scope="col">{isEnum ? 'Tên giá trị' : 'Tên thuộc tính'}</th>{!isEnum && <><th scope="col">Kiểu dữ liệu</th><th scope="col">Dạng</th></>}<th scope="col">Mã số</th><th scope="col"><span className="sr-only">Thao tác</span></th></tr></thead><tbody role="rowgroup">{rows.map(row => <tr role="row" key={isEnum ? row.name : row.number} className={rowChanged(row) ? 'changed-row' : ''}><td role="cell" className="cell-name" data-label={isEnum ? 'Tên giá trị' : 'Tên thuộc tính'}><div className="field-name"><span className={`field-icon ${isEnum ? 'orange' : row.type === 'map' ? 'map-type' : row.type?.includes('.') ? 'message-type' : ''}`}>{isEnum ? <List size={20}/> : row.type === 'map' ? <BracketsCurly size={20}/> : row.type?.includes('.') ? <Cube size={20}/> : <span>Tt</span>}</span><code>{row.name}</code>{rowChanged(row) && <i className="change-dot" aria-label="Đã thay đổi"/>}</div></td>{!isEnum && <><td role="cell" className="cell-type" data-label="Kiểu dữ liệu"><code className="type-name">{typeLabel(row)}</code><span className="type-description">{row.type === 'map' ? 'Bảng ánh xạ' : row.type?.includes('.') ? row.type : ({ string: 'Văn bản', bool: 'Đúng / sai', bytes: 'Dữ liệu nhị phân', double: 'Số thập phân', float: 'Số thập phân' }[row.type] || 'Số nguyên')}</span></td><td role="cell" className="cell-rule rule-cell" data-label="Dạng">{row.type === 'map' ? 'Map' : { repeated: 'Danh sách', optional: 'Tùy chọn', single: 'Một giá trị' }[row.rule]}</td></>}<td role="cell" className="cell-number" data-label="Mã số"><code className="field-number">{row.number}</code></td><td role="cell" className="cell-actions"><div className="row-actions"><button className="icon-button" aria-label={`Chỉnh sửa ${row.name}`} onClick={() => setModal({ kind: isEnum ? 'updateEnumValue' : 'updateField', item: row })} disabled={!!busy}><PencilSimple size={19}/><span className="row-action-label">Chỉnh sửa</span></button><button className="icon-button delete-button" aria-label={`Xóa ${row.name}`} onClick={() => remove(row)} disabled={!!busy}><Trash size={18}/><span className="row-action-label">Xóa</span></button></div></td></tr>)}</tbody></table></div> : <div className="empty-state"><Stack size={35}/><h3>{isEnum ? 'Enum chưa có giá trị' : 'Message chưa có thuộc tính'}</h3><p>Thêm {isEnum ? 'giá trị đầu tiên với mã số 0' : 'thuộc tính đầu tiên'} bằng nút phía trên.</p></div>}
              <div className="editor-footnote"><CheckCircle size={15}/><span>Giữ nguyên comments, imports và các định nghĩa không thay đổi.</span></div>
            </> : <div className="empty-state"><Cube size={38}/><h3>{analysis ? 'Chưa có định nghĩa trong file' : 'Chưa đọc được định nghĩa'}</h3><p>{analysis ? 'Thêm message hoặc enum mới để bắt đầu.' : 'Kiểm tra lại schema để tải danh sách định nghĩa.'}</p>{!analysis && <button className="button" disabled={!!busy} onClick={validate}>Kiểm tra lại</button>}</div>}
          </>}
          {tab === 'diff' && <DiffView original={originalFile?.content || ''} current={file?.content || ''}/>}
          {tab === 'source' && <SourceView content={file?.content || ''} path={file?.path}/>}
        </div>
        {analysis?.diagnostics?.length > 0 && <section className="diagnostics" aria-label="Chẩn đoán schema"><h2>Chẩn đoán từ protoc</h2>{analysis.diagnostics.map((item, i) => <p key={i}><code>{item.file}{item.line ? `:${item.line}` : ''}</code> {item.message}</p>)}</section>}
      </main>
      <FileOutline declarations={declarations} selected={declaration?.fullName} onSelect={setDeclarationName} packageName={fileAnalysis?.package} path={file?.path} busy={!!busy || !analysis} changed={changed.has(file?.path)} onAdd={kind => setModal({ kind })}/>
    </div>
    <footer className="bottom-bar"><div className={`validation-status ${valid === false ? 'invalid' : valid === null ? 'pending' : ''}`}>{busy === 'validate' || busy === 'mutate' ? <CircleNotch size={25} className="spin"/> : valid ? <CheckCircle size={26} weight="fill"/> : <WarningCircle size={26}/>}<div><strong>{busy === 'validate' || busy === 'mutate' ? 'Đang kiểm tra…' : valid ? 'Schema hợp lệ' : valid === false ? 'Schema có lỗi' : 'Cần kiểm tra schema'}</strong><span>{workspace.files.length} file {valid ? 'đã kiểm tra' : 'trong workspace'}</span></div></div><span className="footer-shortcut"><kbd>⌘ / Ctrl ↵</kbd> để kiểm tra nhanh</span><div className="bottom-actions"><button className="button secondary" aria-label="Kiểm tra" onClick={validate} disabled={!!busy}><CheckCircle size={19}/><span>Kiểm tra</span></button><button className="button primary export-button" onClick={() => { setExportError(null); setModal({ kind: 'export' }); }} disabled={!!busy || valid === false}>{busy === 'export' ? <CircleNotch className="spin" size={20}/> : <DownloadSimple size={20}/>}<span>{busy === 'export' ? 'Đang xuất…' : 'Xuất ZIP'}</span></button></div></footer>
    {notice && <div className="toast" role="status"><CheckCircle size={17}/><span>{notice}</span><button className="icon-button" aria-label="Đóng thông báo" onClick={() => setNotice('')}><X size={15}/></button></div>}
    {drawer && <FileDrawer onClose={() => setDrawer(false)}><FileNavigation files={workspace.files} selected={file?.path} onSelect={selectFile} query={query} setQuery={setQuery} onHelp={() => { setDrawer(false); setModal({ kind: 'help' }); }} close={() => setDrawer(false)} changed={changed}/></FileDrawer>}
    {modal?.kind === 'export' && <ExportDialog files={workspace.files} changed={changed} onClose={() => setModal(null)} onExport={exportZip} busy={busy === 'export'} error={exportError}/> }
    {modal?.kind === 'help' && <HelpDialog onClose={() => setModal(null)}/>}
    {modal?.kind === 'import' && <ImportDialog onClose={() => setModal(null)} onImport={importGithub} busy={busy === 'import'}/>}
    {modal && !['help', 'import', 'export'].includes(modal.kind) && <EditDialog mode={modal.kind} item={modal.item} target={declaration} declarations={['addMessage', 'addEnum'].includes(modal.kind) ? declarations : allDeclarations} onClose={() => setModal(null)} onSave={mutate} busy={busy === 'mutate'}/>}
  </div>;
}

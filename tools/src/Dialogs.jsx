import { useEffect, useId, useRef, useState } from 'react';
import { X, GithubLogo, ArrowRight, CircleNotch, DownloadSimple, FileText } from '@phosphor-icons/react';

export function Modal({ title, children, onClose, busy = false, wide = false }) {
  const ref = useRef(null);
  const titleId = useId();
  useEffect(() => {
    const dialog = ref.current;
    const opener = document.activeElement;
    dialog.showModal();
    dialog.querySelector('input, select, textarea')?.focus();
    return () => {
      dialog.close();
      if (opener instanceof HTMLElement && opener.isConnected) opener.focus();
    };
  }, []);
  return <dialog ref={ref} aria-labelledby={titleId} className={wide ? 'modal modal-wide' : 'modal'} onCancel={event => { event.preventDefault(); if (!busy) onClose(); }} onClick={event => { if (event.target === ref.current && !busy) onClose(); }}>
    <div className="modal-heading"><h2 id={titleId}>{title}</h2><button type="button" className="icon-button" aria-label="Close dialog" disabled={busy} onClick={onClose}><X size={20}/></button></div>
    {children}
  </dialog>;
}

export function ErrorNotice({ error }) {
  if (!error) return null;
  return <div className="error-notice" role="alert"><strong>{error.message || error}</strong>{error.diagnostics?.length > 0 && <ul>{error.diagnostics.map((item, i) => <li key={i}>{item.file}{item.line ? `:${item.line}` : ''}: {item.message}</li>)}</ul>}</div>;
}

export function ExportDialog({ files, changed, onClose, onExport, busy, error }) {
  return <Modal title="Export workspace" onClose={onClose} busy={busy}>
    <p className="dialog-caption">Download all current schemas as a ZIP. Your original and draft stay in this workspace.</p>
    <dl className="export-summary"><div><dt>Entire workspace</dt><dd>{files.length} .proto files</dd></div><div><dt>Edited</dt><dd>{changed.size} changed {changed.size === 1 ? 'file' : 'files'}</dd></div></dl>
    <div className="info-note">Folder structure and relative paths are preserved. Also includes {files.length - changed.size} unchanged files.</div>
    <details className="export-details"><summary>View {files.length} files to export</summary><ul className="export-file-list">{files.map(file => <li key={file.path}><FileText size={15}/><code>{file.path}</code>{changed.has(file.path) && <span>Edited</span>}</li>)}</ul></details>
    <ErrorNotice error={error}/>
    <p className="export-validation">Schemas are checked with protoc again before downloading.</p>
    <div className="modal-actions"><button className="button" disabled={busy} onClick={onClose}>Cancel</button><button className="button primary" disabled={busy} onClick={onExport}>{busy ? <CircleNotch className="spin" size={18}/> : <DownloadSimple size={18}/>} {busy ? 'Validating and exporting…' : 'Download ZIP'}</button></div>
  </Modal>;
}

export function ImportForm({ onClose, onImport, busy, initialRepository = '', initialCommit = '' }) {
  const [error, setError] = useState(null);
  async function submit(event) {
    event.preventDefault(); setError(null);
    const form = new FormData(event.currentTarget);
    try { await onImport({ repository: form.get('repository').trim(), commit: form.get('commit').trim() }); } catch (failure) { setError(failure); }
  }
  return <form onSubmit={submit}>
    <div className="dialog-intro"><GithubLogo size={28}/><p>Open .proto files from a specific commit. Your edits stay local and are not written to GitHub.</p></div>
    <label>Repository<input name="repository" spellCheck={false} autoComplete="off" defaultValue={initialRepository} required pattern="[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+" placeholder="owner/repo" autoFocus/></label>
    <label>Full commit SHA<input name="commit" spellCheck={false} autoComplete="off" className="mono" defaultValue={initialCommit} required pattern="[a-fA-F0-9]{40}" maxLength={40} placeholder="40-character commit SHA"/><span className="input-hint">Use the full 40-character commit SHA, rather than a branch name. The repository must contain .proto files.</span></label>
    <ErrorNotice error={error}/><div className="modal-actions">{onClose && <button type="button" className="button" onClick={onClose} disabled={busy}>Cancel</button>}<button className="button primary" disabled={busy}>{busy ? <CircleNotch className="spin"/> : <ArrowRight/>}{busy ? 'Loading schemas…' : 'Save & load'}</button></div>
  </form>;
}

export function ImportDialog(props) {
  return <Modal title="Source settings" onClose={props.onClose} busy={props.busy}><ImportForm {...props}/></Modal>;
}

const scalarTypes = ['string', 'bool', 'int32', 'int64', 'uint32', 'uint64', 'sint32', 'sint64', 'fixed32', 'fixed64', 'sfixed32', 'sfixed64', 'double', 'float', 'bytes'];
const mapKeys = ['string', 'bool', 'int32', 'int64', 'uint32', 'uint64', 'sint32', 'sint64', 'fixed32', 'fixed64', 'sfixed32', 'sfixed64'];

export function EditDialog({ mode, item, target, declarations, onClose, onSave, busy }) {
  const fieldMode = mode === 'addField' || mode === 'updateField';
  const enumMode = mode === 'addEnumValue' || mode === 'updateEnumValue';
  const [type, setType] = useState(item?.type || 'string');
  const [error, setError] = useState(null);
  const formRef = useRef(null);
  const initialForm = useRef(null);
  useEffect(() => { initialForm.current = JSON.stringify([...new FormData(formRef.current)]); }, []);
  const titles = { addField: 'Add field', updateField: 'Edit field', addMessage: 'Add message', addEnum: 'Add enum', addEnumValue: 'Add enum value', updateEnumValue: 'Edit enum value' };
  const userTypes = declarations.map(declaration => declaration.fullName).sort();
  const types = [...new Set([...scalarTypes, ...userTypes, ...(item?.type && item.type !== 'map' ? [item.type] : [])])];
  function describeType(value) {
    if (scalarTypes.includes(value)) {
      const label = { string: 'Text', bool: 'True / false', bytes: 'Binary data', double: 'Decimal number', float: 'Decimal number' }[value] || 'Integer';
      return `${label} (${value})`;
    }
    const declaration = declarations.find(entry => entry.fullName === value);
    return `${declaration ? declaration.fields ? 'Message' : 'Enum' : 'Data type'}: ${value}`;
  }
  function close() {
    if (JSON.stringify([...new FormData(formRef.current)]) !== initialForm.current && !window.confirm('Discard the unsaved changes in this form?')) return;
    onClose();
  }
  async function submit(event) {
    event.preventDefault(); setError(null);
    const form = new FormData(event.currentTarget);
    let operation = { kind: mode };
    if (fieldMode) {
      const field = { name: form.get('name').trim(), type, rule: type === 'map' ? 'single' : form.get('rule') };
      if (item) field.number = item.number;
      if (type === 'map') { field.keyType = form.get('keyType'); field.valueType = form.get('valueType'); }
      operation = { ...operation, target: target.fullName, field, ...(item ? { fieldNumber: item.number } : {}) };
    } else if (enumMode) {
      operation = { ...operation, target: target.fullName, value: { name: form.get('name').trim(), number: Number(form.get('number')) }, ...(item ? { oldName: item.name } : {}) };
    } else {
      operation.name = form.get('name').trim();
      if (form.get('parent')) operation.parent = form.get('parent');
    }
    try { await onSave(operation); } catch (failure) { setError(failure); }
  }
  return <Modal title={titles[mode]} onClose={close} busy={busy}><form ref={formRef} onSubmit={submit}>
    <p className="dialog-caption">{fieldMode || enumMode ? <>Editing <code>{target.fullName}</code></> : 'Create a new definition in the selected file.'}</p>
    <label>{fieldMode ? 'Field name' : enumMode ? 'Value name' : mode === 'addEnum' ? 'Enum name' : 'Message name'}<input name="name" spellCheck={false} autoComplete="off" defaultValue={item?.name || ''} required pattern="[A-Za-z_][A-Za-z0-9_]*" placeholder={fieldMode ? 'e.g. display_name' : enumMode ? 'e.g. STATUS_UNKNOWN' : 'e.g. PlayerProfile'} autoFocus/><span className="input-hint">Use letters, numbers, and underscores. Start with a letter or underscore.</span></label>
    {fieldMode && <><label>Data type<select name="type" value={type} onChange={event => setType(event.target.value)}>{types.map(value => <option key={value} value={value}>{describeType(value)}</option>)}<option value="map">Key-value pairs (map)</option></select></label>
      {type === 'map' ? <div className="form-grid"><label>Key type<select name="keyType" defaultValue={item?.keyType || 'string'}>{mapKeys.map(value => <option key={value} value={value}>{describeType(value)}</option>)}</select></label><label>Value type<select name="valueType" defaultValue={item?.valueType || 'string'}>{types.map(value => <option key={value} value={value}>{describeType(value)}</option>)}</select></label></div> : <label>Field shape<select name="rule" defaultValue={item?.rule || 'single'}><option value="single">Single value</option><option value="optional">Optional</option><option value="repeated">List (repeated)</option></select><span className="input-hint">Single stores one value. Optional tracks whether a value was set. List stores multiple values.</span></label>}
      <div className="info-note">{item ? <>Number <code>{item.number}</code> stays unchanged because it identifies this field in the protocol.</> : 'A field number is assigned automatically, skipping numbers that are used or reserved.'}</div>
    </>}
    {enumMode && <label>Number<input name="number" type="number" required min="-2147483648" max="2147483647" step="1" defaultValue={item?.number ?? (target.values.length ? Math.max(...target.values.map(value => value.number)) + 1 : 0)}/><span className="input-hint">The first value of a proto3 enum must have number 0.</span></label>}
    {!fieldMode && !enumMode && <label>Location<select name="parent" defaultValue=""><option value="">Top level in this file</option>{declarations.filter(value => value.fields).map(value => <option key={value.fullName} value={value.fullName}>Inside {value.fullName}</option>)}</select></label>}
    <ErrorNotice error={error}/><div className="modal-actions"><button type="button" className="button" onClick={close} disabled={busy}>Cancel</button><button className="button primary" disabled={busy}>{busy && <CircleNotch className="spin"/>}{busy ? 'Validating…' : 'Save changes'}</button></div>
  </form></Modal>;
}

export function HelpDialog({ onClose }) {
  return <Modal title="Getting started with Proto Studio" onClose={onClose}><div className="help-content"><div className="eyebrow">SCHEMA WORKSPACE</div><p>A schema defines the data exchanged between client and server. A message groups fields, a field stores one value, and an enum defines named choices. Editing a schema does not change live player data or shop prices.</p><ol><li>Choose a file, then select a message or enum in the outline.</li><li>Add or edit fields with the form. Each saved edit is checked by protoc.</li><li>Open <strong>Compare</strong> to review original and draft side by side. Red shows removed text; green shows added text.</li><li>Validate, then export a ZIP. Your draft is saved in this browser.</li></ol><div className="info-note">After exporting, review protocol compatibility, copy the files into your repository, and regenerate client/server code. Exporting does not commit or deploy changes.</div><p className="muted">Source settings require a public schema repository and a full commit SHA. Schemas appear only after a successful import.</p></div><div className="modal-actions"><button className="button primary" onClick={onClose}>Got it</button></div></Modal>;
}

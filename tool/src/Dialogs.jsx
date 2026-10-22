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
    <div className="modal-heading"><h2 id={titleId}>{title}</h2><button type="button" className="icon-button" aria-label="Đóng hộp thoại" disabled={busy} onClick={onClose}><X size={20}/></button></div>
    {children}
  </dialog>;
}

export function ErrorNotice({ error }) {
  if (!error) return null;
  return <div className="error-notice" role="alert"><strong>{error.message || error}</strong>{error.diagnostics?.length > 0 && <ul>{error.diagnostics.map((item, i) => <li key={i}>{item.file}{item.line ? `:${item.line}` : ''}: {item.message}</li>)}</ul>}</div>;
}

export function ExportDialog({ files, changed, onClose, onExport, busy, error }) {
  return <Modal title="Xuất workspace" onClose={onClose} busy={busy}>
    <p className="dialog-caption">Tải toàn bộ schema hiện tại trong một file ZIP. Bản nháp và bản gốc vẫn được giữ trong workspace.</p>
    <dl className="export-summary"><div><dt>Toàn bộ workspace</dt><dd>{files.length} file .proto</dd></div><div><dt>Đã chỉnh sửa</dt><dd>{changed.size} file có thay đổi</dd></div></dl>
    <div className="info-note">Giữ nguyên cấu trúc thư mục và đường dẫn tương đối. Bao gồm cả {files.length - changed.size} file không thay đổi.</div>
    <details className="export-details"><summary>Xem {files.length} file sẽ xuất</summary><ul className="export-file-list">{files.map(file => <li key={file.path}><FileText size={15}/><code>{file.path}</code>{changed.has(file.path) && <span>Đã sửa</span>}</li>)}</ul></details>
    <ErrorNotice error={error}/>
    <p className="export-validation">Schema được kiểm tra lại bằng protoc trước khi tải ZIP.</p>
    <div className="modal-actions"><button className="button" disabled={busy} onClick={onClose}>Hủy</button><button className="button primary" disabled={busy} onClick={onExport}>{busy ? <CircleNotch className="spin" size={18}/> : <DownloadSimple size={18}/>} {busy ? 'Đang kiểm tra và xuất…' : 'Tải ZIP'}</button></div>
  </Modal>;
}

export function ImportDialog({ onClose, onImport, busy }) {
  const [error, setError] = useState(null);
  async function submit(event) {
    event.preventDefault(); setError(null);
    const form = new FormData(event.currentTarget);
    try { await onImport({ repository: form.get('repository').trim(), commit: form.get('commit').trim() }); } catch (failure) { setError(failure); }
  }
  return <Modal title="Mở schema từ GitHub" onClose={onClose} busy={busy}><form onSubmit={submit}>
    <div className="dialog-intro"><GithubLogo size={28}/><p>Đọc các file .proto tại một commit cụ thể. Proto Studio không ghi thay đổi lên GitHub.</p></div>
    <label>Repository<input name="repository" defaultValue="mudotet/proto_mini-intern-server-project" required pattern="[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+" placeholder="owner/repo" autoFocus/></label>
    <label>Commit SHA đầy đủ<input name="commit" className="mono" required pattern="[a-fA-F0-9]{40}" maxLength={40} placeholder="40 ký tự SHA của commit"/><span className="input-hint">Dùng SHA 40 ký tự, không dùng tên nhánh. Repository cần có file .proto.</span></label>
    <ErrorNotice error={error}/><div className="modal-actions"><button type="button" className="button" onClick={onClose} disabled={busy}>Hủy</button><button className="button primary" disabled={busy}>{busy ? <CircleNotch className="spin"/> : <ArrowRight/>}{busy ? 'Đang tải schema…' : 'Mở repository'}</button></div>
  </form></Modal>;
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
  const titles = { addField: 'Thêm thuộc tính', updateField: 'Chỉnh sửa thuộc tính', addMessage: 'Thêm message', addEnum: 'Thêm enum', addEnumValue: 'Thêm giá trị enum', updateEnumValue: 'Chỉnh sửa giá trị enum' };
  const userTypes = declarations.map(declaration => declaration.fullName).sort();
  const types = [...new Set([...scalarTypes, ...userTypes, ...(item?.type && item.type !== 'map' ? [item.type] : [])])];
  function describeType(value) {
    if (scalarTypes.includes(value)) {
      const label = { string: 'Văn bản', bool: 'Đúng / sai', bytes: 'Dữ liệu nhị phân', double: 'Số thập phân', float: 'Số thập phân' }[value] || 'Số nguyên';
      return `${label} (${value})`;
    }
    const declaration = declarations.find(entry => entry.fullName === value);
    return `${declaration ? declaration.fields ? 'Cấu trúc' : 'Enum' : 'Kiểu dữ liệu'}: ${value}`;
  }
  function close() {
    if (JSON.stringify([...new FormData(formRef.current)]) !== initialForm.current && !window.confirm('Bỏ các thay đổi chưa lưu trong biểu mẫu này?')) return;
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
    <p className="dialog-caption">{fieldMode || enumMode ? <>Trong <code>{target.fullName}</code></> : 'Tạo một định nghĩa mới trong file đang chọn.'}</p>
    <label>Tên {fieldMode ? 'thuộc tính' : enumMode ? 'giá trị' : mode === 'addEnum' ? 'enum' : 'message'}<input name="name" defaultValue={item?.name || ''} required pattern="[A-Za-z_][A-Za-z0-9_]*" placeholder={fieldMode ? 'Ví dụ: display_name' : enumMode ? 'Ví dụ: STATUS_UNKNOWN' : 'Ví dụ: PlayerProfile'} autoFocus/><span className="input-hint">Chữ cái, chữ số và dấu gạch dưới; không bắt đầu bằng chữ số.</span></label>
    {fieldMode && <><label>Kiểu dữ liệu<select name="type" value={type} onChange={event => setType(event.target.value)}>{types.map(value => <option key={value} value={value}>{describeType(value)}</option>)}<option value="map">Bảng ánh xạ (map)</option></select></label>
      {type === 'map' ? <div className="form-grid"><label>Kiểu khóa<select name="keyType" defaultValue={item?.keyType || 'string'}>{mapKeys.map(value => <option key={value} value={value}>{describeType(value)}</option>)}</select></label><label>Kiểu giá trị<select name="valueType" defaultValue={item?.valueType || 'string'}>{types.map(value => <option key={value} value={value}>{describeType(value)}</option>)}</select></label></div> : <label>Dạng<select name="rule" defaultValue={item?.rule || 'single'}><option value="single">Một giá trị</option><option value="optional">Tùy chọn (optional)</option><option value="repeated">Danh sách (repeated)</option></select></label>}
      <div className="info-note">{item ? <>Mã số <code>{item.number}</code> được giữ nguyên để bảo toàn định danh thuộc tính.</> : 'Mã số sẽ được tự động chọn, bỏ qua các số đang dùng và số đã reserved.'}</div>
    </>}
    {enumMode && <label>Mã số<input name="number" type="number" required min="-2147483648" max="2147483647" step="1" defaultValue={item?.number ?? (target.values.length ? Math.max(...target.values.map(value => value.number)) + 1 : 0)}/><span className="input-hint">Giá trị đầu tiên của enum proto3 phải có mã số 0.</span></label>}
    {!fieldMode && !enumMode && <label>Vị trí<select name="parent" defaultValue=""><option value="">Cấp cao nhất trong file</option>{declarations.filter(value => value.fields).map(value => <option key={value.fullName} value={value.fullName}>Bên trong {value.fullName}</option>)}</select></label>}
    <ErrorNotice error={error}/><div className="modal-actions"><button type="button" className="button" onClick={close} disabled={busy}>Hủy</button><button className="button primary" disabled={busy}>{busy && <CircleNotch className="spin"/>}{busy ? 'Đang kiểm tra…' : 'Lưu thay đổi'}</button></div>
  </form></Modal>;
}

export function HelpDialog({ onClose }) {
  return <Modal title="Một chút về Proto Studio" onClose={onClose}><div className="help-content"><div className="eyebrow">SCHEMA WORKSPACE</div><p>Schema là bản định nghĩa cấu trúc dữ liệu giữa client và server. Đổi schema không thay đổi giá vật phẩm hoặc dữ liệu người chơi.</p><ol><li>Chọn file và message hoặc enum trong workspace.</li><li>Dùng biểu mẫu để sửa. Mỗi thay đổi được kiểm tra bằng protoc.</li><li>Mở <strong>Đối chiếu</strong> để xem bản gốc và bản nháp.</li><li>Kiểm tra rồi tải ZIP. Bản nháp được giữ trong trình duyệt này.</li></ol><div className="info-note">Sau khi tải ZIP, bạn cần tự đưa các file vào repository, review tương thích và sinh lại code client/server. Proto Studio không tự commit hay deploy.</div><p className="muted">Local demo dùng schema thực trên máy. Mở GitHub cần repository có schema và SHA đầy đủ của commit.</p></div><div className="modal-actions"><button className="button primary" onClick={onClose}>Đã hiểu</button></div></Modal>;
}

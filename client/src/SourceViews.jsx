import { useMemo, useRef } from 'react';
import { alignedDiff } from './sourceDiff.js';

export function SourceView({ content, path }) {
  return <div className="source-panel" aria-label={`Mã nguồn ${path}`}><div className="source-heading"><span>{path}</span><span>Chỉ đọc · Protobuf</span></div><div className="source-scroll"><pre>{content.split('\n').map((line, index) => <div className="code-line" key={index}><span className="line-number">{index + 1}</span><code>{line || ' '}</code></div>)}</pre></div></div>;
}

export function DiffView({ original, current }) {
  const { left, right, additions, removals } = useMemo(() => alignedDiff(original, current), [original, current]);
  const panes = useRef([]);
  function syncScroll(index, event) {
    const other = panes.current[1 - index];
    if (other && other.scrollTop !== event.currentTarget.scrollTop) other.scrollTop = event.currentTarget.scrollTop;
  }
  return <section className="diff-view" aria-label="Đối chiếu bản gốc và bản nháp"><div className="section-heading"><div><h2>Thay đổi trong file</h2><p>So sánh với schema khi mở workspace.</p></div><span className="diff-count"><b>+{additions}</b> <span>-{removals}</span></span></div>{original === current && <div className="info-note">File này chưa có thay đổi. Chọn “Chỉnh sửa” để bắt đầu.</div>}{!original && !current ? <div className="empty-state"><h3>File chưa có nội dung</h3><p>Bản gốc và bản nháp đều trống.</p></div> : <div className="diff-columns">{[[left, 'BẢN GỐC'], [right, 'BẢN NHÁP']].map(([lines, label], index) => <div className="source-panel" key={label}><div className="source-heading">{label}</div><div className="source-scroll" ref={element => { panes.current[index] = element; }} onScroll={event => syncScroll(index, event)}><pre>{lines.map((line, i) => <div className={`code-line ${line.kind || ''}`} key={i} aria-hidden={line.kind === 'placeholder' || undefined}><span className="line-number">{line.number}</span><code>{line.text || ' '}</code></div>)}</pre></div></div>)}</div>}</section>;
}

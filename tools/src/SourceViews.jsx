import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { X } from '@phosphor-icons/react';
import { diffWordsWithSpace } from 'diff';
import { alignedDiff } from './sourceDiff.js';
import { tokenizeProto } from './protoSyntax.js';
import ProtoCode from './ProtoCode.jsx';

export function SourceView({ content, path }) {
  const syntax = useMemo(() => tokenizeProto(content), [content]);
  return <div className="source-panel" aria-label={`Source ${path}`}><div className="source-heading"><span>{path}</span><span>Read-only · Protobuf</span></div><div className="source-scroll"><pre>{content.split('\n').map((line, index) => <div className="code-line" key={index}><span className="line-number">{index + 1}</span><code><ProtoCode tokens={syntax[index]}/></code></div>)}</pre></div></div>;
}

export function DiffView({ original, current }) {
  const { left, right, additions, removals } = useMemo(() => alignedDiff(original, current), [original, current]);
  const [layout, setLayout] = useState(() => window.matchMedia('(max-width: 700px)').matches ? 'unified' : 'split');
  const [changesOnly, setChangesOnly] = useState(false);
  const panes = useRef([]);
  const originalSyntax = useMemo(() => tokenizeProto(original), [original]);
  const draftSyntax = useMemo(() => tokenizeProto(current), [current]);
  const highlights = useMemo(() => left.map((line, index) => line.kind === 'removed' && right[index]?.kind === 'added' ? diffWordsWithSpace(line.text, right[index].text) : null), [left, right]);
  function syncScroll(index, event) {
    const other = panes.current[1 - index];
    if (other && other.scrollTop !== event.currentTarget.scrollTop) other.scrollTop = event.currentTarget.scrollTop;
  }
  function content(line, index, side) {
    const tokens = (side === 'original' ? originalSyntax : draftSyntax)[line.number - 1] || [];
    const parts = highlights[index];
    const changes = [];
    let offset = 0;
    if (parts && line.kind !== 'placeholder') {
      for (const part of parts.filter(part => line.kind === 'removed' ? !part.added : !part.removed)) {
        if (part.added || part.removed) changes.push({ start: offset, end: offset + part.value.length, kind: part.added ? 'added' : 'removed' });
        offset += part.value.length;
      }
    }
    return <ProtoCode tokens={tokens} changes={changes}/>;
  }
  function renderLine(line, index, unified = false, side = 'draft') {
    const removed = line.kind === 'removed', added = line.kind === 'added';
    return <div className={`code-line ${line.kind || 'unchanged'}`} key={`${index}-${line.kind || 'same'}`} aria-hidden={line.kind === 'placeholder' || undefined}>
      {unified ? <><span className="line-number">{added ? '' : left[index]?.number}</span><span className="line-number">{removed ? '' : right[index]?.number}</span></> : <span className="line-number">{line.number}</span>}
      <span className="diff-marker" aria-label={added ? 'Added' : removed ? 'Removed' : undefined}>{added ? '+' : removed ? '−' : ' '}</span><code>{content(line, index, line.kind === 'removed' ? 'original' : side)}</code>
    </div>;
  }
  const indices = left.map((_, index) => index).filter(index => !changesOnly || left[index].kind || right[index].kind);
  return <section className="diff-view" aria-label="Compare original and draft">
    <div className="section-heading"><div><h2>Review changes</h2><p>Original is the version you opened. Draft contains your edits.</p></div><span className="diff-count" aria-label={`${additions} lines added, ${removals} lines removed`}><b>+{additions}</b><span>−{removals}</span></span></div>
    <div className="diff-toolbar"><div className="diff-layout" role="group" aria-label="Compare layout">{['split', 'unified'].map(value => <button key={value} aria-pressed={layout === value} onClick={() => setLayout(value)}>{value === 'split' ? 'Split' : 'Unified'}</button>)}</div><label className="diff-filter"><input type="checkbox" checked={changesOnly} onChange={event => setChangesOnly(event.target.checked)}/>Changes only</label><span className="diff-legend"><span><i className="legend-removed">−</i>Removed</span><span><i className="legend-added">+</i>Added</span></span></div>
    {original === current && <div className="info-note">No changes in this file. Edit a field, then return here to review it.</div>}
    {!original && !current ? <div className="empty-state"><h3>This file is empty</h3><p>Both original and draft are empty.</p></div> : layout === 'split' ? <div className="diff-columns">{[[left, 'ORIGINAL', 'Before your edits'], [right, 'DRAFT', 'Your current changes']].map(([lines, label, caption], pane) => <div className="source-panel" key={label}><div className="source-heading"><span>{label}</span><span>{caption}</span></div><div className="source-scroll" ref={element => { panes.current[pane] = element; }} onScroll={event => syncScroll(pane, event)}><pre>{indices.map(index => renderLine(lines[index], index, false, pane === 0 ? 'original' : 'draft'))}</pre></div></div>)}</div> : <div className="source-panel unified-diff"><div className="source-heading"><span>ORIGINAL → DRAFT</span><span>Old line / new line</span></div><div className="source-scroll"><pre>{indices.flatMap(index => left[index].kind || right[index].kind ? [left[index].kind === 'removed' ? renderLine(left[index], index, true) : null, right[index].kind === 'added' ? renderLine(right[index], index, true) : null] : [renderLine(right[index], index, true)])}</pre></div></div>}
    <p className="compare-note">Highlights show text changes, not compatibility checks. Review protocol compatibility before using the exported schemas.</p>
  </section>;
}


export function CompareDialog({ original, current, path, onClose }) {
  const ref = useRef(null);
  const titleId = useId();
  useEffect(() => {
    const dialog = ref.current;
    const opener = document.activeElement;
    dialog.showModal();
    dialog.querySelector('button').focus();
    return () => {
      dialog.close();
      if (opener instanceof HTMLElement && opener.isConnected && opener.getClientRects().length) opener.focus();
      else document.getElementById('tab-diff')?.focus();
    };
  }, []);
  return <dialog ref={ref} className="compare-modal" aria-labelledby={titleId} onCancel={event => { event.preventDefault(); onClose(); }}>
    <header className="compare-modal-header"><div><h2 id={titleId}>Compare schemas</h2><code title={path}>{path}</code></div><button className="button" onClick={onClose}><X size={17} aria-hidden="true"/>Back to editor</button></header>
    <DiffView original={original} current={current}/>
  </dialog>;
}

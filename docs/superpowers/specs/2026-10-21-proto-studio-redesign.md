# Proto Studio Workspace Redesign

The user rejected the flat sidebar, green leaf branding, and decorative right sidebar and requested a substantive UI/UX redesign.
Implementation is authorized without another design approval gate.
Retain the existing React, Tailwind, form editing, real compiler validation, persistence, source comparison, and ZIP API behavior.
Do not modify the Java server, secrets, AGENTS.md, or existing user work.

## Visual Direction

Use warm ivory surfaces, graphite text, and a muted burnt-orange accent instead of green.
Replace the leaf mark with a crisp schema-layer symbol and a matching favicon.
Keep Vietnamese typography readable, remove unnecessary uppercase metadata and filler guidance, and use clear hierarchy and restrained motion.
Use existing Phosphor icons and the installed Be Vietnam Pro family without new dependencies.
Consolidate the stylesheet rather than stacking a second full theme over obsolete styles.

## Navigation

The left sidebar must render actual nested parent folders and child files, with folder expand/collapse, indentation, selected states, file counts, and changed indicators.
Folder buttons use `aria-expanded` and accessible names `Thư mục <full directory path>`.
File buttons use accessible names `Mở <full file path>` to disambiguate duplicate basenames.
Keep `nav` labelled `Danh sách schema`, the search textbox labelled `Tìm schema`, and existing mobile drawer opening/closing labels.
Open selected-file ancestors initially and after selecting a different file.
Search shows matching files with their ancestor hierarchy expanded and clearing search restores normal navigation.
Use nested semantic lists and native buttons; do not claim an ARIA tree without implementing its full keyboard contract.

## Context Panel and Editor

Replace the passive right information panel with functional `Cấu trúc file` navigation over actual messages and enums, including nested declarations and real field/value counts.
Selecting a declaration there changes the main form.
Remove the long horizontal declaration-chip strip and provide a compact labelled declaration selector when the right panel is not visible.
Keep existing Message and Enum add buttons and all field/value forms available.
Show concise source/draft context and actual export scope rather than repeated generic help cards.
At mobile widths, keep actions reachable, use the existing accessible file drawer, avoid page overflow, and provide a practical declaration selector.

## Export Clarity

Export continues to include every current `.proto` file, modified and unmodified, preserving relative paths in one ZIP.
Rename the primary action `Xuất ZIP`.
Before download, show an accessible dialog `Xuất workspace` that explicitly states total file count, changed file count, and preserved folder structure.
The final action is `Tải ZIP`; cancel leaves the workspace unchanged.
Keep the existing download filename `proto-studio-schemas.zip` and real server-side validation.
Do not introduce per-file export or deploy modes.

## Shared Tree Helper

The frontend owns `tool/src/fileTree.js`, exporting `buildFileTree(files, changed = new Set())` and `ancestorPaths(filePath)`.
Tree nodes have `{kind: 'folder'|'file', name, path, fileCount, changedCount, children?}`.
Folders sort before files, children retain full paths, counts aggregate descendants, and file nodes have fileCount 1.
Build the same structure from search-filtered files rather than inventing separate search data.
The test worker owns `tool/test/file-tree.test.mjs` and all E2E test changes.

## Verification

First reproduce the missing folder controls in the real browser against the current UI.
Test duplicate basenames, nested structure, counts, collapse/expand/search, right-panel selection, export dialog scope, unchanged ZIP contents, persistence, mobile layout, and keyboard dismissal.
Keep existing editing and preservation acceptance coverage passing.
Inspect fresh desktop, mobile, form, and export-dialog screenshots before reporting completion.

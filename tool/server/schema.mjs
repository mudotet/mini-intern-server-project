import {
  mkdtemp,
  mkdir,
  writeFile,
  readFile,
  readdir,
  rm,
  access,
} from "node:fs/promises";
import { tmpdir, homedir } from "node:os";
import path from "node:path";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { createRequire } from "node:module";
import { zipSync, strToU8 } from "fflate";
const require = createRequire(import.meta.url);
const { FileDescriptorSet } = require("protobufjs/ext/descriptor");
const exec = promisify(execFile);
export class ApiError extends Error {
  constructor(message, status = 422, diagnostics = []) {
    super(message);
    this.status = status;
    this.diagnostics = diagnostics;
  }
}
export function validateFiles(files) {
  if (!Array.isArray(files) || !files.length || files.length > 500)
    throw new ApiError("Provide between 1 and 500 .proto files.");
  const seen = new Set();
  let bytes = 0;
  for (const file of files) {
    if (
      !file ||
      typeof file.path !== "string" ||
      typeof file.content !== "string" ||
      !/^[a-zA-Z0-9_./-]+\.proto$/.test(file.path) ||
      file.path.split("/").some((p) => !p || p === "." || p === "..") ||
      path.posix.isAbsolute(file.path) ||
      file.path.startsWith("-") ||
      seen.has(file.path)
    )
      throw new ApiError("A .proto path is invalid or duplicated.");
    seen.add(file.path);
    bytes += Buffer.byteLength(file.content);
  }
  if (bytes > 5_000_000)
    throw new ApiError("Total schema size exceeds 5 MB.", 413);
  return files;
}
let compiler;
async function findProtoc() {
  if (compiler) return compiler;
  const candidates = [process.env.PROTOC, "protoc"].filter(Boolean);
  const cache = path.join(
    process.env.GRADLE_USER_HOME || path.join(homedir(), ".gradle"),
    "caches/modules-2/files-2.1/com.google.protobuf/protoc/3.21.3",
  );
  try {
    for (const folder of await readdir(cache))
      for (const name of await readdir(path.join(cache, folder)))
        if (name.endsWith(".exe"))
          candidates.push(path.join(cache, folder, name));
  } catch {}
  for (const candidate of candidates) {
    try {
      await exec(candidate, ["--version"], { timeout: 5000 });
      compiler = candidate;
      return candidate;
    } catch {}
  }
  throw new ApiError(
    "protoc was not found. Install protoc or set PROTOC.",
    503,
  );
}
function diagnostics(stderr) {
  return stderr
    .trim()
    .split("\n")
    .filter(Boolean)
    .map((line) => {
      const m = line.match(/^(.+?):(\d+):(\d+):\s*(.*)$/);
      return m
        ? { file: m[1], line: +m[2], column: +m[3], message: m[4] }
        : { file: "", line: 0, column: 0, message: line };
    });
}
const scalar = {
  1: "double",
  2: "float",
  3: "int64",
  4: "uint64",
  5: "int32",
  6: "fixed64",
  7: "fixed32",
  8: "bool",
  9: "string",
  12: "bytes",
  13: "uint32",
  15: "sfixed32",
  16: "sfixed64",
  17: "sint32",
  18: "sint64",
};
const typeName = (f) => scalar[f.type] || (f.typeName || "").replace(/^\./, "");
function describe(descriptors, files) {
  const included = new Set(files.map((f) => f.path));
  return descriptors
    .filter((f) => included.has(f.name))
    .map((f) => {
      const messages = [],
        enums = [];
      function enumWalk(e, prefix) {
        enums.push({
          name: e.name,
          fullName: [prefix, e.name].filter(Boolean).join("."),
          values: (e.value || []).map((v) => ({
            name: v.name,
            number: v.number ?? 0,
          })),
        });
      }
      function walk(m, prefix) {
        const fullName = [prefix, m.name].filter(Boolean).join(".");
        if (!m.options?.mapEntry)
          messages.push({
            name: m.name,
            fullName,
            fields: (m.field || []).map((field) => {
              const map = (m.nestedType || []).find(
                (n) =>
                  n.options?.mapEntry &&
                  `.${fullName}.${n.name}` === field.typeName,
              );
              return map
                ? {
                    name: field.name,
                    number: field.number,
                    type: "map",
                    rule: "single",
                    keyType: typeName(map.field[0]),
                    valueType: typeName(map.field[1]),
                  }
                : {
                    name: field.name,
                    number: field.number,
                    type: typeName(field),
                    rule:
                      field.proto3Optional ||
                      (f.syntax !== "proto3" && field.label === 1)
                        ? "optional"
                        : field.label === 3
                          ? "repeated"
                          : "single",
                  };
            }),
          });
        for (const n of m.nestedType || []) walk(n, fullName);
        for (const e of m.enumType || []) enumWalk(e, fullName);
      }
      for (const m of f.messageType || []) walk(m, f.package || "");
      for (const e of f.enumType || []) enumWalk(e, f.package || "");
      return { path: f.name, package: f.package || "", imports: f.dependency || [], messages, enums };
    });
}
export async function compile(files) {
  validateFiles(files);
  const protoc = await findProtoc();
  const dir = await mkdtemp(path.join(tmpdir(), "proto-studio-"));
  try {
    for (const file of files) {
      const target = path.join(dir, file.path);
      await mkdir(path.dirname(target), { recursive: true });
      await writeFile(target, file.content);
    }
    try {
      const includes = [
        new URL(
          "../../server/build/extracted-include-protos/main/",
          import.meta.url,
        ).pathname,
        new URL("../../build/extracted-include-protos/main/", import.meta.url)
          .pathname,
        "/opt/homebrew/include",
        "/usr/local/include",
        path.dirname(require.resolve("protobufjs/package.json")),
      ];
      const includeArgs = [];
      for (const include of includes) {
        try {
          await access(include);
          includeArgs.push(`--proto_path=${include}`);
        } catch {}
      }
      await exec(
        protoc,
        [
          "--proto_path=.",
          ...includeArgs,
          "--include_source_info",
          "--include_imports",
          "--descriptor_set_out=descriptor.pb",
          ...files.map((f) => f.path),
        ],
        { cwd: dir, timeout: 15000, maxBuffer: 2_000_000 },
      );
    } catch (error) {
      if (error.code === "ENOENT" || error.killed)
        throw new ApiError("The protoc compiler did not respond.", 503);
      return {
        analysis: {
          valid: false,
          diagnostics: diagnostics(error.stderr || error.message),
          files: [],
        },
        descriptors: [],
      };
    }
    const decoded = FileDescriptorSet.toObject(
      FileDescriptorSet.decode(await readFile(path.join(dir, "descriptor.pb"))),
      { defaults: false },
    );
    return {
      analysis: {
        valid: true,
        diagnostics: [],
        files: describe(decoded.file || [], files),
      },
      descriptors: decoded.file || [],
    };
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
}
export async function analyze(files) {
  return (await compile(files)).analysis;
}
function identifier(value) {
  if (typeof value !== "string" || !/^[A-Za-z_][A-Za-z0-9_]*$/.test(value))
    throw new ApiError("Enter a valid Protobuf identifier.");
  return value;
}
function fieldText(field, number) {
  if (!field || typeof field !== "object")
    throw new ApiError("Field information is missing.");
  const name = identifier(field.name);
  const validType = (t) =>
    typeof t === "string" && /^\.?[A-Za-z_][A-Za-z0-9_.]*$/.test(t);
  if (
    !Number.isInteger(number) ||
    number < 1 ||
    number > 536870911 ||
    (number >= 19000 && number <= 19999)
  )
    throw new ApiError("Invalid field number.");
  if (field.type === "map") {
    if (!validType(field.keyType) || !validType(field.valueType))
      throw new ApiError("Invalid map type.");
    return `map<${field.keyType}, ${field.valueType}> ${name} = ${number}`;
  }
  if (
    !validType(field.type) ||
    !["single", "optional", "repeated"].includes(field.rule || "single")
  )
    throw new ApiError("Invalid field type.");
  return `${field.rule && field.rule !== "single" ? field.rule + " " : ""}${field.type} ${name} = ${number}`;
}
function offset(text, line, column) {
  const lines = text.split("\n");
  let n = 0;
  for (let i = 0; i < line; i++) n += lines[i].length + 1;
  return (
    n +
    Buffer.from(lines[line] || "")
      .subarray(0, column)
      .toString().length
  );
}
function span(text, location) {
  if (!location?.span) throw new ApiError("Source location not found.");
  const s = location.span;
  return [
    offset(text, s[0], s[1]),
    offset(text, s.length === 3 ? s[0] : s[2], s.length === 3 ? s[2] : s[3]),
  ];
}
// Replace declaration tokens while leaving whitespace and comment tokens untouched.
function tokenEdits(text, start, end, replacement) {
  const tokens = (value) =>
    [
      ...value.matchAll(
        /\/\*[\s\S]*?\*\/|\/\/[^\r\n]*|[A-Za-z_][A-Za-z0-9_]*|[0-9]+|[^\s]/g,
      ),
    ].filter(
      (match) => !match[0].startsWith("/*") && !match[0].startsWith("//"),
    );
  const oldTokens = tokens(text.slice(start, end));
  const newTokens = tokens(replacement);
  if (!oldTokens.length) throw new ApiError("Declaration token not found.");
  return oldTokens.map((token, index) => ({
    start: start + token.index,
    end: start + token.index + token[0].length,
    value:
      oldTokens.length === newTokens.length
        ? newTokens[index][0]
        : index === 0
          ? replacement
          : "",
  }));
}
function nodes(file) {
  const out = [];
  function walk(m, prefix, p) {
    const fullName = [prefix, m.name].filter(Boolean).join(".");
    out.push({ kind: "message", node: m, fullName, p });
    (m.nestedType || []).forEach((n, i) => walk(n, fullName, [...p, 3, i]));
    (m.enumType || []).forEach((e, i) =>
      out.push({
        kind: "enum",
        node: e,
        fullName: `${fullName}.${e.name}`,
        p: [...p, 4, i],
      }),
    );
  }
  (file.messageType || []).forEach((m, i) =>
    walk(m, file.package || "", [4, i]),
  );
  (file.enumType || []).forEach((e, i) =>
    out.push({
      kind: "enum",
      node: e,
      fullName: [file.package, e.name].filter(Boolean).join("."),
      p: [5, i],
    }),
  );
  return out;
}
export async function mutate(files, filePath, operation) {
  const compiled = await compile(files);
  if (!compiled.analysis.valid)
    throw new ApiError(
      "Fix the schema errors before editing.",
      422,
      compiled.analysis.diagnostics,
    );
  const source = files.find((f) => f.path === filePath),
    descriptor = compiled.descriptors.find((f) => f.name === filePath);
  if (
    !source ||
    !descriptor ||
    !operation ||
    typeof operation.kind !== "string"
  )
    throw new ApiError("Invalid file or operation.");
  const all = nodes(descriptor),
    target = all.find((n) => n.fullName === operation.target);
  const location = (p) =>
    (descriptor.sourceCodeInfo?.location || []).find(
      (l) => JSON.stringify(l.path || []) === JSON.stringify(p),
    );
  let text = source.content;
  const replace = (p, value) => {
    const [a, b] = span(text, location(p));
    text = text.slice(0, a) + value + text.slice(b);
  };
  const patchTokens = (changes) => {
    const edits = changes.flatMap(({ p, value }) => {
      const [start, end] = span(text, location(p));
      return tokenEdits(text, start, end, value);
    });
    for (const edit of edits.sort((a, b) => b.start - a.start || b.end - a.end))
      text = text.slice(0, edit.start) + edit.value + text.slice(edit.end);
  };
  const insert = (parent, value) => {
    if (!parent) {
      text += `\n${value}\n`;
      return;
    }
    const [, b] = span(text, location(parent.p));
    const at = text.lastIndexOf("}", b - 1);
    if (at < 0) throw new ApiError("Declaration range not found.");
    text =
      text.slice(0, at) +
      `\n  ${value.replace(/\n/g, "\n  ")}\n` +
      text.slice(at);
  };
  if (["addMessage", "addEnum"].includes(operation.kind)) {
    const name = identifier(operation.name),
      parent = operation.parent
        ? all.find(
            (n) => n.fullName === operation.parent && n.kind === "message",
          )
        : null;
    if (operation.parent && !parent)
      throw new ApiError("Parent message not found.");
    insert(
      parent,
      operation.kind === "addMessage"
        ? `message ${name} {\n}`
        : `enum ${name} {\n  ${name.replace(/([a-z])([A-Z])/g, "$1_$2").toUpperCase()}_UNSPECIFIED = 0;\n}`,
    );
  } else if (
    ["addField", "updateField", "deleteField"].includes(operation.kind)
  ) {
    if (target?.kind !== "message")
      throw new ApiError("Message not found.");
    const fields = target.node.field || [],
      index = fields.findIndex((f) => f.number === operation.fieldNumber);
    if (operation.kind === "addField") {
      let number = operation.field?.number;
      if (number == null) {
        number = 1;
        while (number <= 536870911) {
          const reserved = (target.node.reservedRange || []).find(
            (r) => number >= r.start && number < r.end,
          );
          if (reserved) number = reserved.end;
          else if (number >= 19000 && number <= 19999) number = 20000;
          else if (fields.some((f) => f.number === number)) number++;
          else break;
        }
      }
      insert(target, fieldText(operation.field, number) + ";");
    } else {
      if (index < 0) throw new ApiError("Field not found.");
      const p = [...target.p, 2, index];
      if (operation.kind === "deleteField") replace(p, "");
      else {
        const field = operation.field;
        const number = field?.number ?? operation.fieldNumber;
        fieldText(field, number);
        const typePath = [...p, location([...p, 5]) ? 5 : 6];
        const labelPath = [...p, 4];
        const label =
          field.type !== "map" && field.rule && field.rule !== "single"
            ? field.rule
            : "";
        let type =
          field.type === "map"
            ? `map<${field.keyType}, ${field.valueType}>`
            : field.type;
        const changes = [
          { p: [...p, 1], value: field.name },
          { p: [...p, 3], value: String(number) },
        ];
        if (location(labelPath)) changes.push({ p: labelPath, value: label });
        else if (label) type = `${label} ${type}`;
        changes.push({ p: typePath, value: type });
        patchTokens(changes);
      }
    }
  } else if (
    ["addEnumValue", "updateEnumValue", "deleteEnumValue"].includes(
      operation.kind,
    )
  ) {
    if (target?.kind !== "enum") throw new ApiError("Enum not found.");
    const value = operation.value;
    if (
      operation.kind !== "deleteEnumValue" &&
      (!value ||
        !Number.isInteger(value.number) ||
        value.number < -2147483648 ||
        value.number > 2147483647)
    )
      throw new ApiError("Invalid enum value.");
    const rendered =
      operation.kind === "deleteEnumValue"
        ? ""
        : `${identifier(value.name)} = ${value.number}`;
    if (operation.kind === "addEnumValue") insert(target, rendered + ";");
    else {
      const index = (target.node.value || []).findIndex(
        (v) => v.name === operation.oldName,
      );
      if (index < 0) throw new ApiError("Enum value not found.");
      const p = [...target.p, 2, index];
      if (operation.kind === "deleteEnumValue") replace(p, "");
      else {
        patchTokens([
          { p: [...p, 1], value: value.name },
          { p: [...p, 2], value: String(value.number) },
        ]);
      }
    }
  } else throw new ApiError("Unsupported operation.");
  const updated = files.map((f) =>
      f.path === filePath ? { ...f, content: text } : f,
    ),
    analysis = await analyze(updated);
  if (!analysis.valid)
    throw new ApiError("The edit is invalid.", 422, analysis.diagnostics);
  return { files: updated, analysis };
}
export async function exportZip(files) {
  const analysis = await analyze(files);
  if (!analysis.valid)
    throw new ApiError(
      "Fix the schema errors before exporting a ZIP.",
      422,
      analysis.diagnostics,
    );
  return zipSync(
    Object.fromEntries(files.map((f) => [f.path, strToU8(f.content)])),
  );
}

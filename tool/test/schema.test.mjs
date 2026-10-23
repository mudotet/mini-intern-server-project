import test from "node:test";
import assert from "node:assert/strict";
import { unzipSync, strFromU8 } from "fflate";
import {
  analyze,
  mutate,
  exportZip,
  validateFiles,
} from "../server/schema.mjs";
import { githubWorkspace } from "../server/workspace.mjs";
import { localWorkspace } from "./fixtures/local-workspace.mjs";
import { createApp } from "../server/index.mjs";
const source = `syntax = "proto3";
package demo;
// untouched header
message Player {
  reserved 2 to 4;
  reserved "old";
  string name = 1 [deprecated = true]; // keep field comment
  message Nested { int32 id = 1; }
  map<string, Nested> items = 5;
  oneof choice { string chosen = 6; int32 other = 7; }
}
enum State {
  option allow_alias = true;
  UNKNOWN = 0;
  ALIAS = 0;
}
`;
const files = [{ path: "game/player.proto", content: source }];
const edit = (operation) => mutate(files, files[0].path, operation);
test("real descriptors flatten nested types and resolve map values", async () => {
  const a = await analyze(files);
  assert.equal(a.valid, true);
  const messages = a.files[0].messages;
  assert.equal(messages.length, 2);
  assert.deepEqual(
    messages[0].fields.find((f) => f.name === "items"),
    {
      name: "items",
      number: 5,
      type: "map",
      rule: "single",
      keyType: "string",
      valueType: "demo.Player.Nested",
    },
  );
});
test("field edit preserves options, comments, reserved declarations and oneof", async () => {
  const result = await edit({
    kind: "updateField",
    target: "demo.Player",
    fieldNumber: 1,
    field: {
      name: "display_name",
      type: "string",
      rule: "optional",
      number: 1,
    },
  });
  assert.equal(result.analysis.valid, true);
  assert.equal(
    result.files[0].content,
    source.replace("string name = 1", "optional string display_name = 1"),
  );
});
test("automatic field allocation skips reserved and existing numbers", async () => {
  const result = await edit({
    kind: "addField",
    target: "demo.Player",
    field: { name: "score", type: "int64", rule: "single" },
  });
  assert.equal(
    result.analysis.files[0].messages[0].fields.find((f) => f.name === "score")
      .number,
    8,
  );
  assert.ok(result.files[0].content.includes("reserved 2 to 4;"));
});
test("nested field mutation and map addition compile", async () => {
  let r = await edit({
    kind: "addField",
    target: "demo.Player.Nested",
    field: {
      name: "labels",
      type: "map",
      keyType: "string",
      valueType: "string",
    },
  });
  assert.equal(r.analysis.valid, true);
  r = await mutate(r.files, files[0].path, {
    kind: "deleteField",
    target: "demo.Player.Nested",
    fieldNumber: 1,
  });
  assert.equal(r.analysis.files[0].messages[1].fields.length, 1);
});
test("enum mutations preserve aliases and options", async () => {
  let r = await edit({
    kind: "addEnumValue",
    target: "demo.State",
    value: { name: "ACTIVE", number: 1 },
  });
  r = await mutate(r.files, files[0].path, {
    kind: "updateEnumValue",
    target: "demo.State",
    oldName: "ACTIVE",
    value: { name: "READY", number: 2 },
  });
  assert.ok(r.files[0].content.includes("option allow_alias = true;"));
  assert.ok(r.files[0].content.includes("READY = 2;"));
  r = await mutate(r.files, files[0].path, {
    kind: "deleteEnumValue",
    target: "demo.State",
    oldName: "READY",
  });
  assert.equal(r.analysis.files[0].enums[0].values.length, 2);
});
test("new nested message and enum compile", async () => {
  let r = await edit({
    kind: "addMessage",
    name: "Extra",
    parent: "demo.Player",
  });
  r = await mutate(r.files, files[0].path, {
    kind: "addEnum",
    name: "Status",
    parent: "demo.Player.Extra",
  });
  assert.ok(
    r.analysis.files[0].enums.some(
      (e) => e.fullName === "demo.Player.Extra.Status",
    ),
  );
});
test("invalid mutation rejected with compiler diagnostics; source remains unchanged", async () => {
  await assert.rejects(
    edit({
      kind: "addField",
      target: "demo.Player",
      field: { name: "name", number: 8, type: "string" },
    }),
    (e) => e.status === 422 && e.diagnostics.length > 0,
  );
  assert.equal(files[0].content, source);
});
test("invalid schema is diagnosed and cannot export", async () => {
  const bad = [
    {
      path: "bad.proto",
      content: 'syntax = "proto3"; message Broken { string x = 0; }',
    },
  ];
  assert.equal((await analyze(bad)).valid, false);
  await assert.rejects(
    exportZip(bad),
    (e) => e.status === 422 && e.diagnostics.length > 0,
  );
});
test("safe relative paths only, no duplicates or traversal", () => {
  for (const p of [
    "../a.proto",
    "/a.proto",
    "a/../a.proto",
    "a\\b.proto",
    "a.txt",
    "a//b.proto",
  ])
    assert.throws(() => validateFiles([{ path: p, content: "" }]));
  assert.throws(() => validateFiles([...files, ...files]));
});
test("ZIP preserves exact content and relative paths", async () => {
  const zip = unzipSync(await exportZip(files));
  assert.deepEqual(Object.keys(zip), ["game/player.proto"]);
  assert.equal(strFromU8(zip["game/player.proto"]), source);
});
test("test fixture uses actual migrated schema and no invented SHA", async () => {
  const result = await localWorkspace();
  assert.equal(result.revision.sha, null);
  assert.equal(result.analysis.valid, true);
  assert.ok(result.files.length > 0);
});
test("GitHub rejects invalid commit, empty schema, truncation and rate limits", async () => {
  const sha = "a".repeat(40);
  await assert.rejects(githubWorkspace("owner/repo", "main"));
  for (const body of [{ tree: [] }, { tree: [], truncated: true }])
    await assert.rejects(
      githubWorkspace(
        "owner/repo",
        sha,
        async () => new Response(JSON.stringify(body)),
      ),
    );
  await assert.rejects(
    githubWorkspace(
      "owner/repo",
      sha,
      async () => new Response("{}", { status: 403 }),
    ),
    /rate limit/,
  );
});
test("GitHub uses immutable tree and blob SHA without writes", async () => {
  const sha = "a".repeat(40),
    blob = "b".repeat(40),
    calls = [];
  const result = await githubWorkspace(
    "owner/repo",
    sha,
    async (url, options) => {
      calls.push({ url, options });
      return new Response(
        JSON.stringify(
          url.includes("/trees/")
            ? {
                tree: [
                  {
                    path: "game/player.proto",
                    type: "blob",
                    mode: "100644",
                    sha: blob,
                    size: source.length,
                  },
                ],
              }
            : {
                encoding: "base64",
                content: Buffer.from(source).toString("base64"),
              },
        ),
      );
    },
  );
  assert.equal(result.revision.sha, sha);
  assert.ok(calls[0].url.endsWith(`/git/trees/${sha}?recursive=1`));
  assert.ok(calls[1].url.endsWith(`/git/blobs/${blob}`));
  assert.ok(calls.every((c) => !c.options.method));
});
test("HTTP rejects cross-origin mutations and returns real JSON diagnostics", async (t) => {
  const app = await createApp();
  await new Promise((resolve) => app.listen(0, "127.0.0.1", resolve));
  t.after(
    () =>
      new Promise((resolve) => {
        app.closeAllConnections();
        app.close(resolve);
      }),
  );
  const base = `http://127.0.0.1:${app.address().port}`;
  let response = await fetch(`${base}/api/analyze`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Origin: "https://other.test",
    },
    body: JSON.stringify({ files }),
  });
  assert.equal(response.status, 403);
  response = await fetch(`${base}/api/analyze`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ files }),
  });
  assert.equal(response.status, 200);
  assert.equal((await response.json()).valid, true);
  response = await fetch(`${base}/api/export`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ files: [{ path: "bad.proto", content: "bad" }] }),
  });
  assert.equal(response.status, 422);
  assert.ok((await response.json()).diagnostics.length);
});

test("allocation jumps large reserved ranges and compiler checks map key types", async () => {
  const input = [
    {
      path: "reserved.proto",
      content: 'syntax = "proto3"; message Huge { reserved 1 to 536870910; }',
    },
  ];
  const result = await mutate(input, "reserved.proto", {
    kind: "addField",
    target: "Huge",
    field: { name: "last", type: "string" },
  });
  assert.equal(
    result.analysis.files[0].messages[0].fields[0].number,
    536870911,
  );
  await assert.rejects(
    edit({
      kind: "addField",
      target: "demo.Player",
      field: {
        name: "invalid",
        type: "map",
        keyType: "bytes",
        valueType: "string",
      },
    }),
    (error) => error.status === 422 && error.diagnostics.length > 0,
  );
});

test("standard descriptor imports resolve through installed protobuf includes", async () => {
  const result = await analyze([
    {
      path: "options.proto",
      content:
        'syntax = "proto3"; import "google/protobuf/descriptor.proto"; message Options { google.protobuf.FileOptions options = 1; }',
    },
  ]);
  assert.equal(result.valid, true, JSON.stringify(result.diagnostics));
  assert.equal(
    result.files[0].messages[0].fields[0].type,
    "google.protobuf.FileOptions",
  );
});

test("UTF-8 source columns preserve inline comments during a surgical edit", async () => {
  const input = [
    {
      path: "utf8.proto",
      content:
        'syntax = "proto3"; message Example { /* tiếng Việt */ string name = 1; }',
    },
  ];
  const result = await mutate(input, "utf8.proto", {
    kind: "updateField",
    target: "Example",
    fieldNumber: 1,
    field: { name: "title", type: "string" },
  });
  assert.equal(
    result.files[0].content,
    input[0].content.replace("string name", "string title"),
  );
});

test("field updates preserve embedded comments around type, name, equals and number", async () => {
  const content =
    'syntax = "proto3"; message Box { optional /*presence*/ string /* units: pixels */ width /*before equals*/ = /*after equals*/ 1 /*option*/ [deprecated = true]; }';
  const input = [{ path: "comments.proto", content }];
  const result = await mutate(input, "comments.proto", {
    kind: "updateField",
    target: "Box",
    fieldNumber: 1,
    field: { name: "height", type: "int64", rule: "repeated", number: 2 },
  });
  assert.equal(
    result.files[0].content,
    content
      .replace("optional", "repeated")
      .replace("string", "int64")
      .replace("width", "height")
      .replace("*/ 1 /*option*/", "*/ 2 /*option*/"),
  );
});

test("enum updates preserve embedded comments around equals and negative numbers", async () => {
  const content =
    'syntax = "proto3"; enum State { ZERO = 0; OLD /*before equals*/ = /*after equals*/ -1 /*option*/ [deprecated = true]; }';
  const result = await mutate(
    [{ path: "comments.proto", content }],
    "comments.proto",
    {
      kind: "updateEnumValue",
      target: "State",
      oldName: "OLD",
      value: { name: "NEW", number: -2 },
    },
  );
  assert.equal(
    result.files[0].content,
    content.replace("OLD", "NEW").replace("-1", "-2"),
  );
});

test("map and rule conversions retain embedded type comments", async () => {
  const content =
    'syntax = "proto3"; message Box { map</*key*/string, /*value*/int32> /*name*/ sizes = 1; }';
  const input = [{ path: "comments.proto", content }];
  let result = await mutate(input, "comments.proto", {
    kind: "updateField",
    target: "Box",
    fieldNumber: 1,
    field: {
      name: "sizes",
      type: "map",
      keyType: "int64",
      valueType: "string",
    },
  });
  assert.equal(
    result.files[0].content,
    content
      .replace("/*key*/string", "/*key*/int64")
      .replace("/*value*/int32", "/*value*/string"),
  );
  result = await mutate(result.files, "comments.proto", {
    kind: "updateField",
    target: "Box",
    fieldNumber: 1,
    field: { name: "sizes", type: "int32", rule: "repeated" },
  });
  assert.equal(result.analysis.valid, true);
  for (const comment of ["/*key*/", "/*value*/", "/*name*/"])
    assert.ok(result.files[0].content.includes(comment));
  result = await mutate(result.files, "comments.proto", {
    kind: "updateField",
    target: "Box",
    fieldNumber: 1,
    field: {
      name: "sizes",
      type: "map",
      keyType: "string",
      valueType: "string",
    },
  });
  assert.equal(result.analysis.valid, true);
  for (const comment of ["/*key*/", "/*value*/", "/*name*/"])
    assert.ok(result.files[0].content.includes(comment));
});

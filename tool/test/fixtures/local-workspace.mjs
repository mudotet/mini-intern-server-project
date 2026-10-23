import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { ApiError, analyze } from "../../server/schema.mjs";
const root = fileURLToPath(new URL("../../../", import.meta.url));
export async function localWorkspace() {
  let source = path.join(root, "server/src/main/proto");
  try {
    await readdir(source);
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
    source = path.join(root, "src/main/proto");
  }
  const files = [];
  async function walk(dir, relative = "") {
    for (const entry of await readdir(dir, { withFileTypes: true })) {
      if (entry.isSymbolicLink())
        throw new ApiError("Schema symlinks are not supported.", 422);
      const name = path.posix.join(relative, entry.name),
        absolute = path.join(dir, entry.name);
      if (entry.isDirectory()) await walk(absolute, name);
      else if (entry.isFile() && entry.name.endsWith(".proto"))
        files.push({ path: name, content: await readFile(absolute, "utf8") });
    }
  }
  await walk(source);
  files.sort((a, b) => a.path.localeCompare(b.path));
  return {
    files,
    revision: {
      source: "local",
      repository: "mini-intern-server-project",
      sha: null,
      label: "Local demo",
    },
    analysis: await analyze(files),
  };
}

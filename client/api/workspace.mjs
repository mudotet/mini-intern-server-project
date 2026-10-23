import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { ApiError, analyze, validateFiles } from "./schema.mjs";
const root = fileURLToPath(new URL("../../", import.meta.url));
export async function localWorkspace() {
  const source = path.join(root, "server/src/main/proto");
  const files = [];
  async function walk(dir, relative = "") {
    for (const entry of await readdir(dir, { withFileTypes: true })) {
      if (entry.isSymbolicLink())
        throw new ApiError("Không chấp nhận symlink trong schema.", 422);
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
      label: "Demo cục bộ",
    },
    analysis: await analyze(files),
  };
}
export async function githubWorkspace(repository, commit, fetcher = fetch) {
  if (
    typeof repository !== "string" ||
    !/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(repository) ||
    repository.split("/").some((x) => x === "." || x === "..")
  )
    throw new ApiError("Repository phải có dạng owner/repo.");
  if (typeof commit !== "string" || !/^[a-fA-F0-9]{40}$/.test(commit))
    throw new ApiError("Cần commit SHA đầy đủ 40 ký tự.");
  async function get(endpoint) {
    let response;
    try {
      response = await fetcher(
        `https://api.github.com/repos/${repository}/${endpoint}`,
        {
          headers: {
            Accept: "application/vnd.github+json",
            "User-Agent": "Proto-Studio-Local",
          },
          signal: AbortSignal.timeout(15000),
          redirect: "error",
        },
      );
    } catch {
      throw new ApiError("Không thể kết nối GitHub. Vui lòng thử lại.", 502);
    }
    if (!response.ok) {
      const message =
        response.status === 403 || response.status === 429
          ? "GitHub giới hạn lượt truy cập. Vui lòng thử lại sau."
          : response.status === 404 || response.status === 409
            ? "Repository trống, không công khai hoặc commit không tồn tại."
            : `GitHub trả về lỗi ${response.status}.`;
      throw new ApiError(message, 502);
    }
    const body = await response.text();
    if (Buffer.byteLength(body) > 12_000_000)
      throw new ApiError("Dữ liệu GitHub quá lớn.", 413);
    try {
      return JSON.parse(body);
    } catch {
      throw new ApiError("GitHub trả về dữ liệu không hợp lệ.", 502);
    }
  }
  const tree = await get(`git/trees/${commit}?recursive=1`);
  if (tree.truncated)
    throw new ApiError(
      "Cây GitHub bị cắt ngắn, không thể tải đầy đủ schema.",
      422,
    );
  if (!Array.isArray(tree.tree))
    throw new ApiError("Không đọc được cây repository.", 502);
  const entries = tree.tree.filter((e) => e.path?.endsWith(".proto"));
  if (!entries.length)
    throw new ApiError("Không tìm thấy tệp .proto tại commit này.");
  if (entries.length > 500)
    throw new ApiError("Repository có quá nhiều tệp .proto.", 413);
  if (
    entries.some(
      (e) =>
        e.type !== "blob" ||
        e.mode === "120000" ||
        !/^[a-f0-9]{40}$/i.test(e.sha),
    )
  )
    throw new ApiError(
      "Không chấp nhận schema symlink hoặc blob không hợp lệ.",
    );
  validateFiles(entries.map((e) => ({ path: e.path, content: "" })));
  if (entries.reduce((n, e) => n + (e.size || 0), 0) > 5_000_000)
    throw new ApiError("Tổng dung lượng schema vượt quá 5 MB.", 413);
  const files = [];
  // Bound parallel requests so a large schema tree does not flood GitHub.
  for (let i = 0; i < entries.length; i += 6) {
    files.push(
      ...(await Promise.all(
        entries.slice(i, i + 6).map(async (entry) => {
          const blob = await get(`git/blobs/${entry.sha}`);
          if (blob.encoding !== "base64" || typeof blob.content !== "string")
            throw new ApiError("Không đọc được nội dung schema GitHub.", 502);
          return {
            path: entry.path,
            content: Buffer.from(blob.content, "base64").toString("utf8"),
          };
        }),
      )),
    );
    validateFiles(files);
  }
  files.sort((a, b) => a.path.localeCompare(b.path));
  return {
    files,
    revision: {
      source: "github",
      repository,
      sha: commit.toLowerCase(),
      label: `${repository} @ ${commit.slice(0, 8)}`,
    },
    analysis: await analyze(files),
  };
}

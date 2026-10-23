import { ApiError, analyze, validateFiles } from "./schema.mjs";
export async function githubWorkspace(repository, commit, fetcher = fetch) {
  if (
    typeof repository !== "string" ||
    !/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(repository) ||
    repository.split("/").some((x) => x === "." || x === "..")
  )
    throw new ApiError("Repository must use the owner/repo format.");
  if (typeof commit !== "string" || !/^[a-fA-F0-9]{40}$/.test(commit))
    throw new ApiError("Enter the full 40-character commit SHA.");
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
      throw new ApiError("Cannot connect to GitHub. Please try again.", 502);
    }
    if (!response.ok) {
      const message =
        response.status === 403 || response.status === 429
          ? "GitHub rate limit reached. Please try again later."
          : response.status === 404 || response.status === 409
            ? "The repository is empty or private, or the commit does not exist."
            : `GitHub returned status ${response.status}.`;
      throw new ApiError(message, 502);
    }
    const body = await response.text();
    if (Buffer.byteLength(body) > 12_000_000)
      throw new ApiError("The GitHub response is too large.", 413);
    try {
      return JSON.parse(body);
    } catch {
      throw new ApiError("GitHub returned invalid data.", 502);
    }
  }
  const tree = await get(`git/trees/${commit}?recursive=1`);
  if (tree.truncated)
    throw new ApiError(
      "The GitHub tree is truncated. The full schema set could not be loaded.",
      422,
    );
  if (!Array.isArray(tree.tree))
    throw new ApiError("Could not read the repository tree.", 502);
  const entries = tree.tree.filter((e) => e.path?.endsWith(".proto"));
  if (!entries.length)
    throw new ApiError("No .proto files were found at this commit.");
  if (entries.length > 500)
    throw new ApiError("The repository contains too many .proto files.", 413);
  if (
    entries.some(
      (e) =>
        e.type !== "blob" ||
        e.mode === "120000" ||
        !/^[a-f0-9]{40}$/i.test(e.sha),
    )
  )
    throw new ApiError(
      "Schema symlinks and invalid blobs are not supported.",
    );
  validateFiles(entries.map((e) => ({ path: e.path, content: "" })));
  if (entries.reduce((n, e) => n + (e.size || 0), 0) > 5_000_000)
    throw new ApiError("Total schema size exceeds 5 MB.", 413);
  const files = [];
  // Bound parallel requests so a large schema tree does not flood GitHub.
  for (let i = 0; i < entries.length; i += 6) {
    files.push(
      ...(await Promise.all(
        entries.slice(i, i + 6).map(async (entry) => {
          const blob = await get(`git/blobs/${entry.sha}`);
          if (blob.encoding !== "base64" || typeof blob.content !== "string")
            throw new ApiError("Could not read the GitHub schema content.", 502);
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

import http from "node:http";
import { readFile, stat } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { ApiError, analyze, mutate, exportZip } from "./schema.mjs";
import { githubWorkspace } from "./workspace.mjs";
const toolRoot = fileURLToPath(new URL("../", import.meta.url));
const mime = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".svg": "image/svg+xml",
  ".woff2": "font/woff2",
  ".png": "image/png",
  ".json": "application/json",
};
async function body(req) {
  if (!req.headers["content-type"]?.startsWith("application/json"))
    throw new ApiError("Send a JSON request.", 415);
  let size = 0;
  const chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    if (size > 7_000_000)
      throw new ApiError("The request exceeds the size limit.", 413);
    chunks.push(chunk);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    throw new ApiError("Invalid JSON.", 400);
  }
}
export async function createApp({ dev = false } = {}) {
  const vite = dev
    ? await (
        await import("vite")
      ).createServer({
        root: toolRoot,
        server: { middlewareMode: true, host: "127.0.0.1" },
        appType: "spa",
      })
    : null;
  const server = http.createServer(async (req, res) => {
    const send = (status, data) => {
      res.writeHead(status, {
        "Content-Type": "application/json; charset=utf-8",
        "Cache-Control": "no-store",
        "X-Content-Type-Options": "nosniff",
      });
      res.end(JSON.stringify(data));
    };
    try {
      const host = req.headers.host || "";
      if (!/^(127\.0\.0\.1|localhost)(:\d+)?$/.test(host))
        throw new ApiError("Host not allowed.", 403);
      const url = new URL(req.url, `http://${host}`);
      if (
        req.method === "POST" &&
        ((req.headers.origin && req.headers.origin !== `http://${host}`) ||
          req.headers["sec-fetch-site"] === "cross-site")
      )
        throw new ApiError("Cross-origin requests are not allowed.", 403);
      if (url.pathname.startsWith("/api/")) {
        if (req.method !== "POST")
          throw new ApiError("API not found.", 404);
        const data = await body(req);
        if (!data || typeof data !== "object" || Array.isArray(data))
          throw new ApiError("Invalid JSON request.", 400);
        if (url.pathname === "/api/github")
          return send(200, await githubWorkspace(data.repository, data.commit));
        if (url.pathname === "/api/analyze")
          return send(200, await analyze(data.files));
        if (url.pathname === "/api/mutate")
          return send(200, await mutate(data.files, data.path, data.operation));
        if (url.pathname === "/api/export") {
          const zip = await exportZip(data.files);
          res.writeHead(200, {
            "Content-Type": "application/zip",
            "Content-Disposition": 'attachment; filename="proto-draft.zip"',
            "Cache-Control": "no-store",
          });
          return res.end(zip);
        }
        throw new ApiError("API not found.", 404);
      }
      if (!["GET", "HEAD"].includes(req.method))
        throw new ApiError("Method not supported.", 405);
      if (vite)
        return vite.middlewares(req, res, () =>
          send(404, { error: "Page not found." }),
        );
      let relative;
      try {
        relative = decodeURIComponent(url.pathname);
      } catch {
        throw new ApiError("Invalid path.", 400);
      }
      const dist = path.join(toolRoot, "dist"),
        target = path.resolve(dist, "." + relative);
      if (!target.startsWith(dist + path.sep) && target !== dist)
        throw new ApiError("Invalid path.", 400);
      let file = target;
      try {
        if (!(await stat(file)).isFile()) file = path.join(dist, "index.html");
      } catch {
        file = path.join(dist, "index.html");
      }
      let content;
      try {
        content = await readFile(file);
      } catch {
        throw new ApiError(
          "The interface has not been built. Run npm run build or npm run dev.",
          503,
        );
      }
      res.writeHead(200, {
        "Content-Type": mime[path.extname(file)] || "application/octet-stream",
        "X-Content-Type-Options": "nosniff",
      });
      res.end(req.method === "HEAD" ? undefined : content);
    } catch (error) {
      const known = error instanceof ApiError;
      send(known ? error.status : 500, {
        error: known ? error.message : "An error occurred while processing the request.",
        ...(known && error.diagnostics.length
          ? { diagnostics: error.diagnostics }
          : {}),
      });
      if (!known) console.error(error);
    }
  });
  server.requestTimeout = 20000;
  server.on("close", () => vite?.close());
  return server;
}
if (
  process.argv[1] &&
  path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  const server = await createApp({ dev: process.argv.includes("--dev") });
  server.listen(5173, "127.0.0.1", () =>
    console.log("Proto Studio: http://127.0.0.1:5173"),
  );
}

// Reads tsc/vue-tsc output (--pretty false) on stdin, prints sorted unique "file:line:code message" keys.
// Usage: normalize.mjs [runCwd] [root]; files are realpath'd (folding symlinked workspaces) relative to root.
import {readFileSync, realpathSync} from "node:fs";
import {relative, resolve} from "node:path";

const [runCwd = ".", root = runCwd] = process.argv.slice(2);
const HEAD = /^(.+?)\((\d+),(\d+)\): (error|warning) (TS\d+): (.*)$/;
const real = (p) => { try { return realpathSync(p); } catch { return p; } };
const keys = new Set();
for (const raw of readFileSync(0, "utf8").split("\n")) {
    const m = HEAD.exec(raw.replace(/\x1b\[[0-9;]*m/g, ""));
    if (!m) continue;
    const file = relative(real(resolve(root)), real(resolve(runCwd, m[1]))).replace(/\\/g, "/");
    keys.add(`${file}:${m[2]}:${m[5]} ${m[6]}`);
}
const sortKey = (s) => s.replace(/:(\d+):/, (_, n) => `:${n.padStart(6, "0")}:`);
const out = [...keys].sort((a, b) => sortKey(a).localeCompare(sortKey(b)));
process.stdout.write(out.join("\n") + (out.length ? "\n" : ""));

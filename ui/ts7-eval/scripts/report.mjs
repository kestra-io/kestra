// Turns results/raw into results/summary.json and the markdown table pasted into the PR body.
// results/raw/runs.tsv lists "name<TAB>cwd of the run"; ROOT (default "..") anchors the diagnostic paths.
import {readFileSync, writeFileSync, existsSync} from "node:fs";
import {execFileSync} from "node:child_process";
import {dirname, join} from "node:path";
import {fileURLToPath} from "node:url";

const RAW = "results/raw";
const ROOT = process.env.ROOT ?? "..";
const NORMALIZE = join(dirname(fileURLToPath(import.meta.url)), "normalize.mjs");
const RUNS = existsSync(`${RAW}/runs.tsv`)
    ? readFileSync(`${RAW}/runs.tsv`, "utf8").trim().split("\n").map((l) => l.split("\t"))
    : ["vue-tsc", "tsgo", "tsrs", "tsrs-fast", "tsgo-flat", "tsrs-flat-4g"].map((n) => [n, n === "vue-tsc" ? ".." : "."]);
const ORDER = RUNS.map(([n]) => n);
const CWD = Object.fromEntries(RUNS);
const median = (xs) => { const s = [...xs].sort((a, b) => a - b); return s.length ? s[Math.floor((s.length - 1) / 2)] : NaN; };
const normalize = (name) => execFileSync("node", [NORMALIZE, CWD[name], ROOT], {input: readFileSync(`${RAW}/${name}.txt`)}).toString().split("\n").filter(Boolean);
const rawCount = (name) => readFileSync(`${RAW}/${name}.txt`, "utf8").split("\n").filter((l) => /\): error TS\d+:/.test(l)).length;

const mem = readFileSync(`${RAW}/mem.jsonl`, "utf8").trim().split("\n").map((l) => JSON.parse(l));
const failedBase = mem.filter((m) => m.name === "vue-tsc" && m.exitCode !== 0);
if (failedBase.length) throw new Error(`The vue-tsc baseline exited non-zero in ${failedBase.length} run(s); see ${RAW}/vue-tsc.txt.`);
const rows = ORDER.filter((n) => existsSync(`${RAW}/${n}.hyperfine.json`)).map((name) => {
    const hf = JSON.parse(readFileSync(`${RAW}/${name}.hyperfine.json`, "utf8")).results[0];
    const maxProc = [...readFileSync(`${RAW}/${name}.time`, "utf8").matchAll(/Maximum resident set size \(kbytes\): (\d+)/g)].map((m) => Number(m[1]));
    const keys = normalize(name);
    return {
        name, runs: hf.times.length, meanS: hf.mean, stddevS: hf.stddev, minS: hf.min, maxS: hf.max,
        treeRssMb: median(mem.filter((m) => m.name === name).map((m) => m.peakTreeRssKb)) / 1024,
        maxProcRssMb: median(maxProc) / 1024,
        diagnostics: rawCount(name), diagnosticsFolded: keys.length, keys,
    };
});

const base = rows.find((r) => r.name === "vue-tsc");
const byName = (n) => rows.find((r) => r.name === n);
const fmtPct = (x) => `${x > 0 ? "+" : ""}${Math.round(x * 100)}%`;
const lines = [
    "| Run | Wall time, mean ± σ | Speedup vs vue-tsc | Peak RSS, process tree | Δ RSS vs vue-tsc | Largest process | Diagnostics (raw / folded) | Δ vs vue-tsc |",
    "|---|---|---|---|---|---|---|---|",
];
for (const r of rows) {
    const set = new Set(r.keys), baseSet = new Set(base.keys);
    const added = r.keys.filter((k) => !baseSet.has(k)).length, removed = base.keys.filter((k) => !set.has(k)).length;
    lines.push(`| \`${r.name}\` | ${r.meanS.toFixed(1)} s ± ${r.stddevS.toFixed(1)} | ${(base.meanS / r.meanS).toFixed(1)}x | ${(r.treeRssMb / 1024).toFixed(2)} GB | ${fmtPct(r.treeRssMb / base.treeRssMb - 1)} | ${(r.maxProcRssMb / 1024).toFixed(2)} GB | ${r.diagnostics} / ${r.diagnosticsFolded} | +${added} / -${removed} |`);
}
const sameAs = (a, b) => a.keys.length === b.keys.length && a.keys.every((k, i) => k === b.keys[i]);
const pairs = [["tsgo", "tsrs"], ["tsgo", "tsrs-fast"], ["tsgo-flat", "tsrs-flat-4g"], ["tsgo-flat", "tsrs-flat-go"]]
    .filter(([a, b]) => byName(a) && byName(b));
lines.push("", ...pairs.map(([a, b]) => `- \`${b}\` vs \`${a}\`: ${sameAs(byName(a), byName(b)) ? "identical diagnostics" : "DIFFERENT diagnostics"}`));
writeFileSync("results/summary.json", JSON.stringify(rows.map(({keys, ...r}) => r), null, 2) + "\n");
writeFileSync("results/results.md", lines.join("\n") + "\n");
for (const r of rows) writeFileSync(`results/${r.name}.diagnostics.txt`, r.keys.join("\n") + "\n");
console.log(lines.join("\n"));

// Buckets folded diagnostics (results/<run>.diagnostics.txt) using rules backed by repros/ and ISSUES.md.
import {readFileSync, writeFileSync} from "node:fs";

const RULES = [
    ["expected v4 (config): data-* attribute not in dataAttributes", (c, m) => /^TS(2353|2561)$/.test(c) && /'data-[^']*'' does not exist/.test(m)],
    ["mapper bug (known, vuejs/language-tools#6318): aria-* / role on a component", (c, m) => c === "TS2353" && /'(role|'aria-[^']*')' does not exist/.test(m) && !/HTMLAttributes/.test(m)],
    ["mapper bug (known, vuejs/language-tools#6334): native listener on a component without emits", (c, m) => c === "TS2353" && /'on[A-Z]\w*' does not exist/.test(m)],
    ["mapper bug (known, vuejs/language-tools#6306): v-else-if narrowing over a v-for local", (c) => c === "TS2367"],
    ["TS 7.1 regression: TS5088 on a recursive mapped type (repros/ts5088-min, ISSUES.md #1)", (c) => c === "TS5088"],
    ["TS 7.1 regression: TS2883 on a subpath of a deduplicated package (repros/ts2883-min, ISSUES.md #1)", (c) => c === "TS2883"],
    ["unconfirmed (EE only): GlobalComponents augmented twice with different types", (c) => c === "TS2717"],
    ["unconfirmed (EE only): SDK key-parity type assertions", (c) => c === "TS2344"],
    ["cascade of TS5088/TS2883 (declaration not emitted for a referenced file)", (c) => ["TS6305", "TS7016", "TS7006", "TS7031"].includes(c)],
    ["genuine (our code): v-model on a read-only computed", (c) => c === "TS2540"],
    ["genuine (our code): camelCase data attribute renders without its dash", (c, m) => c === "TS2353" && /'data[A-Z]\w*' does not exist/.test(m)],
    ["genuine (our code): global directive missing from GlobalDirectives", (c, m) => c === "TS2339" && /GlobalDirectives/.test(m)],
    ["expected v4: unknown custom element / component", (c, m) => c === "TS2339"],
    ["expected v4: v-model write-back of defineModel<T>() (T | undefined)", (c) => c === "TS2322"],
    ["expected v4: unknown prop or fallthrough attr (checkUnknownProps always on)", (c) => /^TS(2353|2561)$/.test(c)],
];

const run = process.argv[2] ?? "tsgo";
const lines = readFileSync(`results/${run}.diagnostics.txt`, "utf8").split("\n").filter(Boolean);
const counts = new Map(RULES.map(([name]) => [name, 0]));
const other = [];
for (const line of lines) {
    const [, code, msg] = /^\S+:\d+:(TS\d+) (.*)$/.exec(line) ?? [];
    const hit = RULES.find(([, test]) => test(code, msg));
    if (hit) counts.set(hit[0], counts.get(hit[0]) + 1); else other.push(line);
}
const md = ["| Bucket | Count |", "|---|---|", ...[...counts].map(([k, v]) => `| ${k} | ${v} |`), `| unclassified | ${other.length} |`, `| **total** | **${lines.length}** |`];
writeFileSync(`results/triage.md`, md.join("\n") + "\n" + (other.length ? "\nUnclassified:\n\n" + other.map((l) => `- ${l}`).join("\n") + "\n" : ""));
console.log(md.join("\n"));
if (other.length) console.log(other.slice(0, 20).join("\n"));

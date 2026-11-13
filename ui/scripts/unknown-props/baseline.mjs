import {spawnSync} from "node:child_process"
import {readFileSync, realpathSync, writeFileSync} from "node:fs"
import {createRequire} from "node:module"
import {dirname, join, relative, resolve} from "node:path"
import {fileURLToPath} from "node:url"
import {compare, decide, knownRenames} from "../explicit-any/baseline.mjs"

const HERE = dirname(fileURLToPath(import.meta.url))
// TS2353 and TS2561 are what `checkUnknownProps` reports; every other diagnostic fails the check as before.
const UNKNOWN_PROP = new Set(["TS2353", "TS2561"])
const LOCATED = /^(.+?)\((\d+),(\d+)\): error (TS\d+): (.*)$/
const GLOBAL = /^error (TS\d+): (.*)$/
const PROJECTS = [
    ["--build", "tsconfig.ds.json", "design-system compiled"],
    ["--build", "tsconfig.app.json", "app checked"],
    ["--project", "tsconfig.test.json", "test checked"],
]

/**
 * Splits vue-tsc output into unknown-prop counts per file and the other diagnostics. A file reached twice, once
 * through a `node_modules/@kestra-io/*` symlink, counts once.
 */
export function classify(output, root, real = (path) => path) {
    const seen = new Set(), counts = {}, others = []
    for (const line of output.split("\n")) {
        const located = LOCATED.exec(line.trim())
        if (!located) {
            const global = GLOBAL.exec(line.trim())
            if (global) others.push(line.trim())
            continue
        }
        const [, path, row, column, code, message] = located
        const file = relative(root, real(resolve(root, path))).replaceAll("\\", "/")
        const key = `${file}:${row}:${column}:${code}:${message}`
        if (seen.has(key)) continue
        seen.add(key)
        if (UNKNOWN_PROP.has(code)) counts[file] = (counts[file] ?? 0) + 1
        else others.push(`${file}(${row},${column}): error ${code}: ${message}`)
    }
    const sorted = Object.fromEntries(Object.entries(counts).sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0)))
    return {counts: sorted, others}
}

function realPath(path) {
    try {
        return realpathSync(path)
    } catch {
        return path
    }
}

function typecheck() {
    const require = createRequire(join(process.cwd(), "package.json"))
    const bin = require.resolve("vue-tsc/bin/vue-tsc.js")
    let output = ""
    for (const [mode, config, done] of PROJECTS) {
        // The app project runs out of heap at Node's default limit once unknown props are checked.
        const run = spawnSync(process.execPath, ["--max-old-space-size=8192", bin, mode, join(HERE, config), "--pretty", "false"], {
            cwd: process.cwd(),
            encoding: "utf8",
            maxBuffer: 256 * 1024 * 1024,
        })
        if (run.status === null || ![0, 1, 2].includes(run.status) || run.stderr.includes("FATAL ERROR")) {
            console.error(`vue-tsc stopped on ${config} (exit ${run.status ?? run.signal}):\n${run.stderr}`)
            process.exit(2)
        }
        output += run.stdout
        console.log(done)
    }
    return output
}

function main() {
    const write = process.argv.includes("--write")
    const acceptNew = process.argv.includes("--accept-new-props")
    const baselinePath = join(HERE, "baseline.json")
    const {counts, others} = classify(typecheck(), process.cwd(), realPath)
    if (others.length) {
        console.error(others.join("\n"))
        process.exit(1)
    }
    const baseline = JSON.parse(readFileSync(baselinePath, "utf8"))
    const {added, removed, moved} = compare(baseline, counts, knownRenames())
    const summary = `${Object.values(counts).reduce((sum, n) => sum + n, 0)} unknown props in ${Object.keys(counts).length} files`
    const line = ({file, was, now, from}) => (from ? `  ${file}: moved from ${from}, ${was} -> ${now}` : `  ${file}: ${was} -> ${now}`)
    const {action, reason, files, raised} = decide({added, removed, moved, write, acceptNewAny: acceptNew})

    if (action === "ok") {
        console.log(`No change against the unknown-props baseline: ${summary}.`)
        return
    }
    if (action === "fail" && reason === "added") {
        console.error(`New unknown props in ${files.length} file(s). Declare the prop on the component or stop passing it, do not raise the baseline.\n${files.map(line).join("\n")}`)
        process.exit(1)
    }
    if (action === "fail") {
        console.error(`The unknown-props baseline is out of date for ${files.length} file(s). Run \`npm run check:types -- --write\` so it follows the code.\n${files.map(line).join("\n")}`)
        process.exit(1)
    }
    writeFileSync(baselinePath, `${JSON.stringify(counts, null, 2)}\n`)
    console.log(`${raised ? "Raised" : "Lowered"} the unknown-props baseline for ${files.length} file(s), now ${summary}:\n${files.map(line).join("\n")}`)
}

if (process.argv[1] === fileURLToPath(import.meta.url)) main()

import {execFileSync} from "node:child_process"
import {readFileSync, writeFileSync} from "node:fs"
import {createRequire} from "node:module"
import {dirname, join} from "node:path"
import {fileURLToPath} from "node:url"

const RULE = "typescript/no-explicit-any"
const CODE = "typescript(no-explicit-any)"

/** Counts of the rule per file, keyed by a `/`-separated path relative to cwd, in code-point order so the file is stable across machines. */
export function countByFile(diagnostics) {
    const counts = {}
    for (const {code, filename} of diagnostics) {
        if (code !== CODE) continue
        const file = filename.replaceAll("\\", "/")
        counts[file] = (counts[file] ?? 0) + 1
    }
    return Object.fromEntries(Object.entries(counts).sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0)))
}

/** Files whose count moved since the baseline, split by direction. */
export function compare(baseline, current) {
    const added = [], removed = []
    for (const file of new Set([...Object.keys(baseline), ...Object.keys(current)])) {
        const was = baseline[file] ?? 0, now = current[file] ?? 0
        if (now > was) added.push({file, was, now})
        if (now < was) removed.push({file, was, now})
    }
    return {added, removed}
}

// Resolved from the working directory, not from this file, so ui-ee runs its own oxlint against its
// own baseline while sharing the script the way the translation tooling does.
function lint(paths) {
    const require = createRequire(join(process.cwd(), "package.json"))
    const manifest = require.resolve("oxlint/package.json")
    const bin = join(dirname(manifest), require(manifest).bin.oxlint)
    const args = [bin, "-A", "all", "-D", RULE, "--format=json", ...paths]
    let stdout
    try {
        stdout = execFileSync(process.execPath, args, {encoding: "utf8", maxBuffer: 256 * 1024 * 1024, stdio: ["ignore", "pipe", "pipe"]})
    } catch (error) {
        // Exit 1 with a JSON body is oxlint reporting findings, which is the normal case here.
        if (error.status !== 1 || !error.stdout) {
            console.error(`oxlint failed (exit ${error.status ?? "?"}):\n${error.stderr ?? error.message}`)
            process.exit(2)
        }
        stdout = error.stdout
    }
    try {
        return JSON.parse(stdout).diagnostics
    } catch {
        console.error(`oxlint returned something that is not JSON:\n${stdout.slice(0, 500)}`)
        process.exit(2)
    }
}

function annotate(file, message) {
    if (process.env.GITHUB_ACTIONS === "true") console.log(`::error file=${file}::${message}`)
}

function main() {
    const write = process.argv.includes("--write")
    const lock = process.argv.includes("--lock")
    const paths = process.argv.slice(2).filter((arg) => !arg.startsWith("--"))
    const baselinePath = join(process.cwd(), "scripts", "explicit-any", "baseline.json")
    const current = countByFile(lint(paths))
    const total = Object.values(current).reduce((sum, n) => sum + n, 0)
    const summary = `${total} explicit any in ${Object.keys(current).length} files`

    if (write) {
        writeFileSync(baselinePath, `${JSON.stringify(current, null, 2)}\n`)
        console.log(`Wrote ${summary} to ${baselinePath}`)
        return
    }

    const {added, removed} = compare(JSON.parse(readFileSync(baselinePath, "utf8")), current)
    const line = ({file, was, now}) => `  ${file}: ${was} -> ${now}${now === 0 ? " (removed from the baseline)" : ""}`

    if (added.length) {
        console.error(`New \`any\` in ${added.length} file(s). Type it, or if it is genuinely unavoidable say why in the PR and run \`npm run check:ts-any -- --write\`.\n${added.map(line).join("\n")}`)
        for (const {file, was, now} of added) annotate(file, `explicit any went from ${was} to ${now}; type it or update the baseline`)
    }
    if (removed.length && !lock) {
        console.error(`${removed.length} file(s) improved. Lock it in with \`npm run check:ts-any -- --write\` so the baseline keeps meaning something.\n${removed.map(line).join("\n")}`)
        for (const {file, was, now} of removed) annotate(file, `explicit any went from ${was} to ${now}; run check:ts-any --write to lower the baseline`)
    }
    if (added.length || (removed.length && !lock)) process.exit(1)

    // --lock only ever lowers: increases were rejected above, so what is left is the improvement.
    if (removed.length) {
        writeFileSync(baselinePath, `${JSON.stringify(current, null, 2)}\n`)
        console.log(`Lowered the baseline for ${removed.length} file(s):\n${removed.map(line).join("\n")}`)
        return
    }
    console.log(`No change against the baseline: ${summary}.`)
}

if (process.argv[1] === fileURLToPath(import.meta.url)) main()

import {execFileSync} from "node:child_process"
import {readdirSync, readFileSync, writeFileSync} from "node:fs"
import {createRequire} from "node:module"
import {dirname, join, relative} from "node:path"
import {fileURLToPath} from "node:url"

const RULE = "typescript/no-explicit-any"
const CODE = "typescript(no-explicit-any)"
const TEMPLATE_ANY = /\bas\s+any\b|:\s*any\b|\bany\[\]|<\s*any\s*>|,\s*any\s*>/g

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

/** Every expression a template evaluates: interpolations, directive values and dynamic argument names. */
export function templateExpressions(ast) {
    const found = []
    const walk = (node) => {
        if (node.type === 5) found.push(node.content.loc.source)
        for (const prop of node.props ?? []) {
            if (prop.type !== 7) continue
            if (prop.exp) found.push(prop.exp.loc.source)
            if (prop.arg?.isStatic === false) found.push(prop.arg.loc.source)
        }
        for (const child of node.children ?? []) walk(child)
    }
    walk(ast)
    return found
}

/** Explicit `any` in the template of a single-file component, which oxlint does not see because it only lints `<script>`. */
export function countTemplateAny(source, parse) {
    const ast = parse(source, {ignoreEmpty: false}).descriptor.template?.ast
    if (!ast) return 0
    return templateExpressions(ast).reduce((sum, code) => sum + (code.match(TEMPLATE_ANY)?.length ?? 0), 0)
}

/** Script and template counts added up per file, in the same code-point order. */
export function merge(script, template) {
    const counts = {...script}
    for (const [file, n] of Object.entries(template)) counts[file] = (counts[file] ?? 0) + n
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

function vueFiles(paths) {
    const found = []
    const walk = (dir) => {
        for (const entry of readdirSync(dir, {withFileTypes: true})) {
            if (entry.name === "node_modules" || entry.name === "dist") continue
            const path = join(dir, entry.name)
            if (entry.isDirectory()) walk(path)
            else if (entry.name.endsWith(".vue")) found.push(path)
        }
    }
    for (const path of paths) walk(join(process.cwd(), path))
    return found
}

function templateCounts(paths) {
    const require = createRequire(join(process.cwd(), "package.json"))
    const {parse} = require("@vue/compiler-sfc")
    const counts = {}
    for (const path of vueFiles(paths)) {
        const n = countTemplateAny(readFileSync(path, "utf8"), parse)
        if (n) counts[relative(process.cwd(), path).replaceAll("\\", "/")] = n
    }
    return counts
}

function annotate(file, message) {
    if (process.env.GITHUB_ACTIONS === "true") console.log(`::error file=${file}::${message}`)
}

function main() {
    const write = process.argv.includes("--write")
    const lock = process.argv.includes("--lock")
    const paths = process.argv.slice(2).filter((arg) => !arg.startsWith("--"))
    const baselinePath = join(process.cwd(), "scripts", "explicit-any", "baseline.json")
    const current = merge(countByFile(lint(paths)), templateCounts(paths))
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

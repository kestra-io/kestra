import {execFileSync} from "node:child_process"
import {readdirSync, readFileSync, writeFileSync} from "node:fs"
import {createRequire} from "node:module"
import {dirname, join, relative} from "node:path"
import {fileURLToPath} from "node:url"

const RULE = "typescript/no-explicit-any"
const CODE = "typescript(no-explicit-any)"

// The closing `[,>]` is a lookahead so that a generic keeps matching after it: `Map<any, any>` counts two.
const TEMPLATE_ANY = /\bas\s+any\b|:\s*any\b|\bany\[\]|[<,]\s*any\s*(?=[,>])/g

// `NodeTypes` values: @vue/compiler-sfc does not re-export the enum and @vue/compiler-core is not a declared dependency.
const INTERPOLATION = 5
const DIRECTIVE = 7

export function countByFile(diagnostics) {
    const counts = {}
    for (const {code, filename} of diagnostics) {
        if (code !== CODE) continue
        const file = filename.replaceAll("\\", "/")
        counts[file] = (counts[file] ?? 0) + 1
    }
    return Object.fromEntries(Object.entries(counts).sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0)))
}

function templateExpressions(ast) {
    const found = []
    const walk = (node) => {
        if (node.type === INTERPOLATION) found.push(node.content.loc.source)
        for (const prop of node.props ?? []) {
            if (prop.type === DIRECTIVE && prop.exp) found.push(prop.exp.loc.source)
        }
        for (const child of node.children ?? []) walk(child)
    }
    walk(ast)
    return found
}

/** The index of the `}` closing the `{` at `from`, counting braces so a nested object does not end it early. */
function closingBrace(expression, from) {
    let depth = 0
    for (let i = from; i < expression.length; i++) {
        if (expression[i] === "{") depth++
        else if (expression[i] === "}" && --depth === 0) return i
    }
    return -1
}

/** The same expression with every string literal blanked, so `:title="'Type: any'"` is not read as a type. */
function withoutStrings(expression) {
    let out = "", quote = null
    for (let i = 0; i < expression.length; i++) {
        const char = expression[i]
        if (quote) {
            if (char === "\\") {
                out += "  "
                i++
                continue
            }
            // `${…}` inside a template literal is code again, so only the literal text around it is blanked.
            if (quote === "`" && char === "$" && expression[i + 1] === "{") {
                const end = closingBrace(expression, i + 1)
                if (end === -1) return out + " ".repeat(expression.length - i)
                out += expression.slice(i, end + 1)
                i = end
                continue
            }
            out += char === "\n" ? char : " "
            if (char === quote) quote = null
            continue
        }
        if (char === "\"" || char === "'" || char === "`") quote = char
        out += char
    }
    return out
}

/** Explicit `any` in the template of a single-file component, which oxlint does not see because it only lints `<script>`. */
export function countTemplateAny(source, parse) {
    const {descriptor, errors} = parse(source, {ignoreEmpty: false})
    if (!descriptor.template) return 0
    // A recoverable error still yields a usable ast, and those components build today; only a missing one is fatal.
    if (!descriptor.template.ast) throw new Error(errors.map((error) => error.message).join("\n"))
    return templateExpressions(descriptor.template.ast)
        .reduce((sum, expression) => sum + (withoutStrings(expression).match(TEMPLATE_ANY)?.length ?? 0), 0)
}

export function merge(script, template) {
    const counts = {...script}
    for (const [file, n] of Object.entries(template)) counts[file] = (counts[file] ?? 0) + n
    return Object.fromEntries(Object.entries(counts).sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0)))
}

export function compare(baseline, current, renames = {}) {
    const added = [], removed = []
    for (const file of new Set([...Object.keys(baseline), ...Object.keys(current)])) {
        const was = baseline[file] ?? 0, now = current[file] ?? 0
        if (now > was) added.push({file, was, now})
        if (now < was) removed.push({file, was, now})
    }

    // A file git reports as moved is not a new file, so its entry follows the path instead of reading as new `any`.
    const moved = []
    for (const arrival of [...added]) {
        const from = renames[arrival.file]
        const departure = from && removed.find(({file, now}) => file === from && now === 0)
        if (!departure || departure.was < arrival.now) continue
        added.splice(added.indexOf(arrival), 1)
        removed.splice(removed.indexOf(departure), 1)
        moved.push({file: arrival.file, was: departure.was, now: arrival.now, from: departure.file})
    }
    return {added, removed, moved}
}

/**
 * What to do about a comparison: `write` updates the baseline, `fail` refuses with a reason, `ok` leaves it alone.
 * Raising a count is the one move that hides an `any`, so it takes both flags; everything else only needs `--write`.
 */
export function decide({added, removed, moved = [], write, acceptNewAny}) {
    if (added.length && !(write && acceptNewAny)) return {action: "fail", reason: "added", files: added}
    const stale = [...removed, ...moved]
    if (!write) return stale.length ? {action: "fail", reason: "stale", files: stale} : {action: "ok"}
    if (!added.length && !stale.length) return {action: "ok"}
    return {action: "write", raised: added.length > 0, files: [...added, ...stale]}
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
        let entries
        try {
            entries = readdirSync(dir, {withFileTypes: true})
        } catch (error) {
            console.error(`Cannot read ${dir}: ${error.message}`)
            process.exit(2)
        }
        for (const entry of entries) {
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
        const file = relative(process.cwd(), path).replaceAll("\\", "/")
        let n
        try {
            n = countTemplateAny(readFileSync(path, "utf8"), parse)
        } catch (error) {
            // A template that does not parse would otherwise contribute a partial count and read as an improvement.
            console.error(`Cannot parse ${file}:\n${error.message}`)
            process.exit(2)
        }
        if (n) counts[file] = n
    }
    return counts
}

function annotate(file, message) {
    if (process.env.GITHUB_ACTIONS === "true") console.log(`::error file=${file}::${message}`)
}

/** Renames git has staged, as new path -> old path, both relative to the working directory. */
function stagedRenames() {
    let status, prefix
    try {
        const git = (args) => execFileSync("git", args, {encoding: "utf8", stdio: ["ignore", "pipe", "pipe"]})
        prefix = git(["rev-parse", "--show-prefix"]).trim()
        status = git(["status", "--porcelain=v1", "-M"])
    } catch {
        return {}
    }
    const renames = {}
    for (const entry of status.split("\n")) {
        if (!entry.startsWith("R")) continue
        const [from, to] = entry.slice(3).split(" -> ").map((path) => path.replace(/^"|"$/g, ""))
        if (to?.startsWith(prefix) && from.startsWith(prefix)) renames[to.slice(prefix.length)] = from.slice(prefix.length)
    }
    return renames
}

function main() {
    const write = process.argv.includes("--write")
    const acceptNewAny = process.argv.includes("--accept-new-any")
    const paths = process.argv.slice(2).filter((arg) => !arg.startsWith("--"))
    const baselinePath = join(process.cwd(), "scripts", "explicit-any", "baseline.json")
    const current = merge(countByFile(lint(paths)), templateCounts(paths))
    const summary = `${Object.values(current).reduce((sum, n) => sum + n, 0)} explicit any in ${Object.keys(current).length} files`
    let baseline
    try {
        baseline = JSON.parse(readFileSync(baselinePath, "utf8"))
    } catch (error) {
        console.error(`Cannot read ${baselinePath}: ${error.message}`)
        process.exit(2)
    }
    const {added, removed, moved} = compare(baseline, current, stagedRenames())
    const line = ({file, was, now, from}) =>
        from ? `  ${file}: moved from ${from}, ${was === now ? `still ${now}` : `${was} -> ${now}`}` : `  ${file}: ${was} -> ${now}${now === 0 ? " (removed from the baseline)" : ""}`
    const {action, reason, files, raised} = decide({added, removed, moved, write, acceptNewAny})

    if (action === "ok") {
        console.log(`No change against the baseline: ${summary}.`)
        return
    }

    if (action === "fail" && reason === "added") {
        console.error(`New \`any\` in ${files.length} file(s). Type the value, do not raise the baseline.\n${files.map(line).join("\n")}`)
        for (const {file, was, now} of files) annotate(file, `explicit any went from ${was} to ${now}; type the value instead of raising the baseline`)
        process.exit(1)
    }

    if (action === "fail") {
        console.error(`The baseline is out of date for ${files.length} file(s). Run \`npm run check:ts-any -- --write\` so it follows the code.\n${files.map(line).join("\n")}`)
        for (const {file, was, now, from} of files) {
            annotate(file, from ? `moved from ${from}; run check:ts-any --write so the baseline follows` : `explicit any went from ${was} to ${now}; run check:ts-any --write to update the baseline`)
        }
        process.exit(1)
    }

    const verb = raised ? "Raised" : files.every(({from}) => from) ? "Updated" : "Lowered"
    writeFileSync(baselinePath, `${JSON.stringify(current, null, 2)}\n`)
    console.log(`${verb} the baseline for ${files.length} file(s), now ${summary}:\n${files.map(line).join("\n")}`)
}

if (process.argv[1] === fileURLToPath(import.meta.url)) main()

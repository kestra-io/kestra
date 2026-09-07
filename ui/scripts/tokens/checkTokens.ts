import fs from "fs"
import path from "path"

const SCANNED_EXTENSIONS = new Set([".vue", ".scss", ".css", ".ts", ".tsx", ".js", ".jsx", ".mjs"])
const IGNORED_DIRECTORIES = new Set(["node_modules", "dist", "coverage", "storybook-static", ".git", ".nuxt", "playwright-report"])

/** A `var(--ks-*)` whose name is not declared anywhere. */
export interface DeadToken {
    file: string;
    line: number;
    token: string;
    hasFallback: boolean;
    suggestions: string[];
}

export interface TokenReport {
    known: number;
    usages: number;
    dead: DeadToken[];
    withFallback: DeadToken[];
}

/**
 * Names retired by a rename, mapped to what replaced them. A fuzzy match cannot find these on its
 * own: `--ks-primary` is no closer to `--ks-text-link` than to twenty other tokens.
 */
const RETIRED: Record<string, string[]> = {
    "--ks-primary": ["--ks-text-link", "--ks-border-focus"],
    "--ks-background-body": ["--ks-bg-base", "--ks-bg-surface"],
    "--ks-background-card": ["--ks-bg-surface", "--ks-bg-elevated", "--ks-bg-hover"],
    "--ks-border-primary": ["--ks-border-default"],
    "--ks-border-radius-sm": ["--ks-radius-sm"],
    "--ks-button-background-secondary-hover": ["--ks-btn-secondary-bg-hover"],
    "--ks-color-text-primary": ["--ks-text-primary"],
    "--ks-color-text-secondary": ["--ks-text-secondary"],
    "--ks-surface-secondary": ["--ks-bg-base", "--ks-bg-elevated"],
    "--ks-tag-background": ["--ks-bg-tag"],
    "--ks-content-primary": ["--ks-text-primary"],
    "--ks-content-secondary": ["--ks-text-secondary"],
    "--ks-content-link": ["--ks-text-link"],
    "--ks-content-success": ["--ks-text-success"],
    "--ks-border-active": ["--ks-border-focus"],
    "--ks-bg-body": ["--ks-bg-base"],
    "--ks-shadow-md": ["--ks-shadow-base"],
    "--ks-status-trace": ["--ks-status-neutral"],
    "--ks-playground-bg-color": ["--ks-toggle-playground"],
}

const walk = (directory: string, files: string[] = []): string[] => {
    for (const entry of fs.readdirSync(directory, {withFileTypes: true})) {
        if (entry.isDirectory()) {
            if (!IGNORED_DIRECTORIES.has(entry.name)) walk(path.join(directory, entry.name), files)
        } else if (SCANNED_EXTENSIONS.has(path.extname(entry.name))) {
            files.push(path.join(directory, entry.name))
        }
    }
    return files
}

const distance = (a: string, b: string): number => {
    const previous = Array.from({length: b.length + 1}, (_, i) => i)
    for (let i = 1; i <= a.length; i++) {
        let diagonal = previous[0]
        previous[0] = i
        for (let j = 1; j <= b.length; j++) {
            const current = previous[j]
            previous[j] = Math.min(
                previous[j] + 1,
                previous[j - 1] + 1,
                diagonal + (a[i - 1] === b[j - 1] ? 0 : 1),
            )
            diagonal = current
        }
    }
    return previous[b.length]
}

/**
 * Up to three plausible replacements: the recorded rename when there is one, otherwise the closest
 * declared names by edit distance, preferring the ones sharing the longest prefix.
 */
const suggest = (token: string, known: Set<string>): string[] => {
    const retired = RETIRED[token]?.filter(name => known.has(name)) ?? []
    if (retired.length > 0) return retired

    return [...known]
        .map(name => ({name, score: distance(token, name)}))
        .filter(({score}) => score <= Math.max(4, Math.round(token.length / 3)))
        .sort((a, b) => a.score - b.score || b.name.length - a.name.length)
        .slice(0, 3)
        .map(({name}) => name)
}

/**
 * Every `--ks-*` custom property this file declares: plain CSS, SCSS's `#{--name}` form, a quoted
 * key in a JS style object, or a runtime `setProperty` call.
 */
const declarationsIn = (source: string): string[] => [
    ...source.matchAll(/(?:#\{)?(--ks-[a-z0-9-]+)["']?\}?\s*:/gi),
    ...source.matchAll(/setProperty\(\s*["'](--ks-[a-z0-9-]+)["']/gi),
].map(match => match[1].toLowerCase())

/**
 * Reads the token names the Figma palette generator writes, which is the source of truth for every
 * colour token. Non-colour tokens (radii, spacing, font sizes) are not in it and are picked up from
 * their declarations instead.
 */
export const readPaletteTokens = (paletteFile: string): string[] =>
    (JSON.parse(fs.readFileSync(paletteFile, "utf-8")) as string[]).map(name => name.toLowerCase())

/**
 * Flags every `var(--ks-…)` in `roots` whose name is declared nowhere, so a typo or a token retired
 * by a Figma rename is reported instead of being silently dropped by the browser (an unresolvable
 * custom property with no fallback makes the whole declaration invalid, which is invisible until
 * someone measures the computed style).
 *
 * Names built by interpolation (`var(--ks-status-#{$state})`) are skipped: their value is only known
 * at runtime.
 *
 * @param roots       directories to scan, both for usages and for declarations
 * @param paletteFile the generator's `Color-variables.json`
 */
export function checkTokens(roots: string[], paletteFile: string): TokenReport {
    const files = roots.flatMap(root => walk(path.resolve(root)))
    const known = new Set(readPaletteTokens(paletteFile))

    const sources = new Map<string, string>()
    for (const file of files) {
        const source = fs.readFileSync(file, "utf-8")
        sources.set(file, source)
        declarationsIn(source).forEach(name => known.add(name))
    }

    const dead: DeadToken[] = []
    const withFallback: DeadToken[] = []
    let usages = 0

    for (const [file, source] of sources) {
        source.split("\n").forEach((text, index) => {
            for (const match of text.matchAll(/var\(\s*(--ks-[a-z0-9-]*)\s*(,)?/gi)) {
                const token = match[1].toLowerCase()
                // `var(--ks-status-#{$state})` captures the literal prefix and stops at the `#`.
                if (token.endsWith("-") || text.slice(match.index).startsWith(`${match[0]}#{`)) continue

                usages++
                if (known.has(token)) continue

                const finding: DeadToken = {
                    file,
                    line: index + 1,
                    token,
                    hasFallback: Boolean(match[2]),
                    suggestions: suggest(token, known),
                }
                ;(finding.hasFallback ? withFallback : dead).push(finding)
            }
        })
    }

    return {known: known.size, usages, dead, withFallback}
}

/** Prints the report the way the translation gate prints its own, and returns the exit code. */
export function reportTokens(report: TokenReport, cwd = process.cwd()): number {
    const format = (finding: DeadToken) => {
        const where = `${path.relative(cwd, finding.file)}:${finding.line}`
        const hint = finding.suggestions.length > 0
            ? `  did you mean ${finding.suggestions.join(", ")}?`
            : "  no close match; declare it in the palette or pick an existing token"
        return `  ${where}  ${finding.token}${hint}`
    }

    console.log(`Checked ${report.usages} var(--ks-*) usages against ${report.known} declared tokens.`)

    if (report.withFallback.length > 0) {
        console.log(`\n${report.withFallback.length} undeclared token(s) used with a fallback, so they render but the name is probably wrong:`)
        report.withFallback.forEach(finding => console.log(format(finding)))
    }

    if (report.dead.length === 0) {
        console.log("\nNo undeclared tokens.")
        return 0
    }

    console.log(`\n${report.dead.length} undeclared token(s) with no fallback. Each of these declarations is dropped by the browser:`)
    report.dead.forEach(finding => console.log(format(finding)))
    return 1
}

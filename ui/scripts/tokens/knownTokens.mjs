import fs from "node:fs"
import path from "node:path"
import {fileURLToPath} from "node:url"

const UI_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..")
const PALETTE = path.join(UI_ROOT, "packages/design-system/tests/storybook/Basic/Color-variables.json")
// EE points this at `ui-ee/src` so a token it declares is not reported as undeclared there.
const EXTRA_ROOTS = (process.env.KS_TOKEN_ROOTS ?? "").split(path.delimiter).filter(Boolean)
const ROOTS = [path.join(UI_ROOT, "src"), path.join(UI_ROOT, "packages"), ...EXTRA_ROOTS]

const SCANNED_EXTENSIONS = new Set([".vue", ".scss", ".css", ".ts", ".tsx", ".js", ".jsx", ".mjs"])
const IGNORED_DIRECTORIES = new Set(["node_modules", "dist", "coverage", "storybook-static", ".git", "playwright-report"])

// The whole scan is ~45ms over ~1200 files, so a short window keeps an editor's long-lived lint
// process from going stale on a token that was just added, at no cost worth measuring.
const CACHE_TTL_MS = 2_000

/**
 * Names retired by a rename, mapped to what replaced them. A fuzzy match cannot find these on its
 * own: `--ks-primary` is no closer to `--ks-text-link` than to twenty other tokens.
 */
export const RETIRED = {
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
    "--ks-gray-inverted-900": ["--ks-gray-cool-900"],
}

/**
 * Every `--ks-*` custom property a source declares: plain CSS, SCSS's `#{--name}` form, a quoted key
 * in a JS style object, or a runtime `setProperty` call.
 */
export const declarationsIn = (source) => [
    ...source.matchAll(/(?:#\{)?(--ks-[a-z0-9-]+)["']?\}?\s*:/gi),
    ...source.matchAll(/setProperty\(\s*["'](--ks-[a-z0-9-]+)["']/gi),
].map(match => match[1].toLowerCase())

const walk = (directory, files = []) => {
    for (const entry of fs.readdirSync(directory, {withFileTypes: true})) {
        if (entry.isDirectory()) {
            if (!IGNORED_DIRECTORIES.has(entry.name)) walk(path.join(directory, entry.name), files)
        } else if (SCANNED_EXTENSIONS.has(path.extname(entry.name))) {
            files.push(path.join(directory, entry.name))
        }
    }
    return files
}

let cache = null

const scan = () => {
    const known = new Set(JSON.parse(fs.readFileSync(PALETTE, "utf-8")).map(name => name.toLowerCase()))
    for (const root of ROOTS) {
        if (!fs.existsSync(root)) continue
        for (const file of walk(root)) {
            declarationsIn(fs.readFileSync(file, "utf-8")).forEach(name => known.add(name))
        }
    }
    return known
}

/**
 * Every `--ks-*` name that resolves to something: the colour tokens the Figma palette generator
 * writes, plus every custom property declared anywhere under `src/` and `packages/` (radii, spacing,
 * component-scoped knobs, and the handful set from JavaScript).
 */
export const knownTokens = () => {
    if (cache && Date.now() - cache.at < CACHE_TTL_MS) return cache.known
    cache = {known: scan(), at: Date.now()}
    return cache.known
}

const distance = (a, b) => {
    const previous = Array.from({length: b.length + 1}, (_, i) => i)
    for (let i = 1; i <= a.length; i++) {
        let diagonal = previous[0]
        previous[0] = i
        for (let j = 1; j <= b.length; j++) {
            const current = previous[j]
            previous[j] = Math.min(previous[j] + 1, previous[j - 1] + 1, diagonal + (a[i - 1] === b[j - 1] ? 0 : 1))
            diagonal = current
        }
    }
    return previous[b.length]
}

/** Up to three plausible replacements: the recorded rename first, otherwise the nearest names. */
export const suggest = (token, known) => {
    const retired = (RETIRED[token] ?? []).filter(name => known.has(name))
    if (retired.length > 0) return retired

    return [...known]
        .map(name => ({name, score: distance(token, name)}))
        .filter(({score}) => score <= Math.max(4, Math.round(token.length / 3)))
        .sort((a, b) => a.score - b.score || b.name.length - a.name.length)
        .slice(0, 3)
        .map(({name}) => name)
}

/** The message both the stylelint rule and the ESLint rule report, so they read the same. */
export const undeclaredMessage = (token, known) => {
    const suggestions = suggest(token, known)
    return suggestions.length > 0
        ? `"${token}" is not declared: did you mean ${suggestions.join(", ")}? An unresolved custom property is dropped by the browser.`
        : `"${token}" is not declared anywhere, so the declaration is dropped by the browser. Add it to the Figma palette or use an existing token.`
}

/**
 * Every `var(--ks-…)` in a source, with the name and the offset it starts at. Names built by
 * interpolation (`var(--ks-status-#{$state})`) are left out: their value is only known at runtime.
 */
export const usagesIn = function* (source) {
    for (const match of source.matchAll(/var\(\s*(--ks-[a-z0-9-]*)/gi)) {
        const token = match[1].toLowerCase()
        if (token.endsWith("-")) continue
        yield {token, index: match.index + match[0].length - match[1].length}
    }
}

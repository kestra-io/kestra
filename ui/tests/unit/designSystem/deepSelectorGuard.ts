import {readFileSync, readdirSync} from "node:fs"
import {join, relative, sep} from "node:path"

/** `:deep()` and its retired spellings, which reach into another component's markup. */
const DEEP = /:deep\(|::v-deep|\/deep\/|>>>/

const STYLE_BLOCK_OR_CHAR = /(<style\b[^>]*>)([\s\S]*?)(<\/style>)|[^\n]/g

const blank = (text: string): string => text.replace(/[^\n]/g, "")

const sources = (dir: string): string[] =>
    readdirSync(dir, {withFileTypes: true}).flatMap((entry) => {
        const full = join(dir, entry.name)
        if (entry.isDirectory()) return sources(full)
        return /\.(vue|css|scss)$/.test(entry.name) ? [full] : []
    })

/** Keeps a file's styles only, without CSS comments, and keeps every newline so line numbers stay true. */
const styles = (file: string, text: string): string => {
    const css = file.endsWith(".vue")
        ? text.replace(STYLE_BLOCK_OR_CHAR, (_match, open?: string, body?: string, close?: string) => open === undefined ? "" : blank(open) + body + blank(close ?? ""))
        : text
    return css.replace(/\/\*[\s\S]*?\*\//g, blank)
}

/** Returns `path:line` for every deep selector under `dirs`, paths relative to `base` with `/` separators on every OS. */
export const findDeepSelectors = (base: string, dirs: string[]): string[] =>
    dirs.flatMap(sources).flatMap((file) =>
        styles(file, readFileSync(file, "utf8"))
            .split("\n")
            .flatMap((line, i) => (DEEP.test(line) ? [`${relative(base, file).split(sep).join("/")}:${i + 1}`] : [])),
    )

export const countByFile = (hits: string[]): Record<string, number> =>
    hits.reduce<Record<string, number>>((counts, hit) => {
        const file = hit.slice(0, hit.lastIndexOf(":"))
        counts[file] = (counts[file] ?? 0) + 1
        return counts
    }, {})

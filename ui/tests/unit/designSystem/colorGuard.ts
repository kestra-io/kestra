import {readFileSync, readdirSync} from "node:fs"
import {join, relative} from "node:path"

const COLOUR_PROPERTY = /^(?:--[a-z0-9-]*(?:colou?r|bg|background|fill|stroke|border|shadow|tint|ring)[a-z0-9-]*|color|fill|stroke|background|background-color|border|border-(?:top|right|bottom|left)|border-(?:top|right|bottom|left|block|inline)?-?color|outline|outline-color|caret-color|accent-color|scrollbar-color|text-decoration|text-decoration-color|column-rule|column-rule-color|box-shadow|text-shadow)$/

/** Words that may sit in one of those values without being a colour; anything else bare is a keyword colour. */
const CSS_WIDE = "inherit initial unset revert revert-layer none auto transparent currentcolor important"
const LINE = "solid dashed dotted double groove ridge inset outset hidden thin medium thick underline overline line-through wavy"
const BACKGROUND = "repeat no-repeat repeat-x repeat-y space round cover contain fixed local scroll center top bottom left right padding-box border-box content-box text"
const COLOUR_FUNCTION = "in to at from srgb srgb-linear oklab oklch lab lch hwb hue shorter longer increasing decreasing circle ellipse closest-side closest-corner farthest-side farthest-corner"
const NOT_A_COLOUR = new Set(`${CSS_WIDE} ${LINE} ${BACKGROUND} ${COLOUR_FUNCTION}`.split(" "))

const HEX_OR_RGB = /#[0-9a-fA-F]{3,8}\b|rgba?\(/g
const DECLARATION = /(?:^|[;{}])\s*([a-z-]+)\s*:([^;{}]+)/g
const PATTERNS = [
    /(?:color|background|border|outline|fill|stroke|shadow)[a-z-]*\s*:[^;]*#[0-9a-fA-F]{3,8}\b/,
    /--[a-z0-9-]+\s*:\s*#[0-9a-fA-F]{3,8}\b/,
    /(?:fill|stroke)\s*=\s*\\?["']#[0-9a-fA-F]{3,8}/,
    /\brgba?\(\s*\d/,
]

const DISABLE_FILE = /(?:\/\/|\/\*|<!--)[^\n]*design-system-disable(?!-next-line)/
const DISABLE_NEXT_LINE = /(?:\/\/|\/\*|<!--)[^\n]*design-system-disable-next-line/

const sources = (dir: string): string[] =>
    readdirSync(dir, {withFileTypes: true}).flatMap((entry) => {
        const path = join(dir, entry.name)
        if (entry.isDirectory()) return sources(path)
        return /\.(vue|scss|ts)$/.test(entry.name) ? [path] : []
    })

/** Comments blanked in place, keeping every newline so reported line numbers stay real. */
const withoutComments = (source: string): string =>
    source
        .replace(/\/\*[\s\S]*?\*\/|<!--[\s\S]*?-->/g, (comment) => comment.replace(/[^\n]/g, " "))
        .replace(/(^|\s)\/\/.*$/gm, (comment, before: string) => before + " ".repeat(comment.length - before.length))

/** The same source with everything outside a `<style>` block blanked, since `color:` is also object syntax. */
const styleOnly = (source: string, file: string): string => {
    if (file.endsWith(".scss")) return source
    if (!file.endsWith(".vue")) return source.replace(/[^\n]/g, " ")
    return source.replace(/<style[^>]*>[\s\S]*?<\/style>|[^\n]/g, (match) => (match.length > 1 ? match : " "))
}

/**
 * The bare words of a value. Strings, interpolation, variables and urls go, and function names go while
 * their arguments stay, so a colour inside a gradient is still read.
 */
const bareWords = (value: string): string[] =>
    value
        .replace(/"[^"]*"|'[^']*'|#\{[^}]*\}|\$[\w-]+|url\([^)]*\)/g, " ")
        .replace(/[\w-]+\(/g, "(")
        .match(/(?<![\w.#-])[a-z][a-z-]*/gi) ?? []

/** Keyword colours per line, read from whole declarations so a value spanning lines is seen once. */
const keywordColours = (styles: string): Map<number, string[]> => {
    const found = new Map<number, string[]>()
    for (const declaration of styles.matchAll(DECLARATION)) {
        const [match, property, value] = declaration
        if (!COLOUR_PROPERTY.test(property)) continue
        const colours = bareWords(value).filter((word) => !NOT_A_COLOUR.has(word.toLowerCase()))
        if (!colours.length) continue
        const line = styles.slice(0, declaration.index + match.indexOf(property)).split("\n").length - 1
        found.set(line, [...(found.get(line) ?? []), ...colours])
    }
    return found
}

/**
 * Returns `path:line (colours)` for every file hardcoding a colour; empty when clean. A file opts out with
 * a `design-system-disable: reason` comment, a single line with `design-system-disable-next-line`.
 */
export const findHardcodedColors = (srcDir: string): string[] =>
    sources(srcDir).flatMap((file) => {
        const path = relative(srcDir, file).replaceAll("\\", "/")
        const raw = readFileSync(file, "utf8")
        if (DISABLE_FILE.test(raw)) return []
        const muted = new Set(raw.split("\n").flatMap((line, index) => (DISABLE_NEXT_LINE.test(line) ? [index + 1] : [])))

        const source = withoutComments(raw)
        const keywords = keywordColours(styleOnly(source, file))
        const offenders = source
            .split("\n")
            .flatMap((line, index) =>
                !muted.has(index) && (PATTERNS.some((pattern) => pattern.test(line)) || keywords.has(index))
                    ? [{line, index}]
                    : [])
        if (!offenders.length) return []

        const colours = new Set(offenders.flatMap(({line, index}) =>
            [...line.match(HEX_OR_RGB) ?? [], ...keywords.get(index) ?? []]))
        return [`${path}:${offenders[0].index + 1} (${[...colours].join(", ")})`]
    })

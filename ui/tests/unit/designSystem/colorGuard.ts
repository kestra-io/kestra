import {readFileSync, readdirSync} from "node:fs"
import {join, relative} from "node:path"

const COLOUR_PROPERTY = /^(?:--[a-z0-9-]*(?:colou?r|bg|background|fill|stroke|border|shadow|tint|ring)[a-z0-9-]*|color|fill|stroke|background|background-color|border|border-(?:top|right|bottom|left)|border-(?:top|right|bottom|left|block|inline)?-?color|outline|outline-color|caret-color|accent-color|scrollbar-color|text-decoration|text-decoration-color|column-rule|column-rule-color|box-shadow|text-shadow)$/

/** Words that may sit in one of those values without being a colour; anything else bare is a keyword colour. */
const CSS_WIDE = "inherit initial unset revert revert-layer none auto transparent currentcolor important"
const LINE = "solid dashed dotted double groove ridge inset outset hidden thin medium thick underline overline line-through wavy"
const BACKGROUND = "repeat no-repeat repeat-x repeat-y space round cover contain fixed local scroll center top bottom left right padding-box border-box content-box text"
const COLOUR_FUNCTION = "in to at from srgb srgb-linear oklab oklch lab lch hwb hue shorter longer increasing decreasing circle ellipse closest-side closest-corner farthest-side farthest-corner"
const NOT_A_COLOUR = new Set(`${CSS_WIDE} ${LINE} ${BACKGROUND} ${COLOUR_FUNCTION}`.split(" "))

const FOREIGN_TOKEN = /var\(\s*--(?:k?el|bs)-[\w-]+/g
const SCSS_VARIABLE = /\$[\w-]+/g
const HEX_OR_FUNCTION = /#[0-9a-fA-F]{3,8}\b|\b(?:rgba?|hsla?|hwb|lab|lch|oklab|oklch)\(/g
const DECLARATION = /(?:^|[;{}])\s*([a-z-]+)\s*:([^;{}]+)/g
const PATTERNS = [
    /(?:color|background|border|outline|fill|stroke|shadow)[a-zA-Z-]*\s*:[^;]*#[0-9a-fA-F]{3,8}\b/,
    /(?:--|\$)[\w-]+\s*:\s*#[0-9a-fA-F]{3,8}\b/,
    /(?:fill|stroke)\s*=\s*\\?["']#[0-9a-fA-F]{3,8}/,
    /\b(?:rgba?|hsla?|hwb|lab|lch|oklab|oklch)\(\s*[\d.]/,
]

const DISABLE_NEXT_LINE = /(?:\/\/|\/\*|<!--)[^\n]*design-system-disable-next-line/
const DISABLE_START = /(?:\/\/|\/\*|<!--)[^\n]*design-system-disable-start/
const DISABLE_END = /(?:\/\/|\/\*|<!--)[^\n]*design-system-disable-end/

const sources = (dir: string): string[] =>
    readdirSync(dir, {withFileTypes: true}).flatMap((entry) => {
        const path = join(dir, entry.name)
        if (entry.isDirectory()) return sources(path)
        return /\.(vue|s?css|[jt]s)$/.test(entry.name) ? [path] : []
    })

/** Comments blanked in place, keeping every newline so reported line numbers stay real. */
const withoutComments = (source: string): string =>
    source
        .replace(/\/\*[\s\S]*?\*\/|<!--[\s\S]*?-->/g, (comment) => comment.replace(/[^\n]/g, " "))
        .replace(/(^|\s)\/\/.*$/gm, (comment, before: string) => before + " ".repeat(comment.length - before.length))

/** The same source with everything outside a `<style>` block blanked, since `color:` is also object syntax. */
const styleOnly = (source: string, file: string): string => {
    if (/\.s?css$/.test(file)) return source
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

/** SCSS variables the file declares as an alias of a token, which carry no colour of their own. */
const tokenAliases = (styles: string): Set<string> =>
    new Set([...styles.matchAll(/(\$[\w-]+)\s*:\s*var\(\s*--ks-[\w-]+/g)].map(([, name]) => name))

/** Keyword colours per line, read from whole declarations so a value spanning lines is seen once. */
const keywordColours = (styles: string): Map<number, string[]> => {
    const found = new Map<number, string[]>()
    const aliases = tokenAliases(styles)
    for (const declaration of styles.matchAll(DECLARATION)) {
        const [match, property, value] = declaration
        if (!COLOUR_PROPERTY.test(property)) continue
        const colours = [
            ...bareWords(value).filter((word) => !NOT_A_COLOUR.has(word.toLowerCase())),
            ...(value.match(SCSS_VARIABLE) ?? []).filter((name) => !aliases.has(name)),
            ...value.match(FOREIGN_TOKEN) ?? [],
        ]
        if (!colours.length) continue
        const line = styles.slice(0, declaration.index + match.indexOf(property)).split("\n").length - 1
        found.set(line, [...(found.get(line) ?? []), ...colours])
    }
    return found
}

/**
 * Returns `path:line (colours)` for every line hardcoding a colour; empty when clean. A line opts out with
 * `design-system-disable-next-line` on the line before it, and a run of lines with `design-system-disable-start`
 * before and `design-system-disable-end` after it; a start with no end is reported instead of muting the rest.
 * Callers pass feature roots only: `ui/packages/design-system` composes the tokens from raw palette values,
 * so scanning it would report the palette itself.
 */
export const findHardcodedColors = (srcDir: string): string[] =>
    sources(srcDir).flatMap((file) => {
        const path = relative(srcDir, file).replaceAll("\\", "/")
        const raw = readFileSync(file, "utf8")
        const muted = new Set<number>()
        let blockStart: number | undefined
        raw.split("\n").forEach((line, index) => {
            if (DISABLE_END.test(line)) blockStart = undefined
            else if (DISABLE_START.test(line)) blockStart = index
            if (blockStart !== undefined) muted.add(index)
            if (DISABLE_NEXT_LINE.test(line)) muted.add(index + 1)
        })
        const unterminated = blockStart === undefined
            ? []
            : [`${path}:${blockStart + 1} (design-system-disable-start without design-system-disable-end)`]

        const source = withoutComments(raw)
        const keywords = keywordColours(styleOnly(source, file))
        const offenders = source.split("\n").flatMap((line, index) => {
            if (muted.has(index) || !(PATTERNS.some((pattern) => pattern.test(line)) || keywords.has(index))) return []
            const colours = new Set([...line.match(HEX_OR_FUNCTION) ?? [], ...keywords.get(index) ?? []])
            return [`${path}:${index + 1} (${[...colours].join(", ")})`]
        })
        return [...unterminated, ...offenders]
    })

import {pebbleBlocks} from "./pebble.ts"
import {pebbleColors} from "./themes/index.ts"

type ThemeInput = string | {name?: string} | undefined

interface Token {
    content: string
    offset: number
    color?: string
    htmlStyle?: Record<string, string>
}

interface TransformerContext {
    source: string
    options: {
        lang: string
        theme?: ThemeInput
        themes?: Partial<Record<string, ThemeInput>>
        defaultColor?: string | false
        cssVariablePrefix?: string
        colorsRendering?: string
    }
}

/** Structural subset of Shiki's `ShikiTransformer`. */
export interface PebbleTransformer {
    name: string
    tokens(this: TransformerContext, tokens: Token[][]): Token[][] | undefined
}

const YAML = new Set(["yaml", "yml"])

const nameOf = (theme: ThemeInput) => typeof theme === "string" ? theme : theme?.name

/** The style Shiki would give a token colored with each theme's Pebble color, or undefined for a non-Kestra theme. */
function pebbleStyle({theme, themes, defaultColor = "light", cssVariablePrefix = "--shiki-", colorsRendering = "css-vars"}: TransformerContext["options"]): Partial<Token> | undefined {
    if (!themes) {
        const color = pebbleColors.get(nameOf(theme))
        return color ? {color} : undefined
    }
    const variants = Object.entries(themes).filter(([, value]) => value)
        .sort(([a], [b]) => a === defaultColor ? -1 : b === defaultColor ? 1 : 0)
        .map(([variant, value]) => [variant, pebbleColors.get(nameOf(value))] as const)
    if (variants.some(([, color]) => !color)) return undefined
    const colors = Object.fromEntries(variants)
    const htmlStyle: Record<string, string> = {}
    variants.forEach(([variant, color], index) => {
        if (index === 0 && defaultColor) {
            htmlStyle.color = defaultColor === "light-dark()" && variants.length > 1 ? `light-dark(${colors.light}, ${colors.dark})` : color!
            if (defaultColor !== "light-dark()" || variants.length < 2 || colorsRendering !== "css-vars") return
        }
        if (colorsRendering === "css-vars") htmlStyle[cssVariablePrefix + variant] = color!
    })
    return {htmlStyle}
}

/**
 * Splits highlighted lines at the Pebble blocks of `code` and passes every piece inside a block through `paint`.
 * Works on any token carrying its `offset` into `code`, such as Shiki's `ThemedToken` and `ThemedTokenWithVariants`.
 */
export function paintPebble<T extends {content: string, offset: number}>(code: string, lines: T[][], paint: (token: T) => T): T[][] {
    const blocks = pebbleBlocks(code)
    if (!blocks.length) return lines
    const slice = (token: T, from: number, to: number): T =>
        ({...token, content: token.content.slice(from - token.offset, to - token.offset), offset: from})
    let next = 0
    return lines.map(line => line.flatMap(token => {
        const end = token.offset + token.content.length
        while (next < blocks.length && blocks[next]![1] <= token.offset) next++
        if (next === blocks.length || blocks[next]![0] >= end) return [token]
        const pieces: T[] = []
        let cursor = token.offset
        for (let b = next; cursor < end && b < blocks.length && blocks[b]![0] < end; b++) {
            const [start, stop] = blocks[b]!
            if (start > cursor) pieces.push(slice(token, cursor, start))
            cursor = Math.max(cursor, start)
            pieces.push(paint(slice(token, cursor, Math.min(end, stop))))
            cursor = Math.min(end, stop)
        }
        if (cursor < end) pieces.push(slice(token, cursor, end))
        return pieces
    }))
}

/**
 * Shiki transformer that paints Pebble blocks in YAML with the Kestra theme's Pebble color, after tokenization and
 * in linear time, so it costs next to nothing on top of highlighting. Other languages and themes pass through.
 */
export function pebbleTransformer(): PebbleTransformer {
    return {
        name: "kestra:pebble",
        tokens(lines) {
            if (!YAML.has(this.options.lang)) return undefined
            const style = pebbleStyle(this.options)
            if (!style) return undefined

            return paintPebble(this.source, lines, token => style.htmlStyle
                ? {...token, htmlStyle: {...token.htmlStyle, ...style.htmlStyle}}
                : {...token, color: style.color})
        },
    }
}

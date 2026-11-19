import {pebbleTokens, type PebbleRole} from "./pebble.ts"
import {syntaxColors} from "./themes/index.ts"
import type {SyntaxColors} from "./types.ts"

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

/** The style Shiki would give a token of `role` under each theme, or undefined when one of them is not a Kestra theme. */
function pebbleStyle({theme, themes, defaultColor = "light", cssVariablePrefix = "--shiki-", colorsRendering = "css-vars"}: TransformerContext["options"]): ((role: PebbleRole) => Partial<Token>) | undefined {
    if (!themes) {
        const syntax = syntaxColors.get(nameOf(theme))
        return syntax && (role => ({color: syntax[role]}))
    }
    const variants = Object.entries(themes).filter(([, value]) => value)
        .sort(([a], [b]) => a === defaultColor ? -1 : b === defaultColor ? 1 : 0)
        .map(([variant, value]) => [variant, syntaxColors.get(nameOf(value))] as const)
    if (variants.some(([, syntax]) => !syntax)) return undefined
    const colors = Object.fromEntries(variants) as Record<string, SyntaxColors>
    return role => {
        const htmlStyle: Record<string, string> = {}
        variants.forEach(([variant, syntax], index) => {
            if (index === 0 && defaultColor) {
                htmlStyle.color = defaultColor === "light-dark()" && variants.length > 1 ? `light-dark(${colors.light![role]}, ${colors.dark![role]})` : syntax![role]
                if (defaultColor !== "light-dark()" || variants.length < 2 || colorsRendering !== "css-vars") return
            }
            if (colorsRendering === "css-vars") htmlStyle[cssVariablePrefix + variant] = syntax![role]
        })
        return {htmlStyle}
    }
}

/**
 * Splits highlighted lines at the Pebble tokens of `code` and passes every piece inside a block through `paint`
 * with the role `pebbleTokens` gave it. Works on any token carrying its `offset` into `code`, such as Shiki's
 * `ThemedToken` and `ThemedTokenWithVariants`.
 */
export function paintPebble<T extends {content: string, offset: number}>(code: string, lines: T[][], paint: (token: T, role: PebbleRole) => T): T[][] {
    const pebble = pebbleTokens(code)
    if (!pebble.length) return lines
    const slice = (token: T, from: number, to: number): T =>
        ({...token, content: token.content.slice(from - token.offset, to - token.offset), offset: from})
    let next = 0
    return lines.map(line => line.flatMap(token => {
        const end = token.offset + token.content.length
        while (next < pebble.length && pebble[next]![1] <= token.offset) next++
        if (next === pebble.length || pebble[next]![0] >= end) return [token]
        const pieces: T[] = []
        let cursor = token.offset
        for (let b = next; cursor < end && b < pebble.length && pebble[b]![0] < end; b++) {
            const [start, stop, role] = pebble[b]!
            if (start > cursor) pieces.push(slice(token, cursor, start))
            cursor = Math.max(cursor, start)
            const to = Math.min(end, stop)
            pieces.push(paint(cursor === token.offset && to === end ? token : slice(token, cursor, to), role))
            cursor = to
        }
        if (cursor < end) pieces.push(slice(token, cursor, end))
        return pieces
    }))
}

/**
 * Shiki transformer that paints Pebble blocks in YAML with the Kestra theme's colors, one per token role, after
 * tokenization and in linear time, so it costs next to nothing on top of highlighting. Other languages and themes
 * pass through.
 */
export function pebbleTransformer(): PebbleTransformer {
    return {
        name: "kestra:pebble",
        tokens(lines) {
            if (!YAML.has(this.options.lang)) return undefined
            const styleOf = pebbleStyle(this.options)
            if (!styleOf) return undefined

            const styles = new Map<PebbleRole, Partial<Token>>()
            return paintPebble(this.source, lines, (token, role) => {
                let style = styles.get(role)
                if (!style) styles.set(role, style = styleOf(role))
                return style.htmlStyle
                    ? {...token, htmlStyle: {...token.htmlStyle, ...style.htmlStyle}}
                    : {...token, color: style.color}
            })
        },
    }
}

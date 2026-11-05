import {beforeAll, describe, expect, it} from "vitest"
import {TokenMetadata} from "monaco-editor/editor/common/encodedTokenAttributes.js"
import {TokenTheme} from "monaco-editor/editor/common/languages/supports/tokenization.js"
import {kestraDark, themes, toMonacoTheme} from "./index.ts"
import type {CodeTheme, SyntaxRole} from "./index.ts"
import {monacoEntry} from "../tests/kseditor.ts"
import {monarchTokens} from "../tests/monarch.ts"

const FONT_BITS = {italic: 1, bold: 2, underline: 4} as const

/** Resolves tokens the way Monaco paints them, through its own theme trie. */
function resolver(theme: CodeTheme) {
    const monaco = TokenTheme.createFromRawTokenTheme(toMonacoTheme(theme).rules, [])
    const colorMap = monaco.getColorMap()
    return (token: string) => {
        const metadata = monaco.match(0, token)
        return {color: colorMap[TokenMetadata.getForeground(metadata)]!.toString().toUpperCase(), fontStyle: TokenMetadata.getFontStyle(metadata)}
    }
}

let tokens: Map<string, Set<string>>

beforeAll(async () => {
    tokens = await monarchTokens()
})

describe("Monaco themes", () => {
    it("harvests every grammar bundled with monaco-editor", () => {
        expect(tokens.size).toBeGreaterThanOrEqual(80)
        expect(tokens.get("yaml")).toContain("type.yaml")
    })

    it("maps every token a bundled grammar can emit to a deliberate rule", () => {
        const unmapped = [...tokens].flatMap(([language, set]) => [...set].filter(t => !monacoEntry(t)).map(t => `${language}: ${t}`))
        expect(unmapped).toEqual([])
    })

    it.each(themes.map(theme => [theme.name, theme] as const))("%s resolves every token to its role in Monaco's own matcher", (_, theme) => {
        const resolve = resolver(theme)
        const wrong: string[] = []
        for (const token of new Set([...tokens.values()].flatMap(set => [...set]))) {
            const [, mapping] = monacoEntry(token)!
            const role = (Array.isArray(mapping) ? mapping[0] : mapping in theme.syntax ? mapping : "text") as SyntaxRole
            const style = Array.isArray(mapping) ? mapping[1] : mapping in FONT_BITS ? mapping as keyof typeof FONT_BITS : undefined
            const {color, fontStyle} = resolve(token)
            if (color !== theme.syntax[role].toUpperCase() || fontStyle !== (style ? FONT_BITS[style] : 0)) {
                wrong.push(`${token}: ${color}/${fontStyle}, expected ${role} ${theme.syntax[role]}/${style ?? "none"}`)
            }
        }
        expect(wrong).toEqual([])
    })

    it("never inherits rules or colors from Monaco's built-in themes", () => {
        expect(themes.map(theme => toMonacoTheme(theme).inherit)).not.toContain(true)
    })

    it.each([
        ["type.yaml", "key"],
        ["string.yaml", "value"],
        ["string.invalid.yaml", "value"],
        ["keyword.yaml", "constant"],
        ["operators.yaml", "punctuation"],
        ["number.float.yaml", "number"],
        ["comment.yaml", "comment"],
        ["namespace.yaml", "namespace"],
        ["string.escape.yaml", "escape"],
        ["string.key.json", "key"],
        ["string.value.json", "value"],
        ["keyword.json", "constant"],
        ["delimiter.twig", "pebble"],
        ["keyword.python", "keyword"],
        ["string.sql", "string"],
        ["predefined.sql", "builtin"],
        ["variable.shell", "variable"],
    ] as const)("gives %s the %s role", (token, role) => {
        expect(resolver(kestraDark)(token).color).toBe(kestraDark.syntax[role].toUpperCase())
    })

    it("renders a Kestra flow with the Dark 2.0 design colors", () => {
        const resolve = resolver(kestraDark)
        expect({
            key: resolve("type.yaml").color,
            value: resolve("string.yaml").color,
            colon: resolve("operators.yaml").color,
            boolean: resolve("keyword.yaml").color,
            pebble: kestraDark.syntax.pebble,
            lineNumbers: kestraDark.editor.lineNumber,
        }).toEqual({
            key: "#CDB6FB",
            value: "#F7F7F8",
            colon: "#ADADBF",
            boolean: "#06F0B1",
            pebble: "#80FAC5",
            lineNumbers: "#ADADBF",
        })
    })
})

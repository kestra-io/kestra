import {editorColors} from "./editorColors.ts"
import type {CodeTheme, FontStyle, SyntaxRole} from "./types.ts"

/** Structural copy of `monaco.editor.IStandaloneThemeData`, so the package does not depend on monaco-editor. */
export interface MonacoThemeData {
    base: "vs" | "vs-dark" | "hc-black" | "hc-light"
    inherit: boolean
    rules: {token: string, foreground?: string, background?: string, fontStyle?: string}[]
    colors: Record<string, string>
}

type Mapping = SyntaxRole | [SyntaxRole, FontStyle] | FontStyle

/**
 * Monarch token prefixes to roles. Monaco picks the longest dot-prefix match, so a language-suffixed entry
 * (`type.yaml`) overrides its generic parent (`type`). The keys cover every token emitted by the 81 grammars
 * bundled with monaco-editor, as harvested by `test/monaco.test.ts`.
 */
export const MONACO_SCOPES: [string, Mapping][] = [
    ["comment", ["comment", "italic"]],

    ["string", "string"],
    ["string.escape", "escape"],
    ["string.link", ["link", "underline"]],
    ["string.target", "link"],
    ["literal", "string"],

    ["number", "number"],
    ["constant", "constant"],
    ["constants", "constant"],
    ["global.constant", "constant"],
    ["predefined.literal", "constant"],

    ["keyword", "keyword"],
    ["operator", "operator"],
    ["operators", "operator"],
    ["wildcard", "operator"],

    ["delimiter", "punctuation"],
    ["delimeter", "punctuation"], // Monaco's Swift grammar emits this misspelling. codespell:ignore delimeter
    ["punctuation", "punctuation"],
    ["identifier.quote", "punctuation"],
    ["attribute.delimiter", "punctuation"],

    ["identifier", "text"],
    ["source", "text"],
    ["white", "text"],

    ["type", "type"],
    ["constructor", "type"],
    ["function", "function"],
    ["variable", "variable"],
    ["argument", "variable"],
    ["variable.predefined", "builtin"],
    ["variable.language", "builtin"],
    ["predefined", "builtin"],
    ["global", "builtin"],
    ["sigil", "builtin"],

    ["tag", "tag"],
    ["tag.id", "attribute"],
    ["tag.class", "attribute"],
    ["attribute", "attribute"],
    ["attribute.value", "string"],
    ["attribute.value.number", "number"],
    ["attribute.value.unit", "number"],
    ["attribute.value.hex", "number"],

    ["annotation", "annotation"],
    ["metatag", "annotation"],
    ["metatag.content", "string"],
    ["meta", "annotation"],
    ["directive", "annotation"],

    ["regexp", "regexp"],
    ["regexp.escape", "escape"],
    ["regexp.invalid", "invalid"],
    ["escape", "escape"],
    ["interpolation", "escape"],

    ["namespace", "namespace"],
    ["key", "key"],
    ["invalid", "invalid"],
    ["error-token", "invalid"],
    ["emphasis", "italic"],
    ["strong", "bold"],

    ["type.yaml", "key"],
    ["string.yaml", "value"],
    ["string.invalid.yaml", "value"],
    ["keyword.yaml", "constant"],
    ["operators.yaml", "punctuation"],
    ["operators.directivesEnd", "punctuation"],
    ["operators.documentEnd", "punctuation"],
    ["meta.directive.yaml", "annotation"],
    ["tag.yaml", "annotation"],

    ["string.key.json", "key"],
    ["string.value.json", "value"],
    ["keyword.json", "constant"],

    ["keyword.md", ["heading", "bold"]],
    ["keyword.table", "punctuation"],
    ["meta.separator.md", "punctuation"],
    ["variable.md", "string"],
    ["variable.source", "string"],

    ["delimiter.twig", "pebble"],
    ["delimiter.output.liquid", "pebble"],
    ["delimiter.tag.liquid", "pebble"],
    ["delimiter.handlebars", "pebble"],
]

/** The scopes of MONACO_SCOPES in a fixed order, usable as a semantic tokens legend. */
export const MONACO_SCOPE_NAMES = MONACO_SCOPES.map(([scope]) => scope)

const SCOPE_INDEX = new Map(MONACO_SCOPE_NAMES.map((scope, index) => [scope, index]))

/** The index in MONACO_SCOPE_NAMES of the scope a Monaco token type resolves to, picked the way Monaco's theme does. */
export function monacoScopeIndex(tokenType: string): number | undefined {
    const parts = tokenType.split(".")
    for (let length = parts.length; length > 0; length--) {
        const index = SCOPE_INDEX.get(parts.slice(0, length).join("."))
        if (index !== undefined) return index
    }
    return undefined
}

const BASES = {
    dark: {regular: "vs-dark", highContrast: "hc-black"},
    light: {regular: "vs", highContrast: "hc-light"},
} as const

const hex = (color: string) => color.replace("#", "").toUpperCase()

/** Builds a self-contained Monaco theme: `inherit` is off, so no `vs`/`vs-dark` rule can leak through. */
export function toMonacoTheme(theme: CodeTheme): MonacoThemeData {
    const {syntax, editor} = theme
    const rules: MonacoThemeData["rules"] = [{token: "", foreground: hex(syntax.text), background: hex(editor.background)}]
    for (const [token, mapping] of MONACO_SCOPES) {
        const [role, fontStyle] = typeof mapping === "string"
            ? (mapping in syntax ? [mapping as SyntaxRole, undefined] : [undefined, mapping as FontStyle])
            : mapping
        rules.push({
            token,
            ...(role ? {foreground: hex(syntax[role])} : {}),
            ...(fontStyle ? {fontStyle} : {}),
        })
    }
    return {
        base: BASES[theme.kind][theme.highContrast ? "highContrast" : "regular"],
        inherit: false,
        rules,
        colors: editorColors(theme),
    }
}

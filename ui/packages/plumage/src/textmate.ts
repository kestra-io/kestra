import {editorColors} from "./editorColors.ts"
import type {CodeTheme, FontStyle, SyntaxRole} from "./types.ts"

/** VS Code color theme, the format Shiki, Expressive Code and VS Code all read. */
export interface TextMateTheme {
    name: string
    displayName: string
    type: "dark" | "light"
    fg: string
    bg: string
    colors: Record<string, string>
    tokenColors: {scope?: string[], settings: {foreground?: string, background?: string, fontStyle?: string}}[]
}

/**
 * TextMate scopes to roles. Theme matching prefers the deepest matching scope, then the longest selector, so an
 * injected Pebble block (`meta.embedded.expression.pebble`) wins over the YAML string around it.
 */
export const TEXTMATE_SCOPES: [string[], SyntaxRole, FontStyle?][] = [
    [["comment", "punctuation.definition.comment", "string.comment"], "comment", "italic"],
    [["string", "punctuation.definition.string", "markup.inline.raw", "markup.fenced_code.block", "markup.raw"], "string"],
    [["constant.character", "constant.character.escape", "constant.other.placeholder", "string.regexp constant.character.escape"], "escape"],
    [["constant.numeric", "constant.other.numeric", "keyword.other.unit", "constant.other.timestamp.yaml"], "number"],
    [["constant", "constant.language", "constant.other.boolean", "support.constant", "variable.other.constant", "variable.other.enummember"], "constant"],
    [["keyword", "storage", "storage.type", "storage.modifier", "keyword.control", "keyword.other"], "keyword"],
    [["keyword.operator", "keyword.operator.assignment", "keyword.operator.logical", "punctuation.accessor", "keyword.operator.pipe.shell", "keyword.operator.redirect.shell"], "operator"],
    [["punctuation", "meta.brace", "punctuation.separator", "punctuation.terminator", "punctuation.section", "punctuation.definition.tag", "punctuation.definition.list.begin.markdown", "punctuation.definition.block.sequence.item.yaml", "punctuation.separator.key-value.mapping.yaml", "punctuation.definition.document.yaml", "entity.other.document.begin.yaml", "entity.other.document.end.yaml", "keyword.control.flow.block-scalar", "storage.modifier.chomping-indicator.yaml"], "punctuation"],
    [["entity.name.type", "entity.name.class", "entity.other.inherited-class", "support.type", "support.class", "storage.type.java", "storage.type.generic.java", "entity.name.type.module"], "type"],
    [["entity.name.function", "support.function", "variable.function", "meta.function-call.generic", "entity.name.command", "support.function.command"], "function"],
    [["variable", "variable.other", "meta.definition.variable", "variable.other.readwrite"], "variable"],
    [["variable.parameter"], "parameter"],
    [["support.variable", "variable.language", "support.function.builtin", "support.function.builtin.shell", "variable.other.special", "variable.parameter.positional", "support.type.builtin"], "builtin"],
    [["entity.name.tag", "meta.tag.sgml"], "tag"],
    [["entity.other.attribute-name"], "attribute"],
    [["meta.decorator", "meta.annotation", "storage.type.annotation", "punctuation.definition.annotation", "entity.name.function.decorator", "meta.directive", "keyword.other.directive", "entity.name.tag.directive.yaml", "storage.type.tag-handle.yaml"], "annotation"],
    [["string.regexp"], "regexp"],
    [["entity.name.namespace", "entity.name.package", "storage.modifier.package", "storage.modifier.import", "entity.name.type.anchor.yaml", "variable.other.alias.yaml", "punctuation.definition.anchor.yaml", "punctuation.definition.alias.yaml", "keyword.control.flow.alias.yaml", "keyword.control.flow.anchor.yaml"], "namespace"],
    [["markup.heading", "entity.name.section", "punctuation.definition.heading", "markup.heading.setext"], "heading", "bold"],
    [["markup.underline.link", "string.other.link", "markup.link"], "link", "underline"],
    [["markup.quote"], "comment", "italic"],
    [["markup.deleted", "punctuation.definition.deleted"], "invalid"],
    [["markup.inserted", "punctuation.definition.inserted"], "constant"],
    [["markup.changed"], "number"],
    [["invalid", "invalid.illegal", "invalid.deprecated"], "invalid"],

    [["entity.name.tag.yaml", "support.type.property-name", "meta.object-literal.key", "keyword.other.definition.ini", "entity.name.tag.toml", "variable.other.key.toml", "support.type.property-name.json", "variable.declaration.hcl", "variable.other.declaration.hcl", "entity.name.label"], "key"],
    [["string.unquoted.plain.out.yaml", "string.unquoted.plain.in.yaml", "string.quoted.single.yaml", "string.quoted.double.yaml", "string.unquoted.block.yaml", "string.quoted.double.json", "punctuation.definition.string.begin.yaml", "punctuation.definition.string.end.yaml", "punctuation.definition.string.begin.json", "punctuation.definition.string.end.json"], "value"],
    [["constant.language.boolean.yaml", "constant.language.null.yaml", "constant.language.json"], "constant"],

    [["meta.embedded.expression.pebble", "meta.embedded.statement.pebble", "variable.other.pebble", "punctuation.section.tag.twig"], "pebble"],
]

const FONT_STYLES: [string[], FontStyle][] = [
    [["markup.italic", "emphasis"], "italic"],
    [["markup.bold", "strong"], "bold"],
]

/** Builds a VS Code color theme. Shiki and Expressive Code accept the returned object as is. */
export function toTextMateTheme(theme: CodeTheme): TextMateTheme {
    const {syntax, editor} = theme
    return {
        name: theme.name,
        displayName: theme.displayName,
        type: theme.kind,
        fg: syntax.text,
        bg: editor.background,
        colors: editorColors(theme),
        tokenColors: [
            {settings: {foreground: syntax.text, background: editor.background}},
            ...TEXTMATE_SCOPES.map(([scope, role, fontStyle]) => ({
                scope,
                settings: {foreground: syntax[role], ...(fontStyle ? {fontStyle} : {})},
            })),
            ...FONT_STYLES.map(([scope, fontStyle]) => ({scope, settings: {fontStyle}})),
        ],
    }
}

export type ThemeKind = "dark" | "light"

/** One color per syntax role. Every adapter maps grammar scopes onto these roles, so a theme is only this table. */
export interface SyntaxColors {
    text: string
    comment: string
    punctuation: string
    /** Mapping keys in YAML, JSON, INI and properties files. */
    key: string
    /** Plain and quoted scalars in data languages (YAML, JSON). Code strings use `string`. */
    value: string
    /** Pebble `{{ }}` and `{% %}` blocks. */
    pebble: string
    /** `true`, `false`, `null` and language constants. */
    constant: string
    number: string
    string: string
    escape: string
    keyword: string
    operator: string
    type: string
    function: string
    /** Function parameters and named arguments, such as `table=` in a Python call. */
    parameter: string
    variable: string
    builtin: string
    tag: string
    attribute: string
    annotation: string
    regexp: string
    /** YAML anchors and aliases, code namespaces and packages. */
    namespace: string
    heading: string
    link: string
    invalid: string
}

export type SyntaxRole = keyof SyntaxColors

/** Editor surface colors. Values may carry an alpha channel (`#RRGGBBAA`). */
export interface EditorColors {
    background: string
    lineHighlight: string
    selection: string
    selectionInactive: string
    selectionHighlight: string
    findMatch: string
    findMatchHighlight: string
    cursor: string
    lineNumber: string
    lineNumberActive: string
    indentGuide: string
    indentGuideActive: string
    whitespace: string
    bracketMatch: string
    widget: string
    widgetBorder: string
    scrollbar: string
    scrollbarHover: string
    inserted: string
    removed: string
    error: string
    warning: string
    info: string
}

export interface CodeTheme {
    name: string
    displayName: string
    kind: ThemeKind
    highContrast: boolean
    syntax: SyntaxColors
    editor: EditorColors
}

export type FontStyle = "italic" | "bold" | "underline"

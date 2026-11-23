import * as monaco from "monaco-editor/editor/editor.api"
import {typescriptDefaults, ScriptTarget} from "monaco-editor/languages/features/typescript/register"
import {kestraDark, kestraLight, themes, toMonacoTheme, type CodeTheme} from "@kestra-io/plumage"

export const OVERFLOW_WIDGETS_ID = "ks-monaco-overflow-widgets"

/** The names KsEditor took before `@kestra-io/plumage`, kept for callers that still pass `"dark"`, `"light"` or Monaco's `"vs"` / `"vs-dark"`. */
const LEGACY_THEMES: Record<string, CodeTheme> = {light: kestraLight, dark: kestraDark, vs: kestraLight, "vs-dark": kestraDark}

/** The code theme behind a KsEditor `theme` name, falling back to Kestra Dark like the prop default. */
export function codeThemeOf(name: string): CodeTheme {
    return themes.find(theme => theme.name === name) ?? LEGACY_THEMES[name] ?? kestraDark
}

export function registerMonacoThemes(): void {
    for (const theme of themes) {
        monaco.editor.defineTheme(theme.name, toMonacoTheme(theme))
    }
    for (const [name, theme] of Object.entries(LEGACY_THEMES)) {
        monaco.editor.defineTheme(name, toMonacoTheme(theme))
    }
}

export function configureMonacoTypescript(): void {
    typescriptDefaults.setCompilerOptions({
        target: ScriptTarget.ES2020,
        lib: ["es2020"],
        allowNonTsExtensions: true,
    })
}

export function getOrCreateOverflowWidgetsDomNode(): HTMLDivElement {
    let node = document.getElementById(OVERFLOW_WIDGETS_ID) as HTMLDivElement | null
    if (!node) {
        node = document.createElement("div")
        node.id = OVERFLOW_WIDGETS_ID
        node.className = "monaco-editor"
        document.body.appendChild(node)
    }
    return node
}

export function editorModelUid(): string {
    return Math.random().toString(36).slice(2, 11)
}

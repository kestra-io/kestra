import * as monaco from "monaco-editor/esm/vs/editor/editor.api"

export const OVERFLOW_WIDGETS_ID = "ks-monaco-overflow-widgets"

const THEMES: Record<string, monaco.editor.IStandaloneThemeData> = {
    dark: {
        base: "vs-dark",
        inherit: true,
        rules: [{token: "", background: "161822"}],
        colors: {
            "minimap.background": "#161822",
            "diffEditor.insertedLineBackground": "#029E734D",
        },
    },
    light: {
        base: "vs",
        inherit: true,
        rules: [
            {token: "type", foreground: "#8405FF"},
            {token: "string.yaml", foreground: "#001233"},
            {token: "comment", foreground: "#8d99ae", fontStyle: "italic"},
        ],
        colors: {
            "editor.lineHighlightBackground": "#fbfaff",
            "editorLineNumber.foreground": "#444444",
            "editor.selectionBackground": "#E8E5FF",
            "editor.wordHighlightBackground": "#E8E5FF",
            "diffEditor.insertedLineBackground": "#029E734D",
        },
    },
}

export function registerMonacoThemes(): void {
    Object.entries(THEMES).forEach(([themeKey, themeData]) => {
        monaco.editor.defineTheme(themeKey, themeData)
    })
}

export function configureMonacoTypescript(): void {
    if (!monaco.languages.typescript) return
    monaco.languages.typescript.typescriptDefaults.setCompilerOptions({
        target: monaco.languages.typescript.ScriptTarget.ES2020,
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

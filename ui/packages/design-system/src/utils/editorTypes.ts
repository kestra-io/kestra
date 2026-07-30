import type * as monaco from "monaco-editor/esm/vs/editor/editor.api"

export type EditorOptions = monaco.editor.IStandaloneEditorConstructionOptions & {
    renderSideBySide?: boolean
    useInlineViewWhenSpaceIsLimited?: boolean
    renderOverviewRuler?: boolean
}

export type KsEditorOptions = {
    keepFocused?: boolean
    largeSuggestions?: boolean
    fullHeight?: boolean
    customHeight?: number
    diffSideBySide?: boolean
    wordWrap?: boolean
    lineNumbers?: boolean
    minimap?: boolean
    creating?: boolean
    shouldFocus?: boolean
    showScroll?: boolean
    diffOverviewBar?: boolean
    scrollKey?: string
    suggestionsOnFocus?: boolean
    pebble?: boolean
    duplicateTaskIdMarkers?: boolean
    highlightLine?: number
    initialHighlight?: string
    editor?: EditorOptions
}

export type KsEditorSchemaType = "flow" | "dashboard" | "app" | "testsuites" | "section" | string

export interface KsEditorExposes {
    focus: () => void
    destroy: () => void
    highlightLinesRange: (range: {start: number, end: number}) => void
    clearLinesRangeHighlights: () => void
    addContentWidget: (widget: {id: string, position: monaco.IPosition, height: number, right: string}) => Promise<void>
    removeContentWidget: (id: string) => void
    monaco: typeof monaco
    getEditor: () => monaco.editor.IStandaloneCodeEditor | monaco.editor.IStandaloneDiffEditor | undefined
}

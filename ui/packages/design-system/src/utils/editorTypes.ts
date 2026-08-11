import type * as monaco from "monaco-editor/editor/editor.api"

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

export interface KsEditorProps {
    modelValue?: string
    original?: string
    lang?: string
    path?: string
    schemaType?: KsEditorSchemaType
    theme?: "dark" | "light" | "vs"
    placeholder?: string | number
    label?: string
    readOnly?: boolean
    inline?: boolean
    navbar?: boolean
    configureLanguage?: (editor: monaco.editor.ICodeEditor | undefined, language: string, schemaType?: string) => Promise<void>
    loadTaskIcon?: (cls: string) => Promise<any>
    options?: KsEditorOptions
}

export type ResolvedKsEditorProps = KsEditorProps & Required<Pick<KsEditorProps,
    "modelValue" | "path" | "theme" | "placeholder" | "readOnly" | "inline" | "navbar"
>>

export interface KsEditorEmit {
    (e: "save", value?: string): void
    (e: "execute", value?: string): void
    (e: "focusout", value?: string): void
    (e: "update:modelValue", value: string): void
    (e: "cursor", payload: {position: monaco.Position, model: monaco.editor.ITextModel}): void
    (e: "confirm", value?: string): void
    (e: "mouse-move", event: monaco.editor.IEditorMouseEvent): void
    (e: "mouse-leave", event: monaco.editor.IPartialEditorMouseEvent): void
    (e: "editorMounted", editor: monaco.editor.IStandaloneCodeEditor | monaco.editor.IStandaloneDiffEditor | undefined): void
}

export interface KsEditorTemplateRefs {
    editorRef: import("vue").Ref<HTMLDivElement | null>
    container: import("vue").Ref<HTMLDivElement | undefined>
    datePickerWrapper: import("vue").Ref<HTMLElement | undefined>
    datePicker: import("vue").Ref<any>
}

import {describe, it, expect, vi} from "vitest"
import {ref, effectScope, nextTick} from "vue"
import {useEditorDecorations, type EditorDecorationsContext} from "./useEditorDecorations"
import type * as monaco from "monaco-editor/editor/editor.api"

interface MockCollection {
    decorations: monaco.editor.IModelDeltaDecoration[]
    clear: () => void
    append: (d: monaco.editor.IModelDeltaDecoration[]) => void
}

function createMockEditor(text: string = "") {
    const decorations: monaco.editor.IModelDeltaDecoration[] = []
    const clear = vi.fn(() => {
        decorations.length = 0
    })
    const append = vi.fn((d: monaco.editor.IModelDeltaDecoration[]) => {
        decorations.push(...d)
    })
    const collection: MockCollection = {decorations, clear, append}

    const model = {
        getValue: () => text,
        getLinesContent: () => text.split("\n"),
        getPositionAt: (offset: number) => ({lineNumber: 1, column: offset + 1}),
        getLineMaxColumn: () => 80,
    }

    const editor = {
        createDecorationsCollection: vi.fn(() => collection),
        getModel: () => model,
        focus: vi.fn(),
        setSelection: vi.fn(),
        revealLineInCenter: vi.fn(),
    }

    return {
        editor: editor as unknown as monaco.editor.IStandaloneCodeEditor,
        model,
        collection,
    }
}

function createContext(codeEditor?: monaco.editor.IStandaloneCodeEditor, modifiedEditor?: monaco.editor.IStandaloneCodeEditor): {
    ctx: EditorDecorationsContext
    pebbleEnabled: ReturnType<typeof ref<boolean>>
    highlightLine: ReturnType<typeof ref<number | undefined>>
    initialHighlight: ReturnType<typeof ref<string | undefined>>
} {
    const pebbleEnabled = ref(true)
    const highlightLine = ref<number | undefined>(undefined)
    const initialHighlight = ref<string | undefined>(undefined)

    const ctx: EditorDecorationsContext = {
        pebbleEnabled,
        highlightLine,
        initialHighlight,
        codeEditor: () => codeEditor,
        modifiedEditor: () => modifiedEditor ?? codeEditor,
    }

    return {ctx, pebbleEnabled, highlightLine, initialHighlight}
}

describe("useEditorDecorations", () => {
    it("applying decorations creates a collection with one entry per range", () => {
        const text = "message: {{ flow.id }} and {% if inputs.ok %}hello{% endif %}"
        const {editor, collection} = createMockEditor(text)
        const {ctx} = createContext(editor)

        const decorations = useEditorDecorations(ctx)
        decorations.attach(editor)
        decorations.highlightPebble()

        expect(editor.createDecorationsCollection).toHaveBeenCalledTimes(1)
        expect(collection.decorations.length).toBe(3)
        expect(collection.decorations[0].options.inlineClassName).toBe("highlight-pebble")
    })

    it("applying again replaces the previous set rather than adding to it", () => {
        const {editor, collection} = createMockEditor("content")
        const {ctx} = createContext(editor)

        const decorations = useEditorDecorations(ctx)
        decorations.attach(editor)

        decorations.highlightLinesRange({start: 1, end: 3})
        expect(collection.decorations.length).toBe(1)
        expect(collection.decorations[0].range.startLineNumber).toBe(1)

        decorations.highlightLinesRange({start: 10, end: 15})
        expect(collection.clear).toHaveBeenCalledTimes(2)
        expect(collection.decorations.length).toBe(1)
        expect(collection.decorations[0].range.startLineNumber).toBe(10)
    })

    it("clearing removes everything", () => {
        const {editor, collection} = createMockEditor("content")
        const {ctx} = createContext(editor)

        const decorations = useEditorDecorations(ctx)
        decorations.attach(editor)

        decorations.highlightLinesRange({start: 2, end: 5})
        expect(collection.decorations.length).toBe(1)

        decorations.clearLinesRangeHighlights()
        expect(collection.decorations.length).toBe(0)
    })

    it("an empty range list clears rather than creating an empty collection", () => {
        const {editor, collection} = createMockEditor("hello world")
        const {ctx, pebbleEnabled} = createContext(editor)

        const decorations = useEditorDecorations(ctx)
        decorations.attach(editor)

        pebbleEnabled.value = false
        decorations.highlightPebble()

        expect(editor.createDecorationsCollection).toHaveBeenCalledTimes(1)
        expect(collection.clear).toHaveBeenCalled()
        expect(collection.decorations.length).toBe(0)
    })

    it("disposal clears the collection", () => {
        const {editor, collection} = createMockEditor("content")
        const {ctx} = createContext(editor)

        const decorations = useEditorDecorations(ctx)
        decorations.attach(editor)

        decorations.highlightLinesRange({start: 1, end: 4})
        expect(collection.decorations.length).toBe(1)

        decorations.dispose()
        expect(collection.decorations.length).toBe(0)
        expect(collection.clear).toHaveBeenCalled()
    })

    it("clears collection when effect scope is disposed", () => {
        const {editor, collection} = createMockEditor("content")
        const {ctx} = createContext(editor)

        const scope = effectScope()
        let decorations!: ReturnType<typeof useEditorDecorations>

        scope.run(() => {
            decorations = useEditorDecorations(ctx)
        })

        decorations.attach(editor)
        decorations.highlightLinesRange({start: 2, end: 6})
        expect(collection.decorations.length).toBe(1)

        scope.stop()
        expect(collection.decorations.length).toBe(0)
    })

    it("no editor or no model is a no-op", async () => {
        const {ctx, highlightLine, initialHighlight} = createContext(undefined, undefined)

        const decorations = useEditorDecorations(ctx)

        expect(() => decorations.highlightPebble()).not.toThrow()
        expect(() => decorations.highlightInitial()).not.toThrow()

        initialHighlight.value = "needle"
        expect(() => decorations.highlightInitial()).not.toThrow()

        highlightLine.value = 5
        await nextTick()

        const editorWithoutModel = {
            createDecorationsCollection: vi.fn(),
            getModel: () => undefined,
        } as unknown as monaco.editor.IStandaloneCodeEditor

        const {ctx: ctxNoModel} = createContext(editorWithoutModel, editorWithoutModel)
        const decNoModel = useEditorDecorations(ctxNoModel)

        expect(() => decNoModel.highlightPebble()).not.toThrow()
    })
})

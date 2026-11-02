import {describe, test, expect, vi} from "vitest"
import {ref, effectScope} from "vue"
import * as monaco from "monaco-editor/editor/editor.api"
import {useEditorDecorations, type EditorDecorationsContext} from "./useEditorDecorations"

interface MockEditorHarness {
    editor: monaco.editor.IStandaloneCodeEditor
    collection: {
        clear: ReturnType<typeof vi.fn>
        append: ReturnType<typeof vi.fn>
        length: number
        decorations: monaco.editor.IModelDeltaDecoration[]
    }
    setText: (text: string) => void
    setModel: (model: ReturnType<typeof createMockModel> | undefined) => void
    getCreateCollectionCalls: () => number
}

function createMockModel(initialText: string) {
    let text = initialText
    return {
        getValue: () => text,
        setValue: (newText: string) => { text = newText },
        getPositionAt: (offset: number) => {
            const lines = text.slice(0, offset).split("\n")
            const lineNumber = lines.length
            const column = lines[lines.length - 1].length + 1
            return {lineNumber, column}
        },
        getLinesContent: () => text.split("\n"),
        getLineMaxColumn: (line: number) => {
            const lines = text.split("\n")
            return (lines[line - 1] ?? "").length + 1
        },
    }
}

function createEditorHarness(initialText = ""): MockEditorHarness {
    let currentModel: ReturnType<typeof createMockModel> | undefined = createMockModel(initialText)
    const currentDecorations: monaco.editor.IModelDeltaDecoration[] = []
    let createCollectionCalls = 0

    const collection = {
        clear: vi.fn(() => {
            currentDecorations.length = 0
        }),
        append: vi.fn((items: monaco.editor.IModelDeltaDecoration[]) => {
            currentDecorations.push(...items)
            return []
        }),
        get length() {
            return currentDecorations.length
        },
        get decorations() {
            return [...currentDecorations]
        },
    }

    const editor = {
        getModel: () => currentModel,
        createDecorationsCollection: vi.fn(() => {
            createCollectionCalls++
            return collection as unknown as monaco.editor.IEditorDecorationsCollection
        }),
        focus: vi.fn(),
        setSelection: vi.fn(),
        revealLineInCenter: vi.fn(),
    }

    return {
        editor: editor as unknown as monaco.editor.IStandaloneCodeEditor,
        collection,
        setText: (newText: string) => {
            currentModel?.setValue(newText)
        },
        setModel: (model: ReturnType<typeof createMockModel> | undefined) => {
            currentModel = model
        },
        getCreateCollectionCalls: () => createCollectionCalls,
    }
}

function createHarnessContext(harness: MockEditorHarness, overrides: Partial<EditorDecorationsContext> = {}): EditorDecorationsContext {
    return {
        pebbleEnabled: ref(true),
        highlightLine: ref(undefined),
        initialHighlight: ref(undefined),
        codeEditor: () => harness.editor,
        modifiedEditor: () => harness.editor,
        ...overrides,
    }
}

describe("useEditorDecorations", () => {
    test("applying decorations creates a collection with one entry per range", () => {
        const text = "tasks:\n  - id: {{ task.id }}\n    message: {% if enabled %}yes{% endif %}"
        const harness = createEditorHarness(text)
        const dec = useEditorDecorations(createHarnessContext(harness))

        dec.attach(harness.editor)
        dec.highlightPebble()

        expect(harness.collection.length).toBe(3)
        expect(harness.collection.decorations[0].options.inlineClassName).toBe("highlight-pebble")
        expect(harness.collection.decorations[0].range).toEqual({
            startLineNumber: 2,
            startColumn: 9,
            endLineNumber: 2,
            endColumn: 22,
        })
        expect(harness.collection.decorations[1].options.inlineClassName).toBe("highlight-pebble")
        expect(harness.collection.decorations[1].range).toEqual({
            startLineNumber: 3,
            startColumn: 14,
            endLineNumber: 3,
            endColumn: 30,
        })
        expect(harness.collection.decorations[2].options.inlineClassName).toBe("highlight-pebble")
        expect(harness.collection.decorations[2].range).toEqual({
            startLineNumber: 3,
            startColumn: 33,
            endLineNumber: 3,
            endColumn: 44,
        })
    })

    test("applying lines range creates a collection entry for the specified range", () => {
        const harness = createEditorHarness("line 1\nline 2\nline 3\nline 4")
        const dec = useEditorDecorations(createHarnessContext(harness))

        dec.attach(harness.editor)
        dec.highlightLinesRange({start: 2, end: 3})

        expect(harness.collection.length).toBe(1)
        expect(harness.collection.decorations[0].options.className).toBe("highlight-lines")
        expect(harness.collection.decorations[0].options.isWholeLine).toBe(true)
        expect(harness.collection.decorations[0].range).toEqual(new monaco.Range(2, 1, 3, 1))
    })

    test("applying again replaces the previous set rather than adding to it", () => {
        const harness = createEditorHarness("{{ first }} and {{ second }}")
        const dec = useEditorDecorations(createHarnessContext(harness))

        dec.attach(harness.editor)
        dec.highlightPebble()
        expect(harness.collection.length).toBe(2)

        harness.setText("only {{ single }}")
        dec.highlightPebble()

        expect(harness.collection.clear).toHaveBeenCalled()
        expect(harness.collection.length).toBe(1)
        expect(harness.collection.decorations[0].range).toEqual({
            startLineNumber: 1,
            startColumn: 6,
            endLineNumber: 1,
            endColumn: 18,
        })
    })

    test("applying line range again replaces previous line range rather than adding to it", () => {
        const harness = createEditorHarness("line 1\nline 2\nline 3\nline 4\nline 5")
        const dec = useEditorDecorations(createHarnessContext(harness))

        dec.attach(harness.editor)
        dec.highlightLinesRange({start: 1, end: 2})
        expect(harness.collection.length).toBe(1)

        dec.highlightLinesRange({start: 4, end: 5})

        expect(harness.collection.length).toBe(1)
        expect(harness.collection.decorations[0].range).toEqual(new monaco.Range(4, 1, 5, 1))
    })

    test("clearing removes everything", () => {
        const harness = createEditorHarness("{{ block }}")
        const pebbleEnabled = ref(true)
        const dec = useEditorDecorations(createHarnessContext(harness, {pebbleEnabled}))

        dec.attach(harness.editor)
        dec.highlightPebble()
        dec.highlightLinesRange({start: 1, end: 1})
        expect(harness.collection.length).toBe(2)

        dec.clearLinesRangeHighlights()
        expect(harness.collection.length).toBe(1)
        expect(harness.collection.decorations[0].options.inlineClassName).toBe("highlight-pebble")

        pebbleEnabled.value = false
        dec.highlightPebble()
        expect(harness.collection.length).toBe(0)
    })

    test("an empty range list clears rather than creating an empty collection", () => {
        const harness = createEditorHarness("{{ has_pebble }}")
        const dec = useEditorDecorations(createHarnessContext(harness))

        dec.attach(harness.editor)
        dec.highlightPebble()
        expect(harness.collection.length).toBe(1)
        expect(harness.getCreateCollectionCalls()).toBe(1)

        harness.setText("plain text with no block")
        dec.highlightPebble()

        expect(harness.collection.length).toBe(0)
        expect(harness.collection.clear).toHaveBeenCalled()
        expect(harness.getCreateCollectionCalls()).toBe(1)
    })

    test("disposal clears the collection", () => {
        const harness = createEditorHarness("{{ pebble }}")
        const scope = effectScope()
        let dec!: ReturnType<typeof useEditorDecorations>

        scope.run(() => {
            dec = useEditorDecorations(createHarnessContext(harness))
        })

        dec.attach(harness.editor)
        dec.highlightPebble()
        expect(harness.collection.length).toBe(1)

        scope.stop()

        expect(harness.collection.clear).toHaveBeenCalled()
        expect(harness.collection.length).toBe(0)
    })

    test("no editor or no model is a no-op", () => {
        const dec = useEditorDecorations({
            pebbleEnabled: ref(true),
            highlightLine: ref(undefined),
            initialHighlight: ref(undefined),
            codeEditor: () => undefined,
            modifiedEditor: () => undefined,
        })

        expect(() => {
            dec.highlightPebble()
            dec.highlightLinesRange({start: 1, end: 2})
            dec.clearLinesRangeHighlights()
            dec.highlightInitial()
        }).not.toThrow()
    })

    test("editor without a model is a no-op", () => {
        const harness = createEditorHarness("{{ pebble }}")
        harness.setModel(undefined)
        const dec = useEditorDecorations(createHarnessContext(harness))

        dec.attach(harness.editor)

        expect(() => {
            dec.highlightPebble()
        }).not.toThrow()
        expect(harness.collection.length).toBe(0)
    })

    test("calling methods before attach is a safe no-op", () => {
        const harness = createEditorHarness("{{ pebble }}")
        const dec = useEditorDecorations(createHarnessContext(harness))

        expect(() => {
            dec.highlightPebble()
            dec.highlightLinesRange({start: 1, end: 2})
            dec.clearLinesRangeHighlights()
        }).not.toThrow()
    })

    test("highlightLine selection and initial highlight navigation", () => {
        const harness = createEditorHarness("first line\ntarget needle here\nthird line")
        const highlightLine = ref<number | undefined>(undefined)
        const initialHighlight = ref<string | undefined>("target needle")
        const dec = useEditorDecorations(createHarnessContext(harness, {
            highlightLine,
            initialHighlight,
        }))

        dec.highlightInitial()

        expect(harness.editor.focus).toHaveBeenCalled()
        expect(harness.editor.setSelection).toHaveBeenCalledWith(new monaco.Range(2, 0, 2, 19))
        expect(harness.editor.revealLineInCenter).toHaveBeenCalledWith(2)
    })
})

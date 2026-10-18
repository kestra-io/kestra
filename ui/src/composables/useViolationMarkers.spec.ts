import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h, nextTick, ref} from "vue"
import {mount} from "@vue/test-utils"
import * as monaco from "monaco-editor/editor/editor.api"
import {MONACO_ERROR_SQUIGGLE_CLASS, useViolationMarkers} from "./useViolationMarkers"
import type {ValidationError} from "../utils/validationErrors"

const sampleYaml = [
    "id: my-flow",
    "namespace: company.team",
    "tasks:",
    "  - id: sample-task",
    "    type: io.kestra.plugin.core.log.Log",
    "    message: hello world",
].join("\n")

describe("useViolationMarkers", () => {
    let setModelMarkersSpy: ReturnType<typeof vi.spyOn>
    let mockDecorationsCollection: {clear: ReturnType<typeof vi.fn>}
    let isDisposed = false
    let currentSource = sampleYaml

    const mockModel = {
        uri: monaco.Uri.parse("inmemory://test/flow.yaml"),
        getValue: () => currentSource,
        isDisposed: () => isDisposed,
    } as unknown as monaco.editor.ITextModel

    const createMockEditor = (hasModel = true) => ({
        getModel: () => (hasModel ? mockModel : undefined),
        createDecorationsCollection: vi.fn(() => mockDecorationsCollection),
    })

    beforeEach(() => {
        isDisposed = false
        currentSource = sampleYaml
        mockDecorationsCollection = {
            clear: vi.fn(),
        }
        setModelMarkersSpy = vi.spyOn(monaco.editor, "setModelMarkers").mockImplementation(() => {})
    })

    afterEach(() => {
        setModelMarkersSpy.mockRestore()
    })

    function mountHelper(initialEditor?: monaco.editor.IStandaloneCodeEditor, initialErrors?: ValidationError[]) {
        const editorRef = ref<monaco.editor.IStandaloneCodeEditor | undefined>(initialEditor)
        const errorsRef = ref<ValidationError[] | undefined>(initialErrors)

        const TestComponent = defineComponent({
            setup() {
                useViolationMarkers({
                    editor: editorRef,
                    errors: errorsRef,
                })
                return () => h("div")
            },
        })

        const wrapper = mount(TestComponent)
        return {wrapper, editorRef, errorsRef}
    }

    it("produces one marker per violation at error severity", () => {
        const editor = createMockEditor() as unknown as monaco.editor.IStandaloneCodeEditor
        const errors: ValidationError[] = [
            {pointer: "/id", detail: "Flow id is invalid"},
        ]

        mountHelper(editor, errors)

        expect(setModelMarkersSpy).toHaveBeenCalledWith(
            mockModel,
            "backend-validation",
            expect.arrayContaining([
                expect.objectContaining({
                    message: "Flow id is invalid",
                    severity: monaco.MarkerSeverity.Error,
                }),
            ]),
        )
    })

    it("uses the first line for the marker and subsequent lines for decorations", () => {
        const editor = createMockEditor() as unknown as monaco.editor.IStandaloneCodeEditor
        // Pointer /tasks/0 covers multiple lines: id, type, message
        const errors: ValidationError[] = [
            {pointer: "/tasks/0", detail: "Task is misconfigured"},
        ]

        mountHelper(editor, errors)

        // Marker is placed on the first line
        expect(setModelMarkersSpy).toHaveBeenCalledWith(
            mockModel,
            "backend-validation",
            expect.arrayContaining([
                expect.objectContaining({
                    message: "Task is misconfigured",
                    startLineNumber: 4,
                }),
            ]),
        )

        // Remaining lines are sent to createDecorationsCollection
        expect(editor.createDecorationsCollection).toHaveBeenCalledWith(
            expect.arrayContaining([
                expect.objectContaining({
                    options: expect.objectContaining({
                        className: MONACO_ERROR_SQUIGGLE_CLASS,
                        hoverMessage: expect.objectContaining({
                            value: expect.stringContaining("Task is misconfigured"),
                        }),
                    }),
                }),
            ]),
        )
    })

    it("clears previous markers and decorations before applying new ones when errors change", async () => {
        const editor = createMockEditor() as unknown as monaco.editor.IStandaloneCodeEditor
        const errors: ValidationError[] = [
            {pointer: "/id", detail: "Initial error"},
        ]

        const {errorsRef} = mountHelper(editor, errors)

        expect(setModelMarkersSpy).toHaveBeenCalledWith(
            mockModel,
            "backend-validation",
            expect.arrayContaining([
                expect.objectContaining({message: "Initial error"}),
            ]),
        )

        setModelMarkersSpy.mockClear()
        mockDecorationsCollection.clear.mockClear()

        errorsRef.value = [
            {pointer: "/namespace", detail: "Second error"},
        ]
        await nextTick()

        // First call in apply() is clear() -> sets markers to []
        expect(setModelMarkersSpy).toHaveBeenNthCalledWith(1, mockModel, "backend-validation", [])
        expect(mockDecorationsCollection.clear).toHaveBeenCalledTimes(1)

        // Second call sets the new markers
        expect(setModelMarkersSpy).toHaveBeenNthCalledWith(
            2,
            mockModel,
            "backend-validation",
            expect.arrayContaining([
                expect.objectContaining({message: "Second error"}),
            ]),
        )
    })

    it("removes every marker and decoration when errors are cleared", async () => {
        const editor = createMockEditor() as unknown as monaco.editor.IStandaloneCodeEditor
        const errors: ValidationError[] = [
            {pointer: "/id", detail: "Some error"},
        ]

        const {errorsRef} = mountHelper(editor, errors)

        setModelMarkersSpy.mockClear()
        mockDecorationsCollection.clear.mockClear()

        errorsRef.value = []
        await nextTick()

        expect(setModelMarkersSpy).toHaveBeenCalledWith(mockModel, "backend-validation", [])
        expect(mockDecorationsCollection.clear).toHaveBeenCalledTimes(1)
    })

    it("does not write to a disposed model when clearing", () => {
        const editor = createMockEditor() as unknown as monaco.editor.IStandaloneCodeEditor
        const errors: ValidationError[] = [
            {pointer: "/id", detail: "Error before disposal"},
        ]

        const {wrapper} = mountHelper(editor, errors)

        setModelMarkersSpy.mockClear()
        isDisposed = true

        wrapper.unmount()

        // Because model isDisposed returns true, setModelMarkers is skipped
        expect(setModelMarkersSpy).not.toHaveBeenCalled()
    })

    it("clears everything on unmount", () => {
        const editor = createMockEditor() as unknown as monaco.editor.IStandaloneCodeEditor
        const errors: ValidationError[] = [
            {pointer: "/id", detail: "Error to clear on unmount"},
        ]

        const {wrapper} = mountHelper(editor, errors)

        setModelMarkersSpy.mockClear()
        mockDecorationsCollection.clear.mockClear()

        wrapper.unmount()

        expect(setModelMarkersSpy).toHaveBeenCalledWith(mockModel, "backend-validation", [])
        expect(mockDecorationsCollection.clear).toHaveBeenCalledTimes(1)
    })

    it("is a no-op rather than a throw when editor or model is missing", () => {
        // Missing editor
        expect(() => {
            mountHelper(undefined, [{pointer: "/id", detail: "Error"}])
        }).not.toThrow()

        // Missing model
        const editorWithoutModel = createMockEditor(false) as unknown as monaco.editor.IStandaloneCodeEditor
        expect(() => {
            mountHelper(editorWithoutModel, [{pointer: "/id", detail: "Error"}])
        }).not.toThrow()
    })
})

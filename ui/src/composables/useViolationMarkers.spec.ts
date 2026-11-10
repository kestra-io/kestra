import {beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, nextTick, shallowRef} from "vue"
import * as monaco from "monaco-editor/editor/editor.api"

import {i18nMount} from "../../tests/unit/i18nMount"
import {useViolationMarkers} from "./useViolationMarkers"
import {
    violationMarkers,
    type LineRange,
    type ViolationMarker,
} from "../utils/violationMarkers"
import type {ValidationError} from "../utils/validationErrors"

vi.mock("monaco-editor/editor/editor.api", async (importOriginal) => {
    const actual = await importOriginal<
        typeof import("monaco-editor/editor/editor.api")
    >()

    return {
        ...actual,
        editor: {
            ...actual.editor,
            setModelMarkers: vi.fn(),
        },
    }
})

vi.mock("../utils/violationMarkers", () => ({
    violationMarkers: vi.fn(),
}))

const setModelMarkers = vi.mocked(monaco.editor.setModelMarkers)
const resolveMarkers = vi.mocked(violationMarkers)

const initialErrors: ValidationError[] = [
    {
        pointer: "/tasks/0/type",
        detail: "Invalid task type",
    },
]

function lineRange(line: number): LineRange {
    return {
        startLineNumber: line,
        startColumn: 3,
        endLineNumber: line,
        endColumn: 12,
    }
}

const firstLine = lineRange(3)
const secondLine = lineRange(4)
const thirdLine = lineRange(5)
const otherLine = lineRange(8)

const initialMarkers: ViolationMarker[] = [
    {
        message: "Invalid task type",
        lines: [otherLine],
    },
    {
        message: "Missing required property",
        lines: [firstLine, secondLine, thirdLine],
    },
]

function createModel() {
    const stub = {
        getValue: vi.fn(() => "id: example\nnamespace: company.team\n"),
        isDisposed: vi.fn(() => false),
    } satisfies Pick<monaco.editor.ITextModel, "getValue" | "isDisposed">

    return {
        ...stub,
        // The composable only uses the methods declared in the typed stub.
        model: stub as unknown as monaco.editor.ITextModel,
    }
}

function createEditor(model: monaco.editor.ITextModel | null) {
    const clear = vi.fn<monaco.editor.IEditorDecorationsCollection["clear"]>()
    const getRange = vi.fn<monaco.editor.IEditorDecorationsCollection["getRange"]>(() => null)
    const collection = {
        clear,
        getRange,
    } satisfies Pick<monaco.editor.IEditorDecorationsCollection, "clear" | "getRange">

    const stub = {
        getModel: vi.fn(() => model),
        createDecorationsCollection: vi.fn<
            monaco.editor.IStandaloneCodeEditor["createDecorationsCollection"]
        >(() => collection as unknown as monaco.editor.IEditorDecorationsCollection),
        setSelection: vi.fn(),
        revealRangeInCenterIfOutsideViewport: vi.fn(),
        focus: vi.fn(),
    } satisfies Pick<
        monaco.editor.IStandaloneCodeEditor,
        "getModel" | "createDecorationsCollection" | "setSelection" | "revealRangeInCenterIfOutsideViewport" | "focus"
    >

    return {
        ...stub,
        clear,
        getRange,
        // Keep the partial-editor assertion at the Monaco boundary.
        editor: stub  as unknown as monaco.editor.IStandaloneCodeEditor,
    }
}

function mountMarkers(
    initialEditor: monaco.editor.IStandaloneCodeEditor | undefined,
    errorsValue: ValidationError[] | undefined = initialErrors,
) {
    const editor = shallowRef(initialEditor)
    const errors = shallowRef<ValidationError[] | undefined>(errorsValue)
    let revealNext: () => void = () => {}

    const wrapper = i18nMount(defineComponent({
        setup() {
            ;({revealNext} = useViolationMarkers({editor, errors}))
            return () => null
        },
    }))

    return {editor, errors, wrapper, revealNext: () => revealNext()}
}

describe("useViolationMarkers", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        resolveMarkers.mockReset()
        resolveMarkers.mockReturnValue(initialMarkers)
    })

    it("creates one error marker per violation using its first range", () => {
        const model = createModel()
        const editor = createEditor(model.model)

        mountMarkers(editor.editor)

        expect(resolveMarkers).toHaveBeenCalledWith(
            model.getValue(),
            initialErrors,
        )
        expect(setModelMarkers).toHaveBeenCalledExactlyOnceWith(
            model.model,
            "backend-validation",
            [
                {
                    ...firstLine,
                    message: "Missing required property",
                    severity: monaco.MarkerSeverity.Error,
                },
                {
                    ...otherLine,
                    message: "Invalid task type",
                    severity: monaco.MarkerSeverity.Error,
                },
            ],
        )
    })

    it("anchors each first line and squiggles only continuation ranges with escaped hover text", () => {
        const model = createModel()
        const editor = createEditor(model.model)
        resolveMarkers.mockReturnValue([
            {
                message: "Invalid *type*",
                lines: [firstLine, secondLine, thirdLine],
            },
            {
                message: "Single line",
                lines: [otherLine],
            },
        ])

        mountMarkers(editor.editor)

        expect(editor.createDecorationsCollection).toHaveBeenCalledExactlyOnceWith([
            {
                range: firstLine,
                options: {stickiness: monaco.editor.TrackedRangeStickiness.NeverGrowsWhenTypingAtEdges},
            },
            {
                range: otherLine,
                options: {stickiness: monaco.editor.TrackedRangeStickiness.NeverGrowsWhenTypingAtEdges},
            },
            {
                range: secondLine,
                options: {
                    className: "squiggly-error",
                    hoverMessage: {value: "Invalid \\*type\\*"},
                },
            },
            {
                range: thirdLine,
                options: {
                    className: "squiggly-error",
                    hoverMessage: {value: "Invalid \\*type\\*"},
                },
            },
        ])
    })

    it("clears previous markings before applying changed errors", async () => {
        const model = createModel()
        const editor = createEditor(model.model)
        const {errors} = mountMarkers(editor.editor)
        const replacementErrors: ValidationError[] = [
            {
                pointer: "/tasks/0/id",
                detail: "Missing task id",
            },
        ]

        setModelMarkers.mockClear()
        editor.createDecorationsCollection.mockClear()
        editor.clear.mockClear()
        resolveMarkers.mockReturnValue([
            {
                message: "Missing task id",
                lines: [otherLine],
            },
        ])

        errors.value = replacementErrors
        await nextTick()

        expect(resolveMarkers).toHaveBeenLastCalledWith(
            model.getValue(),
            replacementErrors,
        )
        expect(setModelMarkers).toHaveBeenCalledTimes(2)
        expect(setModelMarkers).toHaveBeenNthCalledWith(
            1,
            model.model,
            "backend-validation",
            [],
        )
        expect(setModelMarkers).toHaveBeenNthCalledWith(
            2,
            model.model,
            "backend-validation",
            [
                {
                    ...otherLine,
                    message: "Missing task id",
                    severity: monaco.MarkerSeverity.Error,
                },
            ],
        )
        expect(editor.clear).toHaveBeenCalledOnce()
        expect(editor.clear.mock.invocationCallOrder[0]).toBeLessThan(
            setModelMarkers.mock.invocationCallOrder[1],
        )
        expect(editor.createDecorationsCollection).toHaveBeenCalledExactlyOnceWith([
            {
                range: otherLine,
                options: {stickiness: monaco.editor.TrackedRangeStickiness.NeverGrowsWhenTypingAtEdges},
            },
        ])
    })

    it("clears the old model before applying markers to a replacement editor", async () => {
        const oldModel = createModel()
        const oldEditor = createEditor(oldModel.model)
        const newModel = createModel()
        const newEditor = createEditor(newModel.model)
        const {editor, wrapper} = mountMarkers(oldEditor.editor)

        setModelMarkers.mockClear()

        editor.value = newEditor.editor
        await nextTick()

        expect(setModelMarkers).toHaveBeenCalledTimes(2)
        expect(setModelMarkers).toHaveBeenNthCalledWith(
            1,
            oldModel.model,
            "backend-validation",
            [],
        )
        expect(setModelMarkers).toHaveBeenNthCalledWith(
            2,
            newModel.model,
            "backend-validation",
            expect.arrayContaining([
                expect.objectContaining({
                    message: "Missing required property",
                }),
            ]),
        )
        expect(oldEditor.clear).toHaveBeenCalledOnce()

        setModelMarkers.mockClear()
        wrapper.unmount()

        expect(setModelMarkers).toHaveBeenCalledExactlyOnceWith(
            newModel.model,
            "backend-validation",
            [],
        )
        expect(newEditor.clear).toHaveBeenCalledOnce()
    })

    it("clears markers and decorations when the component unmounts", () => {
        const model = createModel()
        const editor = createEditor(model.model)
        const {wrapper} = mountMarkers(editor.editor)

        setModelMarkers.mockClear()

        wrapper.unmount()

        expect(setModelMarkers).toHaveBeenCalledExactlyOnceWith(
            model.model,
            "backend-validation",
            [],
        )
        expect(editor.clear).toHaveBeenCalledOnce()
    })

    it("does not write to a previously marked model after it is disposed", () => {
        const model = createModel()
        const editor = createEditor(model.model)
        const {wrapper} = mountMarkers(editor.editor)

        model.isDisposed.mockReturnValue(true)
        setModelMarkers.mockClear()

        wrapper.unmount()

        expect(setModelMarkers).not.toHaveBeenCalled()
        expect(editor.clear).toHaveBeenCalledOnce()
    })

    it("does not read or write an already-disposed incoming model", () => {
        const model = createModel()
        model.isDisposed.mockReturnValue(true)
        const editor = createEditor(model.model)

        mountMarkers(editor.editor)

        expect(model.getValue).not.toHaveBeenCalled()
        expect(resolveMarkers).not.toHaveBeenCalled()
        expect(setModelMarkers).not.toHaveBeenCalled()
        expect(editor.createDecorationsCollection).not.toHaveBeenCalled()
    })

    it("does nothing when there is no editor", () => {
        expect(() => mountMarkers(undefined)).not.toThrow()

        expect(resolveMarkers).not.toHaveBeenCalled()
        expect(setModelMarkers).not.toHaveBeenCalled()
    })

    it("does nothing when the editor has no model", () => {
        const editor = createEditor(null)

        expect(() => mountMarkers(editor.editor)).not.toThrow()

        expect(resolveMarkers).not.toHaveBeenCalled()
        expect(setModelMarkers).not.toHaveBeenCalled()
        expect(editor.createDecorationsCollection).not.toHaveBeenCalled()
    })

    it("cleans previous markings when the editor becomes unavailable", async () => {
        const model = createModel()
        const oldEditor = createEditor(model.model)
        const {editor} = mountMarkers(oldEditor.editor)

        setModelMarkers.mockClear()
        resolveMarkers.mockClear()
        oldEditor.createDecorationsCollection.mockClear()

        editor.value = undefined
        await nextTick()

        expect(setModelMarkers).toHaveBeenCalledExactlyOnceWith(
            model.model,
            "backend-validation",
            [],
        )
        expect(oldEditor.clear).toHaveBeenCalledOnce()
        expect(resolveMarkers).not.toHaveBeenCalled()
        expect(oldEditor.createDecorationsCollection).not.toHaveBeenCalled()
    })

    it("does not clear the same model or decorations twice after the editor becomes unavailable", async () => {
        const model = createModel()
        const editor = createEditor(model.model)
        const {editor: editorRef, wrapper} = mountMarkers(editor.editor)

        editorRef.value = undefined
        await nextTick()

        setModelMarkers.mockClear()
        editor.clear.mockClear()

        wrapper.unmount()

        expect(setModelMarkers).not.toHaveBeenCalled()
        expect(editor.clear).not.toHaveBeenCalled()
    })

    it("reveals the first marker on the first activation", () => {
        const model = createModel()
        const editor = createEditor(model.model)
        const {revealNext} = mountMarkers(editor.editor)

        revealNext()

        expect(editor.setSelection).toHaveBeenCalledExactlyOnceWith(firstLine)
        expect(editor.revealRangeInCenterIfOutsideViewport).toHaveBeenCalledExactlyOnceWith(
            firstLine,
            monaco.editor.ScrollType.Smooth,
        )
        expect(editor.focus).toHaveBeenCalledOnce()
    })

    it("reveals where the error sits after edits moved it", () => {
        const model = createModel()
        const editor = createEditor(model.model)
        const shiftedLine = lineRange(6)
        editor.getRange.mockImplementation(index => index === 0 ? shiftedLine as monaco.Range : null)
        const {revealNext} = mountMarkers(editor.editor)

        revealNext()

        expect(editor.getRange).toHaveBeenCalledWith(0)
        expect(editor.setSelection).toHaveBeenCalledExactlyOnceWith(shiftedLine)
    })

    it("reveals the second marker on the second activation", () => {
        const model = createModel()
        const editor = createEditor(model.model)
        const {revealNext} = mountMarkers(editor.editor)

        revealNext()
        revealNext()

        expect(editor.setSelection).toHaveBeenLastCalledWith(otherLine)
        expect(editor.revealRangeInCenterIfOutsideViewport).toHaveBeenLastCalledWith(
            otherLine,
            monaco.editor.ScrollType.Smooth,
        )
    })

    it("wraps back to the first marker after the last activation", () => {
        const model = createModel()
        const editor = createEditor(model.model)
        const {revealNext} = mountMarkers(editor.editor)

        revealNext()
        revealNext()
        revealNext()

        expect(editor.setSelection).toHaveBeenLastCalledWith(firstLine)
    })

    it("does nothing on activation when the errors resolve to no marker", () => {
        resolveMarkers.mockReturnValue([])
        const model = createModel()
        const editor = createEditor(model.model)
        const {revealNext} = mountMarkers(editor.editor)

        revealNext()

        expect(editor.setSelection).not.toHaveBeenCalled()
        expect(editor.focus).not.toHaveBeenCalled()
    })

    it("cycles through the located errors only, skipping those without a location", async () => {
        const actual = await vi.importActual<typeof import("../utils/violationMarkers")>("../utils/violationMarkers")
        resolveMarkers.mockImplementation(actual.violationMarkers)
        const model = createModel()
        model.getValue.mockReturnValue("id: example\nnamespace: company.team\ntasks:\n  - id: hello\n    type: io.kestra.plugin.core.log.Log\n    message: hi\n")
        const editor = createEditor(model.model)
        const {revealNext} = mountMarkers(editor.editor, [
            {pointer: "/tasks/0/message", detail: "must not be blank"},
            {detail: "Flow is invalid"},
            {pointer: "/id", detail: "must match the pattern"},
        ])

        revealNext()
        revealNext()
        revealNext()

        expect(editor.setSelection.mock.calls.map(([range]) => range.startLineNumber)).toEqual([1, 6, 1])
    })

    it("keeps the cursor across an unchanged re-resolve", async () => {
        const model = createModel()
        const editor = createEditor(model.model)
        const {errors, revealNext} = mountMarkers(editor.editor)

        revealNext()
        expect(editor.setSelection).toHaveBeenLastCalledWith(firstLine)

        errors.value = [...initialErrors]
        await nextTick()

        revealNext()

        expect(editor.setSelection).toHaveBeenLastCalledWith(otherLine)
    })
})

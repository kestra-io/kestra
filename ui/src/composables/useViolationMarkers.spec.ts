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
        message: "Missing required property",
        lines: [firstLine, secondLine, thirdLine],
    },
    {
        message: "Invalid task type",
        lines: [otherLine],
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
        model: stub as monaco.editor.ITextModel,
    }
}

function createEditor(model: monaco.editor.ITextModel | null) {
    const clear = vi.fn<monaco.editor.IEditorDecorationsCollection["clear"]>()
    const collection = {
        clear,
    } satisfies Pick<monaco.editor.IEditorDecorationsCollection, "clear">

    const stub = {
        getModel: vi.fn(() => model),
        createDecorationsCollection: vi.fn<
            monaco.editor.IStandaloneCodeEditor["createDecorationsCollection"]
        >(() => collection as monaco.editor.IEditorDecorationsCollection),
    } satisfies Pick<
        monaco.editor.IStandaloneCodeEditor,
        "getModel" | "createDecorationsCollection"
    >

    return {
        ...stub,
        clear,
        // Keep the partial-editor assertion at the Monaco boundary.
        editor: stub as monaco.editor.IStandaloneCodeEditor,
    }
}

function mountMarkers(
    initialEditor: monaco.editor.IStandaloneCodeEditor | undefined,
    errorsValue: ValidationError[] | undefined = initialErrors,
) {
    const editor = shallowRef(initialEditor)
    const errors = shallowRef<ValidationError[] | undefined>(errorsValue)

    const wrapper = i18nMount(defineComponent({
        setup() {
            useViolationMarkers({editor, errors})
            return () => null
        },
    }))

    return {editor, errors, wrapper}
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

    it("decorates only continuation ranges with escaped hover text", () => {
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
        expect(editor.createDecorationsCollection).toHaveBeenCalledExactlyOnceWith([])
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
})
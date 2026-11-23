import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {ref} from "vue"
import type * as Monaco from "monaco-editor/editor/editor.api"
import {DATE_PICKER_SUGGESTION_LABEL, useEditorDatePicker, type DatePickerInstance} from "./useEditorDatePicker"

vi.mock("monaco-editor/editor/editor.api", () => ({
    editor: {ContentWidgetPositionPreference: {EXACT: 0, ABOVE: 1, BELOW: 2}},
}))

const ABOVE = 1
const BELOW = 2

type Position = {lineNumber: number, column: number} | null

function fakePicker(value: string | undefined) {
    const input = value === undefined ? null : Object.assign(document.createElement("input"), {value})
    return {
        $el: {nextElementSibling: {querySelector: () => input}},
        handleOpen: vi.fn(),
        focus: vi.fn(),
    } satisfies DatePickerInstance
}

function setup({value = "2026-03-14 09:30:00", suggestionsOnFocus = false}: {value?: string, suggestionsOnFocus?: boolean} = {}) {
    const wrapper = ref<HTMLElement | undefined>(document.createElement("div"))
    const picker = ref<DatePickerInstance | undefined>(fakePicker(value))
    return {
        wrapper,
        picker,
        datePicker: useEditorDatePicker({wrapper, picker, suggestionsOnFocus: ref(suggestionsOnFocus)}),
    }
}

function fakeEditor({position = {lineNumber: 3, column: 12}, word}: {position?: Position, word?: {endColumn: number}} = {}) {
    let current = position
    const model = {
        getWordAtPosition: vi.fn(() => word ?? null),
        pushEditOperations: vi.fn<Monaco.editor.ITextModel["pushEditOperations"]>(),
    }
    const editor = {
        getModel: () => model,
        getPosition: () => current,
        getSelections: () => [],
        focus: vi.fn(),
        trigger: vi.fn(),
        addContentWidget: vi.fn(),
        removeContentWidget: vi.fn(),
    }
    return {
        editor: editor as unknown as Monaco.editor.ICodeEditor,
        model,
        mock: editor,
        moveTo: (next: Position) => current = next,
    }
}

function addedWidget(mock: ReturnType<typeof fakeEditor>["mock"]): Monaco.editor.IContentWidget {
    return mock.addContentWidget.mock.calls[0][0]
}

describe("DATE_PICKER_SUGGESTION_LABEL", () => {
    // KsEditor.vue styles the suggestion row with the hard-coded selector aria-label^="_DATE_PICKER_".
    test("matches the literal that KsEditor styles select on", () => {
        expect(DATE_PICKER_SUGGESTION_LABEL).toBe("_DATE_PICKER_")
    })
})

describe("useEditorDatePicker", () => {
    beforeEach(() => vi.useFakeTimers())
    afterEach(() => vi.useRealTimers())

    test("starts hidden with today selected", () => {
        const {datePicker} = setup()

        expect(datePicker.shown.value).toBe(false)
        expect(datePicker.startOfToday.hour()).toBe(0)
        expect(datePicker.startOfToday.minute()).toBe(0)
        expect(datePicker.selectedDate.value).toEqual(datePicker.startOfToday.toDate())
    })

    test("show places the picker in the editor at the position the editor reports", async () => {
        const {datePicker, wrapper, picker} = setup()
        const {editor, mock, moveTo} = fakeEditor()

        await datePicker.show(editor)
        const widget = addedWidget(mock)

        expect(datePicker.shown.value).toBe(true)
        expect(widget.getDomNode()).toBe(wrapper.value)
        expect(widget.getPosition()).toEqual({position: {lineNumber: 3, column: 12}, preference: [BELOW, ABOVE]})

        moveTo({lineNumber: 7, column: 1})
        expect(widget.getPosition()?.position).toEqual({lineNumber: 7, column: 1})

        expect(picker.value?.handleOpen).toHaveBeenCalledOnce()
        vi.runAllTimers()
        expect(picker.value?.focus).toHaveBeenCalledOnce()
    })

    test("show is ignored while already shown and remove takes the widget back out", async () => {
        const {datePicker} = setup()
        const {editor, mock} = fakeEditor()

        await datePicker.show(editor)
        await datePicker.show(editor)
        expect(mock.addContentWidget).toHaveBeenCalledOnce()

        datePicker.remove(editor)
        expect(datePicker.shown.value).toBe(false)
        expect(mock.removeContentWidget).toHaveBeenCalledExactlyOnceWith(addedWidget(mock))
    })

    test("remove is a no-op when the picker was never shown", () => {
        const {datePicker} = setup()
        const {editor, mock} = fakeEditor()

        datePicker.remove(editor)

        expect(mock.removeContentWidget).not.toHaveBeenCalled()
    })

    test("choosing a date inserts it as an ISO timestamp followed by a space at the cursor", () => {
        const {datePicker} = setup({value: "2026-03-14 09:30:00"})
        const {editor, model, mock} = fakeEditor({position: {lineNumber: 3, column: 12}})
        datePicker.selectedDate.value = new Date(2026, 2, 14)

        datePicker.insertSelectedDate(editor)

        expect(mock.focus).toHaveBeenCalledOnce()
        expect(model.pushEditOperations).toHaveBeenCalledOnce()
        const [edit] = model.pushEditOperations.mock.calls[0][1]
        expect(edit.range).toEqual({startLineNumber: 3, startColumn: 12, endLineNumber: 3, endColumn: 12})
        expect(edit.text).toMatch(/^2026-03-14T09:30:00\.000[+-]\d{2}:\d{2} $/)
        expect(edit.forceMoveMarkers).toBe(true)
        expect(datePicker.selectedDate.value).toEqual(datePicker.startOfToday.toDate())
    })

    test("choosing a date replaces the rest of the word under the cursor", () => {
        const {datePicker} = setup()
        const {editor, model} = fakeEditor({position: {lineNumber: 1, column: 5}, word: {endColumn: 10}})

        datePicker.insertSelectedDate(editor)

        const [edit] = model.pushEditOperations.mock.calls[0][1]
        expect(edit.range).toEqual({startLineNumber: 1, startColumn: 5, endLineNumber: 1, endColumn: 10})
    })

    test("reopens suggestions after inserting only when suggestionsOnFocus is enabled", () => {
        const enabled = fakeEditor()
        setup({suggestionsOnFocus: true}).datePicker.insertSelectedDate(enabled.editor)
        expect(enabled.mock.trigger).toHaveBeenCalledExactlyOnceWith("datePickerCallback", "editor.action.triggerSuggest", {})

        const disabled = fakeEditor()
        setup({suggestionsOnFocus: false}).datePicker.insertSelectedDate(disabled.editor)
        expect(disabled.mock.trigger).not.toHaveBeenCalled()
    })

    test("places the picker nowhere when the editor has no position", async () => {
        const {datePicker} = setup()
        const {editor, mock} = fakeEditor({position: null})

        await datePicker.show(editor)

        expect(addedWidget(mock).getPosition()?.position).toBeNull()
    })
})

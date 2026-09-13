import {ref, type Ref} from "vue"
import * as monaco from "monaco-editor/editor/editor.api"
import moment from "moment"

export const DATE_PICKER_SUGGESTION_LABEL = "_DATE_PICKER_"

type CodeEditor = monaco.editor.ICodeEditor

interface DatePickerInstance {
    $el: {nextElementSibling: {querySelector: (selector: string) => HTMLInputElement | null} | null}
    handleOpen: () => void
    focus: () => void
}

export interface EditorDatePickerContext {
    wrapper: Ref<HTMLElement | undefined>
    picker: Ref<DatePickerInstance | undefined>
    suggestionsOnFocus: Ref<boolean | undefined>
}

export function useEditorDatePicker(ctx: EditorDatePickerContext) {
    const startOfToday = moment().startOf("day")
    const selectedDate = ref<Date>(startOfToday.toDate())
    const shown = ref(false)
    let widget: monaco.editor.IContentWidget | undefined

    function insertSelectedDate(editor: CodeEditor) {
        const model = editor.getModel()!
        const position = editor.getPosition()!
        const wordAtPosition = model.getWordAtPosition(position)
        const chosen = ctx.picker.value?.$el.nextElementSibling?.querySelector("input")?.value

        editor.focus()
        model.pushEditOperations(
            editor.getSelections(),
            [{
                range: {
                    startLineNumber: position.lineNumber,
                    startColumn: position.column,
                    endLineNumber: position.lineNumber,
                    endColumn: wordAtPosition?.endColumn ?? position.column,
                },
                text: `${moment(chosen).toISOString(true)} `,
                forceMoveMarkers: true,
            }],
            () => null,
        )

        selectedDate.value = startOfToday.toDate()

        if (ctx.suggestionsOnFocus.value) {
            editor.trigger("datePickerCallback", "editor.action.triggerSuggest", {})
        }
    }

    async function show(editor: CodeEditor) {
        if (shown.value) return
        shown.value = true
        if (widget === undefined) {
            widget = {
                allowEditorOverflow: true,
                getId() { return "kestra_date_picker" },
                getDomNode() { return ctx.wrapper.value! },
                getPosition() {
                    return {
                        position: editor.getPosition(),
                        preference: [
                            monaco.editor.ContentWidgetPositionPreference.BELOW,
                            monaco.editor.ContentWidgetPositionPreference.ABOVE,
                        ],
                    }
                },
            }
        }
        await editor.addContentWidget(widget)
        ctx.picker.value?.handleOpen()
        setTimeout(() => ctx.picker.value?.focus())
    }

    function remove(editor: CodeEditor) {
        if (!shown.value || !widget) return
        shown.value = false
        editor.removeContentWidget(widget)
    }

    return {startOfToday, selectedDate, shown, show, remove, insertSelectedDate}
}

import {inject, ref, type Ref} from "vue"
import type {KsEditorExposes} from "@kestra-io/design-system"
import {FOCUSED_EXPRESSION_EDITOR_INJECTION_KEY} from "../../injectionKeys"

export function useFocusedExpressionEditor(editorRef: Ref<KsEditorExposes | undefined>) {
    const focusedExpressionEditorInsert = inject(FOCUSED_EXPRESSION_EDITOR_INJECTION_KEY, ref(null))
    const insertAtCursor = (text: string) => editorRef.value?.insertTextAtCursor(text)

    function onFocus() {
        focusedExpressionEditorInsert.value = insertAtCursor
    }

    function onBlur() {
        if (focusedExpressionEditorInsert.value === insertAtCursor) {
            focusedExpressionEditorInsert.value = null
        }
    }

    return {onFocus, onBlur}
}

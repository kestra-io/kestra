import {describe, test, expect} from "vitest"
import {defineComponent, h, ref} from "vue"
import {mount} from "@vue/test-utils"
import type {KsEditorExposes} from "@kestra-io/design-system"
import {useFocusedExpressionEditor} from "./useFocusedExpressionEditor"
import {FOCUSED_EXPRESSION_EDITOR_INJECTION_KEY} from "../../injectionKeys"

describe("useFocusedExpressionEditor", () => {
    test("clears the registration on unmount even when blur never fired", () => {
        const focusedExpressionEditorInsert = ref<((text: string) => void) | null>(null)

        const TestHost = defineComponent({
            setup() {
                const editorRef = ref({insertTextAtCursor: () => {}} as unknown as KsEditorExposes)
                const {onFocus} = useFocusedExpressionEditor(editorRef)
                onFocus()
                return () => h("div")
            },
        })

        const wrapper = mount(TestHost, {
            global: {
                provide: {
                    [FOCUSED_EXPRESSION_EDITOR_INJECTION_KEY as symbol]: focusedExpressionEditorInsert,
                },
            },
        })

        expect(focusedExpressionEditorInsert.value).not.toBeNull()

        wrapper.unmount()

        expect(focusedExpressionEditorInsert.value).toBeNull()
    })
})

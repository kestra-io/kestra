import {describe, expect, it} from "vitest"
import {createPinia} from "pinia"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../../../tests/unit/i18nMount"
import TaskExpression from "./TaskExpression.vue"

const KsEditorStub = {
    name: "KsEditor",
    props: ["modelValue"],
    emits: ["update:modelValue"],
    template: "<textarea :value='modelValue' @input='$emit(\"update:modelValue\", $event.target.value)' />",
}

function mountExpression(modelValue: unknown) {
    return i18nMount(TaskExpression, {
        props: {modelValue, root: "variables.value"},
        global: {
            plugins: [createPinia()],
            stubs: {KsEditor: KsEditorStub},
        },
    })
}

function editorText(wrapper: ReturnType<typeof mountExpression>) {
    return wrapper.getComponent({name: "KsEditor"}).props("modelValue")
}

describe("TaskExpression", () => {
    it("shows a value that is replaced from outside the editor", async () => {
        const wrapper = mountExpression("first")
        expect(editorText(wrapper)).toBe("first")

        await wrapper.setProps({modelValue: "second"})
        await flushPromises()

        expect(editorText(wrapper)).toBe("second")
    })

    it("shows an empty editor when the value is removed from outside", async () => {
        const wrapper = mountExpression("first")

        await wrapper.setProps({modelValue: undefined})
        await flushPromises()

        expect(editorText(wrapper)).toBe("")
    })

    it("keeps the typed text when the parent echoes the parsed value back", async () => {
        const wrapper = mountExpression(undefined)
        const editor = wrapper.getComponent({name: "KsEditor"})

        await editor.get("textarea").setValue("padded  ")
        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["padded"])

        await wrapper.setProps({modelValue: "padded"})
        await flushPromises()
        expect(editorText(wrapper)).toBe("padded  ")

        await editor.get("textarea").setValue("padded  text")
        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["padded  text"])
    })
})

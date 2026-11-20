import {afterAll, describe, expect, it, vi} from "vitest"
import {defineComponent} from "vue"
import {createPinia} from "pinia"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../../../tests/unit/i18nMount"
import TaskArray from "./TaskArray.vue"
import TaskExpression from "./TaskExpression.vue"
import {taskComponentsReady} from "./useBlockComponent"

afterAll(() => taskComponentsReady())

const KsEditorStub = defineComponent({
    props: ["modelValue"],
    emits: ["update:modelValue", "focus"],
    template: "<textarea :value='modelValue' @input='$emit(\"update:modelValue\", $event.target.value)' />",
})

function mountExpression(modelValue?: unknown) {
    return i18nMount(TaskExpression, {
        props: {modelValue, root: "message"},
        global: {plugins: [createPinia()], stubs: {KsEditor: KsEditorStub}},
    })
}

describe("TaskExpression", () => {
    it("shows a value that the parent sets after the editor mounted", async () => {
        const wrapper = mountExpression("first")
        expect(wrapper.get("textarea").element.value).toBe("first")

        await wrapper.setProps({modelValue: "second"})
        expect(wrapper.get("textarea").element.value).toBe("second")

        await wrapper.setProps({modelValue: undefined})
        expect(wrapper.get("textarea").element.value).toBe("")
    })

    it("shows a value that is not valid YAML, and the one that replaces it", async () => {
        const wrapper = mountExpression("Error: failed: x")
        expect(wrapper.get("textarea").element.value).toBe("Error: failed: x")

        await wrapper.setProps({modelValue: "other"})
        expect(wrapper.get("textarea").element.value).toBe("other")

        await wrapper.setProps({modelValue: "Error: failed: y"})
        expect(wrapper.get("textarea").element.value).toBe("Error: failed: y")
    })

    it("keeps the text being typed while the parent echoes it", async () => {
        const wrapper = mountExpression(undefined)
        const editor = wrapper.get("textarea")

        await editor.setValue("{{ inputs.message }}")
        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["{{ inputs.message }}"])
        await wrapper.setProps({modelValue: "{{ inputs.message }}"})
        expect(editor.element.value).toBe("{{ inputs.message }}")

        await editor.setValue("key: value")
        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([{key: "value"}])
        await wrapper.setProps({modelValue: {key: "value"}})
        expect(editor.element.value).toBe("key: value")
    })

    it("shows a value an array row is given from outside", async () => {
        const wrapper = i18nMount(TaskArray, {
            props: {
                root: "messages",
                schema: {type: "array", items: {}},
                modelValue: ["first"],
            },
            global: {plugins: [createPinia()], stubs: {KsEditor: KsEditorStub}},
        })

        await vi.waitFor(() => expect(wrapper.findComponent(TaskExpression).exists()).toBe(true))
        expect(wrapper.get("textarea").element.value).toBe("first")

        await wrapper.setProps({modelValue: ["second"]})
        await flushPromises()
        expect(wrapper.get("textarea").element.value).toBe("second")
    })

    it("keeps a text that collapses to an empty value while the parent echoes it", async () => {
        const wrapper = mountExpression("first")
        const editor = wrapper.get("textarea")

        await editor.setValue("{}")
        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([undefined])
        await wrapper.setProps({modelValue: undefined})
        expect(editor.element.value).toBe("{}")
    })

    it("shows the remaining rows after an array row is removed", async () => {
        const wrapper = i18nMount(TaskArray, {
            props: {
                root: "messages",
                schema: {type: "array", items: {}},
                modelValue: ["first", "second", "third"],
            },
            global: {plugins: [createPinia()], stubs: {KsEditor: KsEditorStub}},
        })

        await vi.waitFor(() => expect(wrapper.findAllComponents(TaskExpression)).toHaveLength(3))

        await wrapper.setProps({modelValue: ["second", "third"]})
        await flushPromises()
        expect(wrapper.findAll("textarea").map((editor) => editor.element.value)).toEqual(["second", "third"])
    })
})

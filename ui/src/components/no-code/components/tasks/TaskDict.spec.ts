import {afterAll, afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent} from "vue"
import {createPinia} from "pinia"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../../../tests/unit/i18nMount"
import TaskDict from "./TaskDict.vue"
import TaskExpression from "./TaskExpression.vue"
import TaskString from "./TaskString.vue"
import InputText from "../inputs/InputText.vue"
import Add from "../Add.vue"
import {taskComponentsReady} from "./useBlockComponent"
import type {Schema} from "./getTaskComponent"

afterAll(() => taskComponentsReady())
beforeEach(() => vi.useFakeTimers())
afterEach(() => vi.useRealTimers())

function mountDict(modelValue: Record<string, unknown> | null = {}, schema: Schema = {}) {
    return i18nMount(TaskDict, {
        props: {modelValue, schema, root: "variables"},
        attachTo: document.body,
        global: {
            plugins: [createPinia()],
            stubs: {
                KsInput: defineComponent({
                    props: ["modelValue"],
                    emits: ["update:modelValue"],
                    template: "<textarea ref='input' :value='modelValue' @input='$emit(\"update:modelValue\", $event.target.value)' />",
                    methods: {
                        focus() {
                            (this.$refs.input as HTMLTextAreaElement).focus()
                        },
                    },
                }),
                KsEditor: {
                    props: ["modelValue"],
                    emits: ["update:modelValue"],
                    template: "<textarea :value='modelValue' @input='$emit(\"update:modelValue\", $event.target.value)' />",
                },
            },
        },
    })
}

describe("TaskDict", () => {
    it("focuses a new key and keeps incomplete rows out of debounced updates", async () => {
        const wrapper = mountDict({existing: "value"})
        await wrapper.getComponent(Add).trigger("click")
        await vi.advanceTimersByTimeAsync(100)
        const key = wrapper.findAllComponents(InputText)[1]
        expect(document.activeElement).toBe(key.get("textarea").element)
        await vi.advanceTimersByTimeAsync(100)
        expect(wrapper.emitted("update:modelValue")).toEqual([[{existing: "value"}]])

        key.vm.$emit("update:modelValue", "added")
        await vi.advanceTimersByTimeAsync(200)
        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([{existing: "value"}])
        wrapper.findAllComponents(TaskExpression)[1].vm.$emit("update:modelValue", {nested: [false, 0, null]})
        await vi.advanceTimersByTimeAsync(199)
        expect(wrapper.emitted("update:modelValue")).toHaveLength(2)
        await vi.advanceTimersByTimeAsync(1)
        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([{existing: "value", added: {nested: [false, 0, null]}}])
    })

    it("blocks duplicate keys until renamed and preserves edits when unmounted before debounce", async () => {
        const wrapper = mountDict({first: "one", second: "two"})
        const keys = wrapper.findAllComponents(InputText)
        keys[1].vm.$emit("update:modelValue", "first")
        await vi.advanceTimersByTimeAsync(200)
        expect(wrapper.find(".duplicate-alert").exists()).toBe(true)
        expect(wrapper.emitted("update:modelValue")).toBeUndefined()
        keys[1].vm.$emit("update:modelValue", "renamed")
        await flushPromises()
        wrapper.unmount()
        expect(wrapper.emitted("update:modelValue")).toEqual([[{first: "one", renamed: "two"}]])
        await vi.advanceTimersByTimeAsync(200)
        expect(wrapper.emitted("update:modelValue")).toHaveLength(1)
    })

    it("round trips YAML values and removes entries through the expression fallback", async () => {
        const wrapper = mountDict({value: "initial", remove: "entry"}, {additionalProperties: false})
        const editor = wrapper.findAllComponents(TaskExpression)[0].get("textarea")
        for (const [yaml, value] of [
            ["text", "text"],
            ["42", 42],
            ["false", false],
            ["[1, false, null]", [1, false, null]],
            ["nested: value", {nested: "value"}],
            ["{{ inputs.value }}", "{{ inputs.value }}"],
        ] as const) {
            await editor.setValue(yaml)
            await vi.advanceTimersByTimeAsync(200)
            expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([{value, remove: "entry"}])
        }
        await editor.setValue("null")
        await vi.advanceTimersByTimeAsync(200)
        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([{remove: "entry"}])
        await wrapper.get(".delete svg").trigger("click")
        await vi.advanceTimersByTimeAsync(200)
        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([{remove: "entry"}])
        await wrapper.get(".delete svg").trigger("click")
        await vi.advanceTimersByTimeAsync(200)
        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([{}])
    })

    it("supports boolean and object additionalProperties and external model resets", async () => {
        const wrapper = mountDict({value: "initial"}, {additionalProperties: true})
        await taskComponentsReady()
        await flushPromises()
        expect(wrapper.findComponent(TaskExpression).exists()).toBe(true)
        expect(wrapper.find(".item-wrapper").exists()).toBe(true)
        await wrapper.setProps({schema: {additionalProperties: {type: "string"}}})
        expect(wrapper.findComponent(TaskExpression).exists()).toBe(false)
        expect(wrapper.getComponent(TaskString).props("modelValue")).toBe("initial")
        await wrapper.setProps({schema: {additionalProperties: false}, modelValue: {changed: null}})
        expect(wrapper.getComponent(InputText).props("modelValue")).toBe("changed")
        expect(wrapper.getComponent(TaskExpression).props("modelValue")).toBeNull()
        await wrapper.setProps({modelValue: null})
        expect(wrapper.findComponent(InputText).exists()).toBe(false)
    })
})

import {describe, expect, test} from "vitest"
import {defineComponent, h} from "vue"
import {i18nMount} from "../../../../../tests/unit/i18nMount"
import MixinTask from "./MixinTask"

const Host = defineComponent({
    mixins: [MixinTask],
    render: () => h("div"),
})

const mountHost = (props: Record<string, unknown> = {}) => i18nMount(Host, {props})

describe("MixinTask", () => {
    test("getKey prefixes the key with root and a dot", () => {
        const wrapper = mountHost({root: "tasks"})

        expect(wrapper.vm.getKey("id")).toBe("tasks.id")
    })

    test("getKey returns the key alone when there is no root", () => {
        const wrapper = mountHost()

        expect(wrapper.vm.getKey("id")).toBe("id")
    })

    test("isRequired is true when the key is in schema.required", () => {
        const wrapper = mountHost({schema: {required: ["id"]}})

        expect(wrapper.vm.isRequired("id")).toBe(true)
    })

    test("isRequired is false when the key is not in schema.required", () => {
        const wrapper = mountHost({schema: {required: ["id"]}})

        expect(wrapper.vm.isRequired("type")).toBe(false)
    })

    test("isRequired is falsy when there is no schema", () => {
        const wrapper = mountHost()

        expect(wrapper.vm.isRequired("id")).toBeFalsy()
    })

    test("values returns modelValue when it is set", () => {
        const wrapper = mountHost({modelValue: "hello", schema: {default: "x"}})

        expect(wrapper.vm.values).toBe("hello")
    })

    test("values falls back to schema.default when modelValue is undefined", () => {
        const wrapper = mountHost({schema: {default: "x"}})

        expect(wrapper.vm.values).toBe("x")
    })

    test.each([0, false])("values keeps the falsy modelValue %s instead of schema.default", (modelValue) => {
        const wrapper = mountHost({modelValue, schema: {default: "x"}})

        expect(wrapper.vm.values).toBe(modelValue)
    })

    test("editorValue keeps a string value as is", () => {
        const wrapper = mountHost({modelValue: "a: 1"})

        expect(wrapper.vm.editorValue).toBe("a: 1")
    })

    test("editorValue stringifies a non-string value to YAML", () => {
        const wrapper = mountHost({modelValue: {a: 1, b: ["x"]}})

        expect(wrapper.vm.editorValue).toBe("a: 1\nb:\n  - x\n")
    })

    test("info prefers schema.title over schema.type", () => {
        const wrapper = mountHost({schema: {title: "Task id", type: "string"}})

        expect(wrapper.vm.info).toBe("Task id")
    })

    test("info falls back to schema.type when there is no title", () => {
        const wrapper = mountHost({schema: {type: "string"}})

        expect(wrapper.vm.info).toBe("string")
    })

    test("info is undefined when there is no schema", () => {
        const wrapper = mountHost()

        expect(wrapper.vm.info).toBeUndefined()
    })

    test("onInput emits the value as update:modelValue", () => {
        const wrapper = mountHost()

        wrapper.vm.onInput("hello")

        expect(wrapper.emitted("update:modelValue")).toEqual([["hello"]])
    })

    test.each(["", null, {}])("onInput emits undefined for the empty value %j", (value) => {
        const wrapper = mountHost()

        wrapper.vm.onInput(value)

        expect(wrapper.emitted("update:modelValue")).toEqual([[undefined]])
    })
})
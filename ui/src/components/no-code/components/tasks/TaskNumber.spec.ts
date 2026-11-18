import {describe, expect, test} from "vitest"
import {KsInputNumber} from "@kestra-io/design-system"
import {i18nMount} from "../../../../../tests/unit/i18nMount"
import TaskNumber from "./TaskNumber.vue"

describe("TaskNumber", () => {
    test("uses modelValue over the schema default", () => {
        const wrapper = i18nMount(TaskNumber, {
            props: {
                modelValue: 5,
                schema: {type: "number", default: 10},
            },
        })

        const input = wrapper.findComponent(KsInputNumber)
        expect(input.props("modelValue")).toBe(5)
        expect((wrapper.find("input").element as HTMLInputElement).value).toBe("5")
    })

    test("emits update:modelValue with the collapsed value on edit", async () => {
        const wrapper = i18nMount(TaskNumber, {
            props: {
                modelValue: 10,
            },
        })

        const input = wrapper.findComponent(KsInputNumber)

        await input.vm.$emit("update:modelValue", 42)
        expect(wrapper.emitted("update:modelValue")).toEqual([[42]])

        await input.vm.$emit("update:modelValue", "")
        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([undefined])
    })

    test("correctly renders and emits for a value of 0", async () => {
        const wrapper = i18nMount(TaskNumber, {
            props: {
                modelValue: 0,
                schema: {type: "number", default: 10},
            },
        })

        const input = wrapper.findComponent(KsInputNumber)
        expect(input.props("modelValue")).toBe(0)
        expect((wrapper.find("input").element as HTMLInputElement).value).toBe("0")

        await input.vm.$emit("update:modelValue", 0)
        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([0])
    })
})


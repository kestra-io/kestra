import { describe, expect, test } from "vitest"
import { KsInputNumber } from "@kestra-io/design-system"
import { i18nMount } from "../../../../../tests/unit/i18nMount"
import TaskNumber from "./TaskNumber.vue"

describe("TaskNumber", () => {
    test("uses modelValue over the schema default", () => {
        const wrapper = i18nMount(TaskNumber, {
            props: {
                modelValue: 5,
                schema: { type: "number", default: 10 },
            },
        })

        const input = wrapper.findComponent(KsInputNumber)
        expect(input.props("modelValue")).toBe(5)
        expect((wrapper.find("input").element as HTMLInputElement).value).toBe("5")
    })

    test("marks a required field with no value as invalid", () => {
        const wrapper = i18nMount(TaskNumber, {
            props: {
                required: true,
                modelValue: undefined,
            },
        })

        const input = wrapper.findComponent(KsInputNumber)
        expect(input.vm.$attrs.state).toBe(false)
    })

    test("marks a non-numeric value as invalid, and a numeric one as valid", () => {
        const invalidWrapper = i18nMount(TaskNumber, {
            props: {
                modelValue: "not-a-number",
            },
        })
        expect(invalidWrapper.findComponent(KsInputNumber).vm.$attrs.state).toBe(false)

        const validWrapper = i18nMount(TaskNumber, {
            props: {
                modelValue: 42,
            },
        })
        expect(validWrapper.findComponent(KsInputNumber).vm.$attrs.state).toBe(true)
    })

    test("marks a non-required field with no value as valid", () => {
        const wrapper = i18nMount(TaskNumber, {
            props: {
                required: false,
                modelValue: undefined,
            },
        })

        const input = wrapper.findComponent(KsInputNumber)
        expect(input.vm.$attrs.state).toBe(true)
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

    test("correctly renders, validates, and emits for a value of 0", async () => {
        const wrapper = i18nMount(TaskNumber, {
            props: {
                modelValue: 0,
                schema: { type: "number", default: 10 },
            },
        })

        const input = wrapper.findComponent(KsInputNumber)
        expect(input.props("modelValue")).toBe(0)
        expect((wrapper.find("input").element as HTMLInputElement).value).toBe("0")
        expect(input.vm.$attrs.state).toBe(true)

        await input.vm.$emit("update:modelValue", 0)
        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([0])
    })
})


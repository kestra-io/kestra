import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../index"
import KsInputTag from "./KsInputTag.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsInputTag", () => {
    test("renders one tag per value", () => {
        const wrapper = mount(KsInputTag, {
            props: {modelValue: ["a@x.com", "b@x.com"]},
            global: globalConfig,
        })

        expect(wrapper.findAll(".kel-tag").map(tag => tag.text())).toEqual(["a@x.com", "b@x.com"])
    })

    test("adds the typed text as a tag on Enter", async () => {
        const wrapper = mount(KsInputTag, {
            props: {modelValue: ["a@x.com"]},
            global: globalConfig,
        })
        const input = wrapper.find("input")

        await input.setValue("  b@x.com ")
        await input.trigger("keydown", {key: "Enter", code: "Enter"})

        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([["a@x.com", "b@x.com"]])
    })

    test("does not add a tag while the input is blank", async () => {
        const wrapper = mount(KsInputTag, {
            props: {modelValue: []},
            global: globalConfig,
        })
        const input = wrapper.find("input")

        await input.setValue("   ")
        await input.trigger("keydown", {key: "Enter", code: "Enter"})

        expect(wrapper.emitted("update:modelValue")).toBeUndefined()
    })

    test("splits typed text on the delimiter into several tags", async () => {
        const wrapper = mount(KsInputTag, {
            props: {modelValue: [], delimiter: /[\s,;]+/},
            global: globalConfig,
        })

        await wrapper.find("input").setValue("a@x.com, b@x.com; c@x.com")

        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([["a@x.com", "b@x.com", "c@x.com"]])
    })

    test("removes the last tag on Backspace in an empty input", async () => {
        const wrapper = mount(KsInputTag, {
            props: {modelValue: ["a@x.com", "b@x.com"]},
            global: globalConfig,
        })

        await wrapper.find("input").trigger("keydown", {key: "Backspace", code: "Backspace"})

        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([["a@x.com"]])
    })

    test("marks only the tags the invalid predicate flags and keeps them", () => {
        const wrapper = mount(KsInputTag, {
            props: {modelValue: ["a@x.com", "nope"], invalid: (value: string) => !value.includes("@")},
            global: globalConfig,
        })

        expect(wrapper.findAll(".kel-tag").length).toBe(2)
        expect(wrapper.findAll(".ks-input-tag-invalid").map(tag => tag.text())).toEqual(["nope"])
    })

    test("disabled applies is-disabled class", () => {
        const wrapper = mount(KsInputTag, {
            props: {modelValue: ["a@x.com"], disabled: true},
            global: globalConfig,
        })

        expect(wrapper.find(".kel-input-tag.is-disabled").exists()).toBe(true)
    })
})

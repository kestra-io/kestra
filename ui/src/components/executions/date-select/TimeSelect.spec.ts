import {describe, expect, it} from "vitest"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import en from "../../../translations/en.json"
import TimeSelect from "./TimeSelect.vue"
import DateSelect from "./DateSelect.vue"

describe("TimeSelect", () => {
    it("hides custom duration input when includeNever is true and expiration defaults to Never", () => {
        const wrapper = i18nMount(TimeSelect, {
            locales: en,
            props: {
                fromNow: false,
                allowInfinite: true,
                allowCustom: true,
                clearable: true,
                includeNever: true,
                timeRange: undefined,
            },
        })

        const dateSelect = wrapper.getComponent(DateSelect)
        expect(dateSelect.props("value")).toBe("never")
        expect(wrapper.find("[data-test='custom-duration']").exists()).toBe(false)
    })

    it("shows custom duration input when Custom is selected and hides it again when Never is selected", async () => {
        const wrapper = i18nMount(TimeSelect, {
            locales: en,
            props: {
                fromNow: false,
                allowInfinite: true,
                allowCustom: true,
                clearable: true,
                includeNever: true,
                timeRange: undefined,
            },
        })

        const dateSelect = wrapper.getComponent(DateSelect)
        await dateSelect.vm.$emit("change", "custom")

        expect(dateSelect.props("value")).toBe("custom")
        expect(wrapper.find("[data-test='custom-duration']").exists()).toBe(true)
        expect(wrapper.emitted("update:modelValue")).toEqual([[{timeRange: undefined}]])

        // Typing a duration that passes through a preset string (PT1H -> PT1H30M) stays in Custom mode
        await wrapper.setProps({timeRange: "PT1H"})
        expect(dateSelect.props("value")).toBe("custom")
        expect(wrapper.find("[data-test='custom-duration']").exists()).toBe(true)

        await dateSelect.vm.$emit("change", "never")
        expect(dateSelect.props("value")).toBe("never")
        expect(wrapper.find("[data-test='custom-duration']").exists()).toBe(false)
    })

    it("selects Custom and shows input when hydrated with a non-preset duration", () => {
        const wrapper = i18nMount(TimeSelect, {
            locales: en,
            props: {
                fromNow: false,
                allowInfinite: true,
                allowCustom: true,
                clearable: true,
                includeNever: true,
                timeRange: "PT45M",
            },
        })

        expect(wrapper.getComponent(DateSelect).props("value")).toBe("custom")
        expect(wrapper.find("[data-test='custom-duration']").exists()).toBe(true)
    })

    it("hides custom duration input and keeps the preset selected when hydrated with a preset duration", () => {
        const wrapper = i18nMount(TimeSelect, {
            locales: en,
            props: {
                fromNow: false,
                allowInfinite: true,
                allowCustom: true,
                clearable: true,
                includeNever: true,
                timeRange: "PT1H",
            },
        })

        expect(wrapper.getComponent(DateSelect).props("value")).toBe("PT1H")
        expect(wrapper.find("[data-test='custom-duration']").exists()).toBe(false)
    })

    it("lists the Custom and Never options only when allowCustom and includeNever are set", () => {
        const values = (props: Record<string, unknown>) => i18nMount(TimeSelect, {locales: en, props: {fromNow: false, ...props}})
            .getComponent(DateSelect)
            .props("options")
            .map((option: {value?: string}) => option.value)

        expect(values({allowCustom: true, includeNever: true})).toEqual(expect.arrayContaining(["custom", "never"]))
        expect(values({allowCustom: true})).toContain("custom")
        expect(values({allowCustom: true})).not.toContain("never")
        expect(values({includeNever: true})).toContain("never")
        expect(values({includeNever: true})).not.toContain("custom")
    })

    it("only offers the clear button when a duration is selected, not on Never", async () => {
        const wrapper = i18nMount(TimeSelect, {
            locales: en,
            props: {
                fromNow: false,
                allowInfinite: true,
                allowCustom: true,
                clearable: true,
                includeNever: true,
                timeRange: undefined,
            },
        })

        const dateSelect = wrapper.getComponent(DateSelect)
        expect(dateSelect.props("clearable")).toBe(false)

        await dateSelect.vm.$emit("change", "PT1H")
        expect(dateSelect.props("value")).toBe("PT1H")
        expect(dateSelect.props("clearable")).toBe(true)

        await dateSelect.vm.$emit("change", undefined)
        expect(dateSelect.props("value")).toBe("never")
        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([{timeRange: undefined}])
    })

    it("defaults to Custom mode with input visible when includeNever is false and allowCustom is true", () => {
        const wrapper = i18nMount(TimeSelect, {
            locales: en,
            props: {
                fromNow: false,
                allowCustom: true,
                clearable: true,
                timeRange: undefined,
            },
        })

        expect(wrapper.getComponent(DateSelect).props("value")).toBe("custom")
        expect(wrapper.find("[data-test='custom-duration']").exists()).toBe(true)
    })
})

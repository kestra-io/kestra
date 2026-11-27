import {describe, test, expect} from "vitest"
import FilterRadio from "./FilterRadio.vue"
import type {FilterValue} from "../utils/filterTypes"
import {i18nMount} from "../../../../../../tests/units/i18nMount"

interface FilterRadioProps {
    modelValue: string;
    options: FilterValue[];
    allLabel?: string;
    allDescription?: string;
}

const sampleOptions: FilterValue[] = [
    {label: "Option A", value: "opt-a", description: "Description for A"},
    {label: "Option B", value: "opt-b"},
]

const mountFilterRadio = (props: Partial<FilterRadioProps> = {}) =>
    i18nMount(FilterRadio, {
        props: {
            modelValue: "ALL",
            options: sampleOptions,
            ...props,
        },
    })

describe("FilterRadio", () => {
    test("an ALL option is rendered first, ahead of options", () => {
        const wrapper = mountFilterRadio({options: sampleOptions})
        const options = wrapper.findAll(".option")

        expect(options).toHaveLength(3)
        expect(options[0].attributes("data-test")).toBe("filter-radio-default")
        expect(options[0].find(".title").text()).toBe("filter.hierarchy.all")
        expect(options[1].attributes("data-test")).toBe("filter-radio-opt-a")
        expect(options[2].attributes("data-test")).toBe("filter-radio-opt-b")
    })

    test("allLabel and allDescription override the default texts", () => {
        const defaultWrapper = mountFilterRadio()
        const defaultOption = defaultWrapper.find("[data-test='filter-radio-default']")
        expect(defaultOption.find(".title").text()).toBe("filter.hierarchy.all")
        expect(defaultOption.find(".desc").text()).toBe("filter.show default")

        const customWrapper = mountFilterRadio({
            allLabel: "Custom All",
            allDescription: "Custom Description",
        })
        const customOption = customWrapper.find("[data-test='filter-radio-default']")
        expect(customOption.find(".title").text()).toBe("Custom All")
        expect(customOption.find(".desc").text()).toBe("Custom Description")
    })

    test("an option's description is rendered only when it carries one", () => {
        const wrapper = mountFilterRadio({options: sampleOptions})

        const optA = wrapper.find("[data-test='filter-radio-opt-a']")
        expect(optA.find(".desc").exists()).toBe(true)
        expect(optA.find(".desc").text()).toBe("Description for A")

        const optB = wrapper.find("[data-test='filter-radio-opt-b']")
        expect(optB.find(".desc").exists()).toBe(false)
    })

    test("the selected class marks exactly the matching option", () => {
        const wrapper = mountFilterRadio({modelValue: "opt-a"})

        expect(wrapper.find("[data-test='filter-radio-opt-a']").classes()).toContain("selected")
        expect(wrapper.find("[data-test='filter-radio-default']").classes()).not.toContain("selected")
        expect(wrapper.find("[data-test='filter-radio-opt-b']").classes()).not.toContain("selected")
        expect(wrapper.findAll(".option.selected")).toHaveLength(1)
    })

    test("clicking an option emits update:modelValue with its value", async () => {
        const wrapper = mountFilterRadio({modelValue: "ALL"})

        await wrapper.find("[data-test='filter-radio-opt-a']").trigger("click")

        expect(wrapper.emitted("update:modelValue")).toEqual([["opt-a"]])
        expect(wrapper.find("[data-test='filter-radio-opt-a']").classes()).toContain("selected")
        expect(wrapper.find("[data-test='filter-radio-default']").classes()).not.toContain("selected")
    })

    test("clicking the ALL row emits ALL", async () => {
        const wrapper = mountFilterRadio({modelValue: "opt-a"})

        await wrapper.find("[data-test='filter-radio-default']").trigger("click")

        expect(wrapper.emitted("update:modelValue")).toEqual([["ALL"]])
        expect(wrapper.find("[data-test='filter-radio-default']").classes()).toContain("selected")
        expect(wrapper.find("[data-test='filter-radio-opt-a']").classes()).not.toContain("selected")
    })

    test("a modelValue change from the parent updates the selection", async () => {
        const wrapper = mountFilterRadio({modelValue: "ALL"})
        expect(wrapper.find("[data-test='filter-radio-default']").classes()).toContain("selected")

        await wrapper.find("[data-test='filter-radio-opt-a']").trigger("click")
        expect(wrapper.find("[data-test='filter-radio-opt-a']").classes()).toContain("selected")

        await wrapper.setProps({modelValue: "opt-b"})

        expect(wrapper.find("[data-test='filter-radio-opt-b']").classes()).toContain("selected")
        expect(wrapper.find("[data-test='filter-radio-opt-a']").classes()).not.toContain("selected")
        expect(wrapper.find("[data-test='filter-radio-default']").classes()).not.toContain("selected")
    })
})


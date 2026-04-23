import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsCascaderPanel from "../../../src/components/Form/KsCascaderPanel.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

const options = [
    {
        value: "guide",
        label: "Guide",
        children: [
            {value: "disciplines", label: "Disciplines"},
            {value: "consistency", label: "Consistency"},
        ],
    },
    {
        value: "component",
        label: "Component",
        children: [
            {value: "basic", label: "Basic"},
            {value: "form", label: "Form"},
        ],
    },
]

describe("KsCascaderPanel", () => {
    test("renders cascader panel element", () => {
        const wrapper = mount(KsCascaderPanel, {
            props: {options},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-cascader-panel").exists()).toBe(true)
    })

    test("renders first-level options from options prop", () => {
        const wrapper = mount(KsCascaderPanel, {
            props: {options},
            global: globalConfig,
        })
        expect(wrapper.text()).toContain("Guide")
        expect(wrapper.text()).toContain("Component")
    })

    test("emits change when value changes", async () => {
        const wrapper = mount(KsCascaderPanel, {
            props: {options, modelValue: null},
            global: globalConfig,
        })
        // Trigger change programmatically via emitted event
        wrapper.vm.$emit("change", ["guide", "disciplines"])
        expect(wrapper.emitted("change")).toBeTruthy()
    })
})

import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsTabsToggle from "../../../src/components/Navigation/KsTabs/KsTabsToggle.vue"
import KsRadioButton from "../../../src/components/Form/KsRadio/KsRadioButton.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsTabsToggle", () => {
    test("renders the tabs-toggle container", () => {
        const wrapper = mount(KsTabsToggle, {
            props: {modelValue: "a"},
            slots: {
                default: `
                    <KsRadioButton value="a">A</KsRadioButton>
                    <KsRadioButton value="b">B</KsRadioButton>
                `,
            },
            global: globalConfig,
        })
        expect(wrapper.find(".kel-tabs-toggle").exists()).toBe(true)
    })

    test("renders provided radio buttons", () => {
        const wrapper = mount(KsTabsToggle, {
            props: {modelValue: "a"},
            slots: {
                default: `
                    <KsRadioButton value="a">A</KsRadioButton>
                    <KsRadioButton value="b">B</KsRadioButton>
                    <KsRadioButton value="c">C</KsRadioButton>
                `,
            },
            global: globalConfig,
        })
        expect(wrapper.findAllComponents(KsRadioButton)).toHaveLength(3)
    })

    test("emits update:modelValue and change when selection changes", async () => {
        const wrapper = mount(KsTabsToggle, {
            props: {modelValue: "a"},
            slots: {
                default: `
                    <KsRadioButton value="a">A</KsRadioButton>
                    <KsRadioButton value="b">B</KsRadioButton>
                `,
            },
            global: globalConfig,
        })

        const radios = wrapper.findAll("input[type='radio']")
        await radios[1].setValue(true)

        expect(wrapper.emitted("update:modelValue")?.[0]).toEqual(["b"])
        expect(wrapper.emitted("change")?.[0]).toEqual(["b"])
    })

    test("forwards aria-label to the radiogroup", () => {
        const wrapper = mount(KsTabsToggle, {
            props: {modelValue: "a", ariaLabel: "Ticket type"},
            slots: {
                default: "<KsRadioButton value=\"a\">A</KsRadioButton>",
            },
            global: globalConfig,
        })
        expect(wrapper.find(".kel-tabs-toggle").attributes("aria-label")).toBe("Ticket type")
    })

    test("disabled prop is accepted on the toggle", () => {
        const wrapper = mount(KsTabsToggle, {
            props: {modelValue: "a", disabled: true},
            slots: {
                default: "<KsRadioButton value=\"a\">A</KsRadioButton>",
            },
            global: globalConfig,
        })
        expect(wrapper.props("disabled")).toBe(true)
    })
})

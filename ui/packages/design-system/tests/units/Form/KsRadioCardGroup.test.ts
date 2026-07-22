import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsRadioCardGroup from "../../../src/components/Form/KsRadio/KsRadioCardGroup.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

const OPTIONS = [
    {value: "SERVER", label: "Server", hint: "shared token"},
    {value: "CLIENT", label: "Client"},
]

describe("KsRadioCardGroup", () => {
    test("renders a card per option inside a radiogroup", () => {
        const wrapper = mount(KsRadioCardGroup, {
            props: {modelValue: "SERVER", options: OPTIONS},
            global: globalConfig,
        })
        expect(wrapper.find("[role='radiogroup']").exists()).toBe(true)
        expect(wrapper.findAll(".card")).toHaveLength(2)
    })

    test("marks the selected option", () => {
        const wrapper = mount(KsRadioCardGroup, {
            props: {modelValue: "SERVER", options: OPTIONS},
            global: globalConfig,
        })
        const cards = wrapper.findAll(".card")
        expect(cards[0].classes()).toContain("selected")
        expect(cards[1].classes()).not.toContain("selected")
    })

    test("renders the hint when present", () => {
        const wrapper = mount(KsRadioCardGroup, {
            props: {modelValue: "SERVER", options: OPTIONS},
            global: globalConfig,
        })
        expect(wrapper.find(".hint").text()).toBe("shared token")
    })

    test("emits update:modelValue and change on selection", async () => {
        const wrapper = mount(KsRadioCardGroup, {
            props: {modelValue: "SERVER", options: OPTIONS},
            global: globalConfig,
        })
        await wrapper.findAll("input[type='radio']")[1].setValue()
        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["CLIENT"])
        expect(wrapper.emitted("change")?.at(-1)).toEqual(["CLIENT"])
    })

    test("disables an option and its input", () => {
        const wrapper = mount(KsRadioCardGroup, {
            props: {
                modelValue: "a",
                options: [{value: "a", label: "A"}, {value: "b", label: "B", disabled: true}],
            },
            global: globalConfig,
        })
        const disabledCard = wrapper.findAll(".card")[1]
        expect(disabledCard.classes()).toContain("disabled")
        expect(disabledCard.find("input").attributes("disabled")).toBeDefined()
    })
})

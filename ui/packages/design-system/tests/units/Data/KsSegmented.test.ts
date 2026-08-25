import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import Account from "vue-material-design-icons/Account.vue"
import KsSegmented from "../../../src/components/Data/KsSegmented.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsSegmented", () => {
    test("renders segmented element", () => {
        const wrapper = mount(KsSegmented, {
            props: {modelValue: "a", options: ["a", "b", "c"]},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-segmented").exists()).toBe(true)
    })

    test("disabled applies is-disabled class", () => {
        const wrapper = mount(KsSegmented, {
            props: {modelValue: "a", options: ["a", "b"], disabled: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-segmented.is-disabled").exists()).toBe(true)
    })

    test("small size applies correct class", () => {
        const wrapper = mount(KsSegmented, {
            props: {modelValue: "a", options: ["a", "b"], size: "small"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-segmented--small").exists()).toBe(true)
    })

    test("renders an option icon before its label", () => {
        const wrapper = mount(KsSegmented, {
            props: {
                modelValue: "user",
                options: [
                    {label: "User", value: "user", icon: Account},
                    {label: "Group", value: "group"},
                ],
            },
            global: globalConfig,
        })
        expect(wrapper.findComponent(Account).exists()).toBe(true)
        expect(wrapper.text()).toContain("User")
        expect(wrapper.text()).toContain("Group")
    })

    test("selected item gets is-selected class", () => {
        const wrapper = mount(KsSegmented, {
            props: {modelValue: "b", options: ["a", "b", "c"]},
            global: globalConfig,
        })
        const items = wrapper.findAll(".kel-segmented__item")
        const selectedItem = items.find(item => item.classes("is-selected"))
        expect(selectedItem).toBeTruthy()
    })
})

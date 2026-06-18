import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsCheckItem from "../../../src/components/Data/KsCheckItem.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsCheckItem", () => {
    test("renders the label from the slot", () => {
        const wrapper = mount(KsCheckItem, {slots: {default: "Passwords match"}, global: globalConfig})
        expect(wrapper.text()).toContain("Passwords match")
    })

    test("is not met by default", () => {
        const wrapper = mount(KsCheckItem, {global: globalConfig})
        expect(wrapper.classes()).not.toContain("is-met")
    })

    test("applies is-met when met", () => {
        const wrapper = mount(KsCheckItem, {props: {met: true}, global: globalConfig})
        expect(wrapper.classes()).toContain("is-met")
    })
})

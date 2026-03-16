import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsCard from "../../../src/components/Data/KsCard.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsCard", () => {
    test("renders card element", () => {
        const wrapper = mount(KsCard, {
            slots: {default: "Card content"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-card").exists()).toBe(true)
    })

    test("renders header slot", () => {
        const wrapper = mount(KsCard, {
            slots: {header: "My Header", default: "Content"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-card__header").exists()).toBe(true)
    })

    test("shadow prop is accepted", () => {
        const wrapper = mount(KsCard, {
            props: {shadow: "never"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-card").exists()).toBe(true)
    })
})

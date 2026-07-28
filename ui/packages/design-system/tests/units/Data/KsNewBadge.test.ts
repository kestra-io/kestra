import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsNewBadge from "../../../src/components/Data/KsNewBadge.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsNewBadge", () => {
    test("renders the ks-new-badge root with the default label", () => {
        const wrapper = mount(KsNewBadge, {global: globalConfig})
        const badge = wrapper.find(".ks-new-badge")
        expect(badge.exists()).toBe(true)
        expect(badge.text()).toBe("NEW")
    })

    test("renders the default slot in place of the label", () => {
        const wrapper = mount(KsNewBadge, {
            global: globalConfig,
            slots: {default: "Nouveau"},
        })
        expect(wrapper.find(".ks-new-badge").text()).toBe("Nouveau")
    })
})

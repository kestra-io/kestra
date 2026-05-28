import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsBadge from "../../../src/components/Data/KsBadge.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsBadge", () => {
    test("renders badge element", () => {
        const wrapper = mount(KsBadge, {
            props: {value: 5},
            slots: {default: "<button>Notifications</button>"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-badge").exists()).toBe(true)
    })

    test("renders badge value", () => {
        const wrapper = mount(KsBadge, {
            props: {value: 42},
            global: globalConfig,
        })
        expect(wrapper.text()).toContain("42")
    })

    test("isDot renders as dot", () => {
        const wrapper = mount(KsBadge, {
            props: {isDot: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-badge.is-dot").exists() || wrapper.find(".kel-badge__content.is-dot").exists()).toBe(true)
    })
})

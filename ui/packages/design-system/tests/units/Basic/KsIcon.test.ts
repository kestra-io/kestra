import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsIcon from "../../../src/components/Basic/KsIcon.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsIcon", () => {
    test("renders icon element", () => {
        const wrapper = mount(KsIcon, {
            slots: {default: "<svg></svg>"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-icon").exists()).toBe(true)
    })

    test("size prop sets style", () => {
        const wrapper = mount(KsIcon, {
            props: {size: 32},
            slots: {default: "<svg></svg>"},
            global: globalConfig,
        })
        const icon = wrapper.find(".kel-icon")
        expect(icon.exists()).toBe(true)
    })

    test("color prop sets color", () => {
        const wrapper = mount(KsIcon, {
            props: {color: "red"},
            slots: {default: "<svg></svg>"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-icon").exists()).toBe(true)
    })
})

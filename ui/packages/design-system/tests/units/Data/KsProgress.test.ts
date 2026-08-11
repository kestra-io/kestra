import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsProgress from "../../../src/components/Data/KsProgress.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsProgress", () => {
    test("renders progress element", () => {
        const wrapper = mount(KsProgress, {
            props: {percentage: 50},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-progress").exists()).toBe(true)
    })

    test("circle type applies correct class", () => {
        const wrapper = mount(KsProgress, {
            props: {percentage: 50, type: "circle"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-progress--circle").exists()).toBe(true)
    })

    test("success status applies correct class", () => {
        const wrapper = mount(KsProgress, {
            props: {percentage: 100, status: "success"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-progress.is-success").exists()).toBe(true)
    })

    test("renders with a custom radius and keeps the progress element", () => {
        const wrapper = mount(KsProgress, {
            props: {percentage: 50, radius: 81},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-progress").exists()).toBe(true)
    })

    test("radius is not forwarded as a DOM attribute", () => {
        const wrapper = mount(KsProgress, {
            props: {percentage: 50, radius: 81},
            global: globalConfig,
        })
        expect(wrapper.attributes("radius")).toBeUndefined()
    })
})

import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsSkeleton from "../../../src/components/Data/KsSkeleton.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsSkeleton", () => {
    test("renders skeleton element", () => {
        const wrapper = mount(KsSkeleton, {
            global: globalConfig,
        })
        expect(wrapper.find(".kel-skeleton").exists()).toBe(true)
    })

    test("animated prop applies animation class", () => {
        const wrapper = mount(KsSkeleton, {
            props: {animated: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-skeleton.is-animated").exists()).toBe(true)
    })

    test("when loading is false shows default slot content", () => {
        const wrapper = mount(KsSkeleton, {
            props: {loading: false},
            slots: {default: "<p>Loaded content</p>"},
            global: globalConfig,
        })
        expect(wrapper.text()).toContain("Loaded content")
    })
})

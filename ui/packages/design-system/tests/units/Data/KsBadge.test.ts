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



    test("applies the inline class only when inline is set", () => {
        const overlay = mount(KsBadge, {props: {value: 1}, global: globalConfig})
        expect(overlay.find(".kel-badge--inline").exists()).toBe(false)

        const inline = mount(KsBadge, {props: {value: 1, inline: true}, global: globalConfig})
        expect(inline.find(".kel-badge--inline").exists()).toBe(true)
    })

    test("does not forward inline to the underlying element", () => {
        const wrapper = mount(KsBadge, {
            props: {value: 1, inline: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-badge").attributes("inline")).toBeUndefined()
    })

    test("keeps a caller-provided class alongside the generated ones", () => {
        const wrapper = mount(KsBadge, {
            props: {value: 1, inline: true},
            attrs: {class: "count"},
            global: globalConfig,
        })
        const root = wrapper.find(".kel-badge")
        expect(root.classes()).toContain("count")
        expect(root.classes()).toContain("kel-badge--inline")
    })
})

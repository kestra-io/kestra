import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsId from "../../../src/components/Data/KsId.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsId", () => {
    test("renders code element", () => {
        const wrapper = mount(KsId, {
            props: {value: "abc12345"},
            global: globalConfig,
        })
        expect(wrapper.find("code").exists()).toBe(true)
    })

    test("shows full value when shrink is false", () => {
        const wrapper = mount(KsId, {
            props: {value: "abc123456789", shrink: false},
            global: globalConfig,
        })
        expect(wrapper.find("code").text()).toBe("abc123456789")
    })

    test("truncates value to default 8 chars when shrink is true", () => {
        const wrapper = mount(KsId, {
            props: {value: "abc123456789"},
            global: globalConfig,
        })
        expect(wrapper.find("code").text()).toBe("abc12345")
    })

    test("truncates value to custom size with ellipsis", () => {
        const wrapper = mount(KsId, {
            props: {value: "abc123456789", size: 6},
            global: globalConfig,
        })
        expect(wrapper.find("code").text()).toBe("abc123…")
    })

    test("renders full value when shorter than size", () => {
        const wrapper = mount(KsId, {
            props: {value: "abc"},
            global: globalConfig,
        })
        expect(wrapper.find("code").text()).toBe("abc")
    })

    test("renders empty string when no value", () => {
        const wrapper = mount(KsId, {
            props: {},
            global: globalConfig,
        })
        expect(wrapper.find("code").text()).toBe("")
    })

    test("emits click event when code is clicked", async () => {
        const wrapper = mount(KsId, {
            props: {value: "abc12345"},
            global: globalConfig,
        })
        await wrapper.find("code").trigger("click")
        expect(wrapper.emitted("click")).toBeTruthy()
    })

    test("applies clickable class when onClick listener is present", () => {
        const wrapper = mount(KsId, {
            props: {value: "abc12345"},
            attrs: {onClick: () => {}},
            global: globalConfig,
        })
        expect(wrapper.find("code").classes()).toContain("ks-id--clickable")
    })

    test("shows tooltip when value exceeds size", () => {
        const wrapper = mount(KsId, {
            props: {value: "abc123456789"},
            global: globalConfig,
        })
        expect(wrapper.findComponent({name: "KsTooltip"}).exists()).toBe(true)
    })

    test("does not show tooltip when value is within size", () => {
        const wrapper = mount(KsId, {
            props: {value: "abc"},
            global: globalConfig,
        })
        expect(wrapper.findComponent({name: "KsTooltip"}).exists()).toBe(false)
    })

    test("does not show tooltip when shrink is false", () => {
        const wrapper = mount(KsId, {
            props: {value: "abc123456789", shrink: false},
            global: globalConfig,
        })
        expect(wrapper.findComponent({name: "KsTooltip"}).exists()).toBe(false)
    })
})

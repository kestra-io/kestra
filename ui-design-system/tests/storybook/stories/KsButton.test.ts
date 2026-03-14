import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import ElementPlus from "element-plus"
import KsButton from "../../../src/components/KsButton/KsButton.vue"

const globalConfig = {plugins: [ElementPlus]}

describe("KsButton", () => {
    test("renders a button element", () => {
        const wrapper = mount(KsButton, {
            slots: {default: "Click me"},
            global: globalConfig,
        })
        expect(wrapper.find(".el-button").exists()).toBe(true)
        expect(wrapper.text()).toBe("Click me")
    })

    test("type prop applies the correct class", () => {
        const wrapper = mount(KsButton, {
            props: {type: "primary"},
            global: globalConfig,
        })
        expect(wrapper.find(".el-button--primary").exists()).toBe(true)
    })

    test("small size applies el-button--small class", () => {
        const wrapper = mount(KsButton, {
            props: {size: "small"},
            global: globalConfig,
        })
        expect(wrapper.find(".el-button--small").exists()).toBe(true)
    })

    test("disabled applies is-disabled class", () => {
        const wrapper = mount(KsButton, {
            props: {disabled: true},
            global: globalConfig,
        })
        expect(wrapper.find(".el-button.is-disabled").exists()).toBe(true)
    })

    test("loading applies is-loading class", () => {
        const wrapper = mount(KsButton, {
            props: {loading: true},
            global: globalConfig,
        })
        expect(wrapper.find(".el-button.is-loading").exists()).toBe(true)
    })

    test("plain applies is-plain class", () => {
        const wrapper = mount(KsButton, {
            props: {plain: true},
            global: globalConfig,
        })
        expect(wrapper.find(".el-button.is-plain").exists()).toBe(true)
    })

    test("round applies is-round class", () => {
        const wrapper = mount(KsButton, {
            props: {round: true},
            global: globalConfig,
        })
        expect(wrapper.find(".el-button.is-round").exists()).toBe(true)
    })

    test("circle applies is-circle class", () => {
        const wrapper = mount(KsButton, {
            props: {circle: true},
            global: globalConfig,
        })
        expect(wrapper.find(".el-button.is-circle").exists()).toBe(true)
    })

    test("emits click event when clicked", async () => {
        const wrapper = mount(KsButton, {
            slots: {default: "Click"},
            global: globalConfig,
        })
        await wrapper.find(".el-button").trigger("click")
        expect(wrapper.emitted("click")).toBeTruthy()
    })

    test("does not emit click when disabled", async () => {
        const wrapper = mount(KsButton, {
            props: {disabled: true},
            slots: {default: "Click"},
            global: globalConfig,
        })
        await wrapper.find(".el-button").trigger("click")
        expect(wrapper.emitted("click")).toBeFalsy()
    })

    test("link prop applies is-link class", () => {
        const wrapper = mount(KsButton, {
            props: {link: true},
            global: globalConfig,
        })
        expect(wrapper.find(".el-button.is-link").exists()).toBe(true)
    })

    test("text prop applies is-text class", () => {
        const wrapper = mount(KsButton, {
            props: {text: true},
            global: globalConfig,
        })
        expect(wrapper.find(".el-button.is-text").exists()).toBe(true)
    })
})

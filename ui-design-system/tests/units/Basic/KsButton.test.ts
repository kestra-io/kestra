import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsButton", () => {
    test("renders a button element", () => {
        const wrapper = mount(KsButton, {
            slots: {default: "Click me"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button").exists()).toBe(true)
        expect(wrapper.text()).toBe("Click me")
    })

    test("type prop applies the correct class", () => {
        const wrapper = mount(KsButton, {
            props: {type: "primary"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button--primary").exists()).toBe(true)
    })

    test("small size applies kel-button--small class", () => {
        const wrapper = mount(KsButton, {
            props: {size: "small"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button--small").exists()).toBe(true)
    })

    test("disabled applies is-disabled class", () => {
        const wrapper = mount(KsButton, {
            props: {disabled: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button.is-disabled").exists()).toBe(true)
    })

    test("loading applies is-loading class", () => {
        const wrapper = mount(KsButton, {
            props: {loading: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button.is-loading").exists()).toBe(true)
    })

    test("plain applies is-plain class", () => {
        const wrapper = mount(KsButton, {
            props: {plain: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button.is-plain").exists()).toBe(true)
    })

    test("round applies is-round class", () => {
        const wrapper = mount(KsButton, {
            props: {round: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button.is-round").exists()).toBe(true)
    })

    test("circle applies is-circle class", () => {
        const wrapper = mount(KsButton, {
            props: {circle: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button.is-circle").exists()).toBe(true)
    })

    test("emits click event when clicked", async () => {
        const wrapper = mount(KsButton, {
            slots: {default: "Click"},
            global: globalConfig,
        })
        await wrapper.find(".kel-button").trigger("click")
        expect(wrapper.emitted("click")).toBeTruthy()
    })

    test("does not emit click when disabled", async () => {
        const wrapper = mount(KsButton, {
            props: {disabled: true},
            slots: {default: "Click"},
            global: globalConfig,
        })
        await wrapper.find(".kel-button").trigger("click")
        expect(wrapper.emitted("click")).toBeFalsy()
    })

    test("link prop applies is-link class", () => {
        const wrapper = mount(KsButton, {
            props: {link: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button.is-link").exists()).toBe(true)
    })

    test("text prop applies is-text class", () => {
        const wrapper = mount(KsButton, {
            props: {text: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button.is-text").exists()).toBe(true)
    })
})

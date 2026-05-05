import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsIconButton from "../../../src/components/Basic/KsIconButton/KsIconButton.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

const StarIcon = {template: "<svg data-testid=\"star-icon\" viewBox=\"0 0 24 24\"><path d=\"M12 2l3.09 6.26L22 9.27l-5 4.87L18.18 21 12 17.77 5.82 21 7 14.14 2 9.27l6.91-1.01L12 2z\"/></svg>"}

describe("KsIconButton", () => {
    test("renders a button with ks-icon-button class", () => {
        const wrapper = mount(KsIconButton, {
            slots: {default: StarIcon},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-icon-button").exists()).toBe(true)
    })

    test("renders slot content", () => {
        const wrapper = mount(KsIconButton, {
            slots: {default: "<span data-testid=\"icon\">★</span>"},
            global: globalConfig,
        })
        expect(wrapper.find("[data-testid=icon]").exists()).toBe(true)
    })

    test("does not render tooltip when tooltip prop is empty", () => {
        const wrapper = mount(KsIconButton, {
            slots: {default: StarIcon},
            global: globalConfig,
        })
        expect(wrapper.findComponent({name: "ElTooltip"}).exists()).toBe(false)
    })

    test("renders tooltip when tooltip prop is provided", () => {
        const wrapper = mount(KsIconButton, {
            props: {tooltip: "Delete item"},
            slots: {default: StarIcon},
            global: globalConfig,
        })
        expect(wrapper.findComponent({name: "ElTooltip"}).exists()).toBe(true)
    })

    test("tooltip uses provided content", () => {
        const wrapper = mount(KsIconButton, {
            props: {tooltip: "Copy to clipboard"},
            slots: {default: StarIcon},
            global: globalConfig,
        })
        const tooltip = wrapper.findComponent({name: "ElTooltip"})
        expect(tooltip.props("content")).toBe("Copy to clipboard")
    })

    test("aria-label falls back to tooltip when ariaLabel is not set", () => {
        const wrapper = mount(KsIconButton, {
            props: {tooltip: "Delete"},
            slots: {default: StarIcon},
            global: globalConfig,
        })
        const button = wrapper.find(".ks-icon-button")
        expect(button.attributes("aria-label")).toBe("Delete")
    })

    test("aria-label uses ariaLabel prop when provided", () => {
        const wrapper = mount(KsIconButton, {
            props: {tooltip: "Delete", ariaLabel: "Delete this item"},
            slots: {default: StarIcon},
            global: globalConfig,
        })
        const button = wrapper.find(".ks-icon-button")
        expect(button.attributes("aria-label")).toBe("Delete this item")
    })

    test("disabled prop makes button disabled", () => {
        const wrapper = mount(KsIconButton, {
            props: {disabled: true},
            slots: {default: StarIcon},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-icon-button.is-disabled").exists()).toBe(true)
    })

    test("emits click event when clicked", async () => {
        const wrapper = mount(KsIconButton, {
            slots: {default: StarIcon},
            global: globalConfig,
        })
        await wrapper.find(".ks-icon-button").trigger("click")
        expect(wrapper.emitted("click")).toBeTruthy()
    })

    test("does not emit click when disabled", async () => {
        const wrapper = mount(KsIconButton, {
            props: {disabled: true},
            slots: {default: StarIcon},
            global: globalConfig,
        })
        await wrapper.find(".ks-icon-button").trigger("click")
        expect(wrapper.emitted("click")).toBeFalsy()
    })

    test("uses router-link tag when to prop is provided", () => {
        const wrapper = mount(KsIconButton, {
            props: {to: "/flows"},
            slots: {default: StarIcon},
            global: globalConfig,
        })
        // In jsdom (no vue-router), router-link renders as a custom element
        const button = wrapper.find(".ks-icon-button")
        expect(button.element.tagName.toLowerCase()).toBe("router-link")
    })

    test("does not navigate when disabled and to is set", () => {
        const wrapper = mount(KsIconButton, {
            props: {to: "/flows", disabled: true},
            slots: {default: StarIcon},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-icon-button.is-disabled").exists()).toBe(true)
    })

    test("passes extra attributes to the button", () => {
        const wrapper = mount(KsIconButton, {
            attrs: {"data-testid": "my-icon-btn"},
            slots: {default: StarIcon},
            global: globalConfig,
        })
        expect(wrapper.find("[data-testid=my-icon-btn]").exists()).toBe(true)
    })
})

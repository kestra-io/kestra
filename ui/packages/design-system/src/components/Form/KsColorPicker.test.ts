import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../index"
import {ElColorPicker} from "element-plus"
import KsColorPicker from "./KsColorPicker.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsColorPicker", () => {
    test("renders color-picker element", () => {
        const wrapper = mount(KsColorPicker, {
            props: {modelValue: "#409EFF"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-color-picker").exists()).toBe(true)
    })

    test("disabled applies is-disabled class", () => {
        const wrapper = mount(KsColorPicker, {
            props: {modelValue: "#409EFF", disabled: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-color-picker.is-disabled").exists()).toBe(true)
    })

    test("small size applies correct class", () => {
        const wrapper = mount(KsColorPicker, {
            props: {modelValue: "#409EFF", size: "small"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-color-picker--small").exists()).toBe(true)
    })

    test("alphaOnSurface marks the trigger and adds its class to the caller's popper class", () => {
        const wrapper = mount(KsColorPicker, {
            props: {modelValue: "rgba(5, 66, 182, 0.08)", showAlpha: true, alphaOnSurface: true},
            attrs: {popperClass: "caller-class"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-color-picker.ks-color-picker--alpha-on-surface").exists()).toBe(true)
        expect(wrapper.findComponent(ElColorPicker).props("popperClass")).toEqual(["caller-class", "ks-color-picker--alpha-on-surface"])
    })

    test("without alphaOnSurface the checkerboard is kept and the caller's popper class is untouched", () => {
        const wrapper = mount(KsColorPicker, {
            props: {modelValue: "rgba(5, 66, 182, 0.08)", showAlpha: true},
            attrs: {popperClass: "caller-class"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-color-picker--alpha-on-surface").exists()).toBe(false)
        expect(wrapper.findComponent(ElColorPicker).props("popperClass")).toBe("caller-class")
    })
})

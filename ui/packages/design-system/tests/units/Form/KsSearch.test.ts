import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsSearch from "../../../src/components/Form/KsSearch.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsSearch", () => {
    test("renders input element with ks-search class", () => {
        const wrapper = mount(KsSearch, {
            props: {placeholder: "Search..."},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-search").exists()).toBe(true)
        expect(wrapper.find(".kel-input").exists()).toBe(true)
    })

    test("renders default Magnify icon in prefix", () => {
        const wrapper = mount(KsSearch, {
            global: globalConfig,
        })
        expect(wrapper.find(".ks-search__icon").exists()).toBe(true)
    })

    test("custom prefix slot replaces the default icon", () => {
        const wrapper = mount(KsSearch, {
            global: globalConfig,
            slots: {prefix: "<span class=\"custom-prefix\">#</span>"},
        })
        expect(wrapper.find(".ks-search__icon").exists()).toBe(false)
        expect(wrapper.find(".custom-prefix").exists()).toBe(true)
    })

    test("disabled applies is-disabled class", () => {
        const wrapper = mount(KsSearch, {
            props: {disabled: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-input.is-disabled").exists()).toBe(true)
    })

    test("v-model two-way binding", async () => {
        const wrapper = mount(KsSearch, {
            props: {modelValue: "hello", "onUpdate:modelValue": (v: string) => wrapper.setProps({modelValue: v})},
            global: globalConfig,
        })
        const input = wrapper.find("input")
        expect((input.element as HTMLInputElement).value).toBe("hello")
        await input.setValue("world")
        expect(wrapper.props("modelValue")).toBe("world")
    })
})

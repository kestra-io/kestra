import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {h} from "vue"
import KestraDesignSystem from "../../../src/index"
import KsSelect from "../../../src/components/KsSelect/KsSelect.vue"
import KsOption from "../../../src/components/KsSelect/KsOption.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsSelect", () => {
    test("renders trigger with placeholder", () => {
        const wrapper = mount(KsSelect, {
            props: {placeholder: "Select a status"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-select").exists()).toBe(true)
        expect(wrapper.find(".kel-select__placeholder").text()).toBe("Select a status")
    })

    test("renders options via KsOption", () => {
        const wrapper = mount(KsSelect, {
            props: {placeholder: "Pick"},
            slots: {
                default: () => [
                    h(KsOption, {value: "A", label: "Option A"}),
                    h(KsOption, {value: "B", label: "Option B"}),
                ],
            },
            global: globalConfig,
        })
        expect(wrapper.find(".kel-select").exists()).toBe(true)
    })

    test("small size applies kel-select--small class", () => {
        const wrapper = mount(KsSelect, {
            props: {size: "small"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-select--small").exists()).toBe(true)
    })

    test("disabled applies is-disabled class", () => {
        const wrapper = mount(KsSelect, {
            props: {disabled: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-select__wrapper.is-disabled").exists()).toBe(true)
    })

    test("multiple mode renders select wrapper", () => {
        const wrapper = mount(KsSelect, {
            props: {multiple: true, placeholder: "Select statuses"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-select").exists()).toBe(true)
    })

    test("filterable mode renders input", () => {
        const wrapper = mount(KsSelect, {
            props: {filterable: true, placeholder: "Filter…"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-select").exists()).toBe(true)
    })
})

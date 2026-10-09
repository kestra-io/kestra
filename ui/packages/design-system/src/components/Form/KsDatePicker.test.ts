import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../index"
import KsDatePicker from "./KsDatePicker.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsDatePicker", () => {
    test("renders date picker element", () => {
        const wrapper = mount(KsDatePicker, {
            global: globalConfig,
        })
        expect(wrapper.find(".kel-date-editor").exists()).toBe(true)
    })

    test("accepts type prop", () => {
        const wrapper = mount(KsDatePicker, {
            props: {type: "daterange"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-date-editor").exists()).toBe(true)
    })

    test("accepts disabled prop", () => {
        const wrapper = mount(KsDatePicker, {
            props: {disabled: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-date-editor").exists()).toBe(true)
    })
})

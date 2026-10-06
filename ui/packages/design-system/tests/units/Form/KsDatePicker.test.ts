import {describe, test, expect} from "vitest"
import {nextTick} from "vue"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsDatePicker from "../../../src/components/Form/KsDatePicker.vue"

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

    test("forwards data and aria attributes to the input rather than to the tooltip", () => {
        const wrapper = mount(KsDatePicker, {
            attrs: {"data-test": "cell", "aria-invalid": "true", "aria-describedby": "cell-error", "aria-label": "Deadline"},
            global: globalConfig,
        })
        const input = wrapper.find("input")
        expect(input.attributes("data-test")).toBe("cell")
        expect(input.attributes("aria-invalid")).toBe("true")
        expect(input.attributes("aria-describedby")).toBe("cell-error")
        expect(input.attributes("aria-label")).toBe("Deadline")
    })

    test("removes a forwarded attribute once it is no longer passed", async () => {
        const wrapper = mount(KsDatePicker, {
            attrs: {"aria-invalid": "true"},
            global: globalConfig,
        })
        await wrapper.setProps({"aria-invalid": undefined})
        await nextTick()
        expect(wrapper.find("input").attributes("aria-invalid")).toBeUndefined()
    })
})

import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsDrillRow from "../../../src/components/Navigation/KsDrillRow/KsDrillRow.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsDrillRow", () => {
    test("renders label, type chip and preview", () => {
        const wrapper = mount(KsDrillRow, {
            props: {label: "country", type: "array<object>", preview: "SELECT · 3 values"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-drill-row__label").text()).toBe("country")
        expect(wrapper.find(".kel-drill-row__type").text()).toBe("array<object>")
        expect(wrapper.find(".kel-drill-row__preview").text()).toBe("SELECT · 3 values")
    })

    test("is a button and emits open on click", async () => {
        const wrapper = mount(KsDrillRow, {props: {label: "retry"}, global: globalConfig})
        expect(wrapper.element.tagName).toBe("BUTTON")
        await wrapper.trigger("click")
        expect(wrapper.emitted("open")).toHaveLength(1)
    })

    test("does not emit open when disabled", async () => {
        const wrapper = mount(KsDrillRow, {props: {label: "retry", disabled: true}, global: globalConfig})
        expect(wrapper.attributes("disabled")).toBeDefined()
        await wrapper.trigger("click")
        expect(wrapper.emitted("open")).toBeUndefined()
    })

    test("uses ariaLabel when given, falls back to label", () => {
        const withAria = mount(KsDrillRow, {props: {label: "country", ariaLabel: "Open country input"}, global: globalConfig})
        expect(withAria.attributes("aria-label")).toBe("Open country input")

        const fallback = mount(KsDrillRow, {props: {label: "country"}, global: globalConfig})
        expect(fallback.attributes("aria-label")).toBe("country")
    })

    test("default slot overrides the preview prop", () => {
        const wrapper = mount(KsDrillRow, {
            props: {label: "conditions", preview: "ignored"},
            slots: {default: "2 conditions set"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-drill-row__preview").text()).toBe("2 conditions set")
    })

    test("omits the type chip when no type is given", () => {
        const wrapper = mount(KsDrillRow, {props: {label: "retry"}, global: globalConfig})
        expect(wrapper.find(".kel-drill-row__type").exists()).toBe(false)
    })
})

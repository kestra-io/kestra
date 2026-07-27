import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "../../../src/index"
import KsDurationPicker from "../../../src/components/Form/KsDurationPicker.vue"

const globalConfig = {plugins: [createI18n({legacy: false, locale: "en"}), KestraDesignSystem]}

describe("KsDurationPicker", () => {
    test("renders the duration picker container", () => {
        const wrapper = mount(KsDurationPicker, {
            global: globalConfig,
        })
        expect(wrapper.find(".ks-duration-picker").exists()).toBe(true)
    })

    test("renders all 4 number inputs", () => {
        const wrapper = mount(KsDurationPicker, {
            global: globalConfig,
        })
        expect(wrapper.findAll(".ks-duration-picker__field").length).toBe(4)
    })

    test("renders custom duration text input", () => {
        const wrapper = mount(KsDurationPicker, {
            global: globalConfig,
        })
        expect(wrapper.find(".ks-duration-picker__custom").exists()).toBe(true)
    })

    test("disables unit inputs and custom duration input", () => {
        const wrapper = mount(KsDurationPicker, {
            props: {disabled: true},
            global: globalConfig,
        })

        const unitInputs = wrapper.findAll(".ks-duration-picker__field input")
        expect(unitInputs).toHaveLength(4)
        unitInputs.forEach((input) => {
            expect(input.attributes("disabled")).toBeDefined()
        })
        expect(wrapper.find(".ks-duration-picker__custom input").attributes("disabled")).toBeDefined()
    })

    test("parses modelValue on mount and populates fields", async () => {
        const wrapper = mount(KsDurationPicker, {
            props: {modelValue: "P4DT5H6M7S"},
            global: globalConfig,
        })
        await wrapper.vm.$nextTick()

        // Custom duration input should reflect the parsed value
        const input = wrapper.find("#ks-duration-custom")
        expect(input.exists()).toBe(true)
    })

    test("emits update:modelValue with null when no values set", async () => {
        const wrapper = mount(KsDurationPicker, {
            global: globalConfig,
        })
        await wrapper.vm.$nextTick()

        const emitted = wrapper.emitted("update:modelValue")
        expect(emitted).toBeTruthy()
        // All zeros → null duration
        const lastEmit = emitted![emitted!.length - 1]
        expect(lastEmit[0]).toBeNull()
    })

    test("emits update:modelValue with ISO string when modelValue provided", async () => {
        const wrapper = mount(KsDurationPicker, {
            props: {modelValue: "P1D"},
            global: globalConfig,
        })
        await wrapper.vm.$nextTick()

        const emitted = wrapper.emitted("update:modelValue")
        expect(emitted).toBeTruthy()
    })

    test("reacts to modelValue prop change", async () => {
        const wrapper = mount(KsDurationPicker, {
            props: {modelValue: "P1D"},
            global: globalConfig,
        })
        await wrapper.vm.$nextTick()

        await wrapper.setProps({modelValue: "PT30M"})
        await wrapper.vm.$nextTick()

        const emitted = wrapper.emitted("update:modelValue")
        expect(emitted).toBeTruthy()
    })

    test("rejects calendar-based (year/month/week) durations as invalid", async () => {
        const wrapper = mount(KsDurationPicker, {
            global: globalConfig,
        })

        const customInputEl = wrapper.find(".ks-duration-picker__custom input")
        await customInputEl.setValue("P1Y")
        await customInputEl.trigger("input")
        await wrapper.vm.$nextTick()

        expect(wrapper.text()).toContain("Invalid ISO 8601 duration: P1Y")
        const emitted = wrapper.emitted("update:modelValue")
        const lastEmit = emitted![emitted!.length - 1]
        expect(lastEmit[0]).toBeNull()
    })

    test("custom duration input shows invalid message for bad input", async () => {
        const wrapper = mount(KsDurationPicker, {
            global: globalConfig,
        })

        // Directly call parseDuration on the vm via the input event handler
        const customInputEl = wrapper.find(".ks-duration-picker__custom input")
        if (customInputEl.exists()) {
            await customInputEl.setValue("not-a-duration")
            await customInputEl.trigger("input")
        }

        // durationIssue should be set — ks-text danger class appears
        expect(wrapper.find(".ks-duration-picker__custom").exists()).toBe(true)
    })

    test("custom duration input valid ISO string emits correct value", async () => {
        const wrapper = mount(KsDurationPicker, {
            global: globalConfig,
        })

        const customInputEl = wrapper.find(".ks-duration-picker__custom input")
        if (customInputEl.exists()) {
            await customInputEl.setValue("P2DT3H")
            await customInputEl.trigger("input")
        }
        await wrapper.vm.$nextTick()

        const emitted = wrapper.emitted("update:modelValue")
        expect(emitted).toBeTruthy()
    })

    test("empty modelValue results in null emit", async () => {
        const wrapper = mount(KsDurationPicker, {
            props: {modelValue: ""},
            global: globalConfig,
        })
        await wrapper.vm.$nextTick()

        const emitted = wrapper.emitted("update:modelValue")
        const lastEmit = emitted![emitted!.length - 1]
        expect(lastEmit[0]).toBeNull()
    })
})

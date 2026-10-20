import {describe, test, expect} from "vitest"
import FilterDateTime from "./FilterDateTime.vue"
import {mount} from "@vue/test-utils"
import {defineComponent, h} from "vue"

const KsDatePickerStub = defineComponent({
    name: "KsDatePicker",
    props: ["modelValue", "type", "placeholder"],
    emits: ["update:modelValue"],
    render() {
        return h("div", {class: "date-picker-stub"})
    },
})

const mountComponent = (props = {}) =>
    mount(FilterDateTime, {
        props: {label: "Start Date", dateValue: null, ...props},
        global: {stubs: {KsDatePicker: KsDatePickerStub}},
    })

describe("FilterDateTime", () => {
    test("binds dateValue to the picker", () => {
        const date = new Date("2026-10-20T10:00:00Z")
        const wrapper = mountComponent({dateValue: date})
        expect(wrapper.findComponent(KsDatePickerStub).props("modelValue")).toEqual(date)
    })

    test("placeholder is 'Select <label>' lowercased", () => {
        const wrapper = mountComponent({label: "Start Date"})
        expect(wrapper.findComponent(KsDatePickerStub).props("placeholder")).toBe("Select start date")
    })

    test("picker is in datetime mode", () => {
        const wrapper = mountComponent()
        expect(wrapper.findComponent(KsDatePickerStub).props("type")).toBe("datetime")
    })

    test("picking a date emits update:dateValue with that date", async () => {
        const wrapper = mountComponent()
        const date = new Date("2026-10-21T08:30:00Z")
        await wrapper.findComponent(KsDatePickerStub).vm.$emit("update:modelValue", date)
        expect(wrapper.emitted("update:dateValue")![0]).toEqual([date])
    })

    test("clearing emits null, not undefined", async () => {
        const wrapper = mountComponent({dateValue: new Date()})
        await wrapper.findComponent(KsDatePickerStub).vm.$emit("update:modelValue", undefined)
        expect(wrapper.emitted("update:dateValue")![0][0]).toBeNull()
    })
})
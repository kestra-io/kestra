import {describe, expect, test} from "vitest"
import LogLevelSelector from "./LogLevelSelector.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

describe("LogLevelSelector", () => {
    test("renders the five levels as options, in order", () => {
        const wrapper = i18nMount(LogLevelSelector)
        const options = wrapper.findAllComponents({name: "KsOption"})

        expect(options).toHaveLength(5)
        expect(options.map(option => option.props("value"))).toEqual([
            "TRACE",
            "DEBUG",
            "INFO",
            "WARN",
            "ERROR",
        ])
    })

    test("binds the current value to the select", () => {
        const wrapper = i18nMount(LogLevelSelector, {props: {value: "WARN"}})
        const select = wrapper.findComponent({name: "KsSelect"})

        expect(select.props("modelValue")).toBe("WARN")
    })

    test("defaults value to INFO", () => {
        const wrapper = i18nMount(LogLevelSelector)
        const select = wrapper.findComponent({name: "KsSelect"})

        expect(select.props("modelValue")).toBe("INFO")
    })

    test("emits update:modelValue with the selected level", () => {
        const wrapper = i18nMount(LogLevelSelector)
        const select = wrapper.findComponent({name: "KsSelect"})

        select.vm.$emit("update:modelValue", "DEBUG")

        expect(wrapper.emitted("update:modelValue")).toEqual([["DEBUG"]])
    })

    test("emits nothing on an undefined selection", () => {
        const wrapper = i18nMount(LogLevelSelector)
        const select = wrapper.findComponent({name: "KsSelect"})

        select.vm.$emit("update:modelValue", undefined)

        expect(wrapper.emitted("update:modelValue")).toBeUndefined()
    })

    test("configures the select as filterable", () => {
        const wrapper = i18nMount(LogLevelSelector)
        const select = wrapper.findComponent({name: "KsSelect"})

        expect(select.props("filterable")).toBe(true)
    })

    test("forwards the fit prop to the select", () => {
        const wrapper = i18nMount(LogLevelSelector, {props: {fit: true}})
        const select = wrapper.findComponent({name: "KsSelect"})

        expect(select.props("fit")).toBe(true)
    })
})

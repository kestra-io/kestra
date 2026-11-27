import {describe, expect, test} from "vitest"
import LogLevelSelector from "./LogLevelSelector.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

describe("LogLevelSelector", () => {
    test("renders the five levels as options, in order", () => {
        const wrapper = i18nMount(LogLevelSelector)
        const options = wrapper.findAllComponents({name: "KsOption"})

        expect(options).toHaveLength(5)
        expect(options.map(option => option.props("label"))).toEqual(["TRACE", "DEBUG", "INFO", "WARN", "ERROR"])
        expect(options.map(option => option.props("value"))).toEqual([
            "TRACE",
            "DEBUG",
            "INFO",
            "WARN",
            "ERROR",
        ])
    })

    test("defaults value to INFO", () => {
        const wrapper = i18nMount(LogLevelSelector)

        expect(wrapper.props("value")).toBe("INFO")
        expect(wrapper.findComponent({name: "KsSelect"}).props("modelValue")).toBe("INFO")
    })

    test("emits update:modelValue with the selected level", () => {
        const wrapper = i18nMount(LogLevelSelector)
        const select = wrapper.findComponent({name: "KsSelect"})

        select.vm.$emit("update:modelValue", "DEBUG")

        expect(wrapper.emitted("update:modelValue")).toEqual([["DEBUG"]])

        select.vm.$emit("update:modelValue", "ERROR")

        expect(wrapper.emitted("update:modelValue")).toEqual([["DEBUG"], ["ERROR"]])
    })

    test("emits nothing on an undefined selection", () => {
        const wrapper = i18nMount(LogLevelSelector)
        const select = wrapper.findComponent({name: "KsSelect"})

        select.vm.$emit("update:modelValue", undefined)

        expect(wrapper.emitted("update:modelValue")).toBeUndefined()
    })

    test("binds the current value and follows changes", async () => {
        const wrapper = i18nMount(LogLevelSelector, {props: {value: "WARN"}})
        const select = wrapper.findComponent({name: "KsSelect"})
        expect(select.props("modelValue")).toBe("WARN")

        await wrapper.setProps({value: "DEBUG"})

        expect(select.props("modelValue")).toBe("DEBUG")
    })

    test("enables filtering", () => {
        const wrapper = i18nMount(LogLevelSelector)

        expect(wrapper.findComponent({name: "KsSelect"}).props("filterable")).toBe(true)
    })

    test.each([true, false])("forwards fit=%s", (fit) => {
        const wrapper = i18nMount(LogLevelSelector, {props: {fit}})

        expect(wrapper.findComponent({name: "KsSelect"}).props("fit")).toBe(fit)
    })
})

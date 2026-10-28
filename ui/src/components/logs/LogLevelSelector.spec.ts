import {describe, expect, it} from "vitest"
import {KsOption} from "@kestra-io/design-system"
import {i18nMount} from "../../../tests/unit/i18nMount"
import LogLevelSelector from "./LogLevelSelector.vue"

describe("LogLevelSelector", () => {
    it("renders the five levels in order", () => {
        const wrapper = i18nMount(LogLevelSelector)

        expect(wrapper.findAllComponents(KsOption).map(option => ({
            label: option.props("label"),
            value: option.props("value"),
        }))).toEqual(["TRACE", "DEBUG", "INFO", "WARN", "ERROR"].map(level => ({label: level, value: level})))
    })

    it("binds the current value and follows changes", async () => {
        const wrapper = i18nMount(LogLevelSelector, {props: {value: "WARN"}})
        const select = wrapper.findComponent({name: "KsSelect"})
        expect(select.props("modelValue")).toBe("WARN")

        await wrapper.setProps({value: "DEBUG"})

        expect(select.props("modelValue")).toBe("DEBUG")
    })

    it("defaults to INFO", () => {
        const wrapper = i18nMount(LogLevelSelector)

        expect(wrapper.findComponent({name: "KsSelect"}).props("modelValue")).toBe("INFO")
    })

    it("emits the selected level", () => {
        const wrapper = i18nMount(LogLevelSelector)

        wrapper.findComponent({name: "KsSelect"}).vm.$emit("update:modelValue", "ERROR")

        expect(wrapper.emitted("update:modelValue")).toEqual([["ERROR"]])
    })

    it("does not emit when the selection is undefined", () => {
        const wrapper = i18nMount(LogLevelSelector)

        wrapper.findComponent({name: "KsSelect"}).vm.$emit("update:modelValue", undefined)

        expect(wrapper.emitted("update:modelValue")).toBeUndefined()
    })

    it("enables filtering", () => {
        const wrapper = i18nMount(LogLevelSelector)

        expect(wrapper.findComponent({name: "KsSelect"}).props("filterable")).toBe(true)
    })

    it.each([true, false])("forwards fit=%s", (fit) => {
        const wrapper = i18nMount(LogLevelSelector, {props: {fit}})

        expect(wrapper.findComponent({name: "KsSelect"}).props("fit")).toBe(fit)
    })
})

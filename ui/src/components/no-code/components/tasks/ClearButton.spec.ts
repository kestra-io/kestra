import {describe, expect, test} from "vitest"
import {i18nMount} from "../../../../../tests/unit/i18nMount"
import ClearButton from "./ClearButton.vue"
import en from "../../../../translations/en.json"

function mountClearButton() {
    return i18nMount(ClearButton, {locales: en})
}

describe("ClearButton", () => {
    test("emits click when clicked", async () => {
        const wrapper = mountClearButton()

        await wrapper.get("button").trigger("click")

        expect(wrapper.emitted("click")).toHaveLength(1)
    })

    test("is a plain button so it never submits the surrounding form", () => {
        const wrapper = mountClearButton()

        expect(wrapper.get("button").attributes("type")).toBe("button")
    })

    test("renders the translated clear selection label", () => {
        const wrapper = mountClearButton()

        expect(wrapper.text()).toBe(en.en.no_code.clearSelection)
    })

    test("renders the close icon", () => {
        const wrapper = mountClearButton()

        expect(wrapper.find(".clear-icon").exists()).toBe(true)
        expect(wrapper.find(".clear-icon svg").exists()).toBe(true)
    })

    test("resolves the translation key instead of rendering it raw", () => {
        const wrapper = mountClearButton()

        expect(wrapper.text()).not.toContain("no_code.clearSelection")
    })

    test("emits nothing on mount", () => {
        const wrapper = mountClearButton()

        expect(wrapper.emitted("click")).toBeUndefined()
    })
})

import {describe, expect, test, vi} from "vitest"
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

    test("does not submit the surrounding form when clicked", async () => {
        const onSubmit = vi.fn()
        const wrapper = i18nMount({
            components: {ClearButton},
            template: "<form @submit.prevent=\"onSubmit\"><ClearButton /></form>",
            setup: () => ({onSubmit}),
        }, {locales: en, attachTo: document.body})

        await wrapper.get("button").trigger("click")

        expect(onSubmit).not.toHaveBeenCalled()
        wrapper.unmount()
    })

    test("renders the translated clear selection label", () => {
        const wrapper = mountClearButton()

        expect(wrapper.text()).toBe(en.en.no_code.clearSelection)
    })
})

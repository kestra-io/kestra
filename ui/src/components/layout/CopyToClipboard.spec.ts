import {afterEach, describe, expect, test, vi} from "vitest"
import {i18nMount} from "../../../tests/unit/i18nMount"
import CopyToClipboard from "./CopyToClipboard.vue"
import * as Utils from "../../utils/utils"

describe("CopyToClipboard", () => {
    const mountComponent = (props: {text: string; label?: string; link?: boolean} = {text: "sample-token"}, slots = {}) =>
        i18nMount(CopyToClipboard, {props, slots})

    afterEach(() => {
        vi.restoreAllMocks()
    })

    test("clicking the button calls the copy helper with text", async () => {
        const copySpy = vi.spyOn(Utils, "copy").mockResolvedValue(undefined)
        const wrapper = mountComponent({text: "secret-key"})

        await wrapper.find("button").trigger("click")

        expect(copySpy).toHaveBeenCalledWith("secret-key")
    })

    test("the label is rendered when label is set", () => {
        const wrapper = mountComponent({text: "payload", label: "Copy Secret"})

        expect(wrapper.text()).toContain("Copy Secret")
    })

    test("copy-icon-button is applied when there is no label, and absent when there is one", () => {
        const withoutLabel = mountComponent({text: "payload"})
        expect(withoutLabel.find("button").classes()).toContain("copy-icon-button")

        const withLabel = mountComponent({text: "payload", label: "Copy"})
        expect(withLabel.find("button").classes()).not.toContain("copy-icon-button")
    })

    test("the right slot is rendered", () => {
        const wrapper = mountComponent(
            {text: "payload"},
            {right: "<span data-test=\"custom-action\">Extra Action</span>"},
        )

        expect(wrapper.find("[data-test=\"custom-action\"]").exists()).toBe(true)
        expect(wrapper.text()).toContain("Extra Action")
    })

    test("the button carries an aria-label", () => {
        const wrapper = mountComponent({text: "payload"})

        expect(wrapper.find("button").attributes("aria-label")).toBe("copy_to_clipboard")
    })
})

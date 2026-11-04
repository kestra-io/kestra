import {describe, expect, test} from "vitest"
import {KsCard} from "@kestra-io/design-system"
import {i18nMount} from "../../../../../tests/unit/i18nMount"
import Block from "./Block.vue"

const mountBlock = (heading: string, slot = "<p>Settings content</p>") => i18nMount(Block, {
    props: {heading},
    slots: {default: slot},
})

describe("Block", () => {
    test("renders the heading as an h2", () => {
        const wrapper = mountBlock("General settings")

        expect(wrapper.get("h2").text()).toBe("General settings")
    })

    test("renders the default slot inside the card", () => {
        const wrapper = mountBlock("General settings")

        expect(wrapper.findComponent(KsCard).get("p").text()).toBe("Settings content")
    })

    test("renders the card without shadow and without body padding", () => {
        const wrapper = mountBlock("General settings")

        expect(wrapper.findComponent(KsCard).props("shadow")).toBe("never")
        // The slot content sits directly in the card body, so its parent carries the padding that reaches the user.
        expect(wrapper.get("p").element.parentElement?.style.padding).toBe("0px")
    })

    test("renders an empty heading element", () => {
        const wrapper = mountBlock("")

        expect(wrapper.find("h2").exists()).toBe(true)
        expect(wrapper.get("h2").text()).toBe("")
    })
})

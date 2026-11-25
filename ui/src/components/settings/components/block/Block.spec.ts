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

    test("renders an empty heading element", () => {
        const wrapper = mountBlock("")

        expect(wrapper.find("h2").exists()).toBe(true)
        expect(wrapper.get("h2").text()).toBe("")
    })
})

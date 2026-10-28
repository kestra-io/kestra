import {defineComponent, h} from "vue"
import {mount} from "@vue/test-utils"
import {describe, expect, test} from "vitest"
import Block from "./Block.vue"

const KsTextStub = defineComponent({
    props: ["tag"],
    template: "<component :is=\"tag\"><slot /></component>",
})

const KsCardStub = defineComponent({
    props: ["shadow", "bodyStyle"],
    setup(_, {slots}) {
        return () => h("div", {"data-test": "card"}, slots.default?.())
    },
})

const mountBlock = (heading: string, slot = "Settings content") => mount(Block, {
    props: {heading},
    slots: {default: slot},
    global: {stubs: {KsText: KsTextStub, KsCard: KsCardStub}},
})

describe("Block", () => {
    test("renders the heading as an h2", () => {
        const wrapper = mountBlock("General settings")

        expect(wrapper.find("h2").text()).toBe("General settings")
    })

    test("renders the default slot inside the card", () => {
        const wrapper = mountBlock("General settings")

        expect(wrapper.get('[data-test="card"]').text()).toBe("Settings content")
    })

    test("configures the card without shadow or body padding", () => {
        const wrapper = mountBlock("General settings")
        const card = wrapper.findComponent(KsCardStub)

        expect(card.props("shadow")).toBe("never")
        expect(card.props("bodyStyle")).toEqual({padding: "0"})
    })

    test("renders an empty heading element", () => {
        const wrapper = mountBlock("")

        expect(wrapper.find("h2").exists()).toBe(true)
        expect(wrapper.find("h2").text()).toBe("")
    })
})

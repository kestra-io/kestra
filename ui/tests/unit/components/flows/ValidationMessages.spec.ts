import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import ValidationMessages from "../../../../src/components/flows/ValidationMessages.vue"

const stubs = {KsText: {template: "<span class=\"ks-text-stub\"><slot/></span>"}}

describe("ValidationMessages", () => {
    test("renders one danger message per entry, in order", () => {
        const wrapper = mount(ValidationMessages, {
            props: {messages: ["first message", "second message"]},
            global: {stubs},
        })
        const rendered = wrapper.findAll(".ks-text-stub")
        expect(rendered).toHaveLength(2)
        expect(rendered.map(m => m.text())).toEqual(["first message", "second message"])
    })

    test("renders nothing when there are no messages", () => {
        const wrapper = mount(ValidationMessages, {
            props: {messages: []},
            global: {stubs},
        })
        expect(wrapper.find(".validation-messages").exists()).toBe(false)
        expect(wrapper.find(".ks-text-stub").exists()).toBe(false)
    })
})

import {describe, expect, test} from "vitest"
import {KsText} from "@kestra-io/design-system"
import {i18nMount} from "../../../tests/unit/i18nMount"
import ValidationMessages from "./ValidationMessages.vue"

const mountMessages = (messages: string[]) => i18nMount(ValidationMessages, {props: {messages}})

describe("ValidationMessages", () => {
    test("renders one line per message, in order", () => {
        const messages = ["First validation error", "Second validation error", "Third validation error"]
        const wrapper = mountMessages(messages)

        const lines = wrapper.findAllComponents(KsText)
        expect(lines).toHaveLength(3)
        expect(lines.map((line) => line.text())).toEqual(messages)
    })

    test("renders each line as danger-type small text", () => {
        const wrapper = mountMessages(["Invalid flow configuration", "Missing required property"])

        const lines = wrapper.findAllComponents(KsText)
        expect(lines).toHaveLength(2)
        for (const line of lines) {
            expect(line.props("type")).toBe("danger")
            expect(line.props("size")).toBe("small")
        }
    })

    test("renders no wrapper element at all for an empty array", () => {
        const wrapper = mountMessages([])

        expect(wrapper.find(".validation-messages").exists()).toBe(false)
        expect(wrapper.html()).toBe("<!--v-if-->")
    })

    test("renders duplicate messages when provided", () => {
        const messages = ["Duplicate validation error", "Duplicate validation error"]
        const wrapper = mountMessages(messages)

        const lines = wrapper.findAllComponents(KsText)
        expect(lines).toHaveLength(2)
        expect(lines.map((line) => line.text())).toEqual(messages)
    })
})

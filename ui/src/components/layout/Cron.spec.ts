import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import Cron from "./Cron.vue"

vi.mock("../../utils/utils", () => ({
    getLang: vi.fn().mockReturnValue("en"),
}))

describe("Cron", () => {
    it("shouldRenderHumanReadableTextForValidExpression", () => {
        const wrapper = mount(Cron, {props: {cronExpression: "0 * * * *"}})

        expect(wrapper.text()).toContain("Every hour")
    })

    it("shouldUseLocaleFromGetLang", async () => {
        const {getLang} = await import("../../utils/utils")
        const wrapper = mount(Cron, {props: {cronExpression: "0 9 * * 1"}})

        expect(getLang).toHaveBeenCalled()
        expect(wrapper.text().length).toBeGreaterThan(0)
    })

    it("shouldReRenderWhenExpressionChanges", async () => {
        const wrapper = mount(Cron, {props: {cronExpression: "0 * * * *"}})
        const initialText = wrapper.text()

        await wrapper.setProps({cronExpression: "0 9 * * 1"})

        expect(wrapper.text()).not.toBe(initialText)
    })

    it("shouldThrowWhenMountedWithInvalidExpression", () => {
        expect(() => {
            mount(Cron, {props: {cronExpression: "not-a-cron"}})
        }).toThrow()
    })
})

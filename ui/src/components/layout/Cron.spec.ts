import {afterEach, describe, expect, test, vi} from "vitest"
import {mount} from "@vue/test-utils"
import cronstrue from "cronstrue"
import Cron from "./Cron.vue"
import * as Utils from "../../utils/utils"

describe("Cron", () => {
    afterEach(() => {
        vi.restoreAllMocks()
    })

    test("renders a human-readable description for a valid cron expression", () => {
        vi.spyOn(Utils, "getLang").mockReturnValue("en")
        const wrapper = mount(Cron, {props: {cronExpression: "* * * * *"}})

        expect(wrapper.text()).toContain("minute")
    })

    test("passes the configured language to cronstrue as locale", () => {
        const cronstrueSpy = vi.spyOn(cronstrue, "toString")
        vi.spyOn(Utils, "getLang").mockReturnValue("fr")

        const wrapper = mount(Cron, {props: {cronExpression: "* * * * *"}})

        expect(cronstrueSpy).toHaveBeenCalledWith(
            "* * * * *",
            expect.objectContaining({locale: "fr"}),
        )
        expect(wrapper.text()).toBe("Toutes les minutes")
    })

    test("updates the rendered description when cronExpression prop changes", async () => {
        vi.spyOn(Utils, "getLang").mockReturnValue("en")
        const wrapper = mount(Cron, {props: {cronExpression: "* * * * *"}})
        expect(wrapper.text()).toBe(cronstrue.toString("* * * * *", {locale: "en"}))

        await wrapper.setProps({cronExpression: "0 0 * * *"})

        expect(wrapper.text()).toBe(cronstrue.toString("0 0 * * *", {locale: "en"}))
    })

    test("falls back to the raw expression when the cron expression is invalid", () => {
        vi.spyOn(Utils, "getLang").mockReturnValue("en")
        const wrapper = mount(Cron, {props: {cronExpression: "invalid-cron-expression"}})

        expect(wrapper.text()).toBe("invalid-cron-expression")
    })
})

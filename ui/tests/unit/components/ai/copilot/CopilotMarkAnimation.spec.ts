import {describe, it, expect} from "vitest"
import {mount} from "@vue/test-utils"
import CopilotMarkAnimation from "../../../../../src/components/ai/copilot/CopilotMarkAnimation.vue"

describe("CopilotMarkAnimation", () => {
    it("renders three decorative brand dots, hidden from the a11y tree", () => {
        const w = mount(CopilotMarkAnimation, {props: {phase: "thinking"}})
        expect(w.find(".copilot-mark").attributes("aria-hidden")).toBe("true")
        expect(w.findAll("circle")).toHaveLength(3)
    })

    it.each(["thinking", "answering", "end"] as const)("applies the %s phase class", (phase) => {
        const w = mount(CopilotMarkAnimation, {props: {phase}})
        expect(w.find(".copilot-mark").classes()).toContain(`copilot-mark-${phase}`)
    })
})

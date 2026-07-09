import {describe, it, expect} from "vitest"
import {mount} from "@vue/test-utils"
import CopilotThinking from "../../../../../src/components/ai/copilot/CopilotThinking.vue"
import {mountGlobal} from "./_helpers"

describe("CopilotThinking", () => {
    it("renders the thinking label with an animated dots element", () => {
        const w = mount(CopilotThinking, {global: mountGlobal})
        expect(w.find("[data-test=\"copilot-thinking\"]").exists()).toBe(true)
        expect(w.text()).toContain("Thinking")
        // The rising dots are a decorative, aria-hidden pseudo-element host.
        const dots = w.find(".copilot-thinking-dots")
        expect(dots.exists()).toBe(true)
        expect(dots.attributes("aria-hidden")).toBe("true")
    })
})

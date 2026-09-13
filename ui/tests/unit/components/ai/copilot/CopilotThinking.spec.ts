import {describe, it, expect, vi, afterEach} from "vitest"
import {mount} from "@vue/test-utils"
import CopilotThinking from "../../../../../src/components/ai/copilot/CopilotThinking.vue"
import {mountGlobal} from "./_helpers"

// Stub the built-in transition so word swaps apply synchronously (no real transitions in jsdom).
const mountThinking = (props: Record<string, unknown> = {}) =>
    mount(CopilotThinking, {props, global: {...mountGlobal, stubs: {...mountGlobal.stubs, transition: true}}})

describe("CopilotThinking", () => {
    afterEach(() => {
        vi.restoreAllMocks()
        vi.useRealTimers()
    })

    it("renders a rotating orchestration word beside the animated Kestra mark", () => {
        vi.spyOn(Math, "random").mockReturnValue(0) // start on the first word
        const w = mountThinking()
        const indicator = w.find("[data-test=\"copilot-thinking\"]")
        expect(indicator.exists()).toBe(true)
        // Decorative: the rotating flavour words are hidden from the a11y tree (no screen-reader spam).
        expect(indicator.attributes("aria-hidden")).toBe("true")
        // The word carries a static trailing ellipsis ("Orchestrating…").
        expect(w.text()).toContain("Orchestrating…")
        // The animated mark is decorative and hidden from the a11y tree; defaults to the thinking movement.
        const mark = w.find(".copilot-mark")
        expect(mark.exists()).toBe(true)
        expect(mark.attributes("aria-hidden")).toBe("true")
        expect(mark.classes()).toContain("copilot-mark-thinking")
    })

    it("rotates to a different (random) word every 5 seconds, never repeating the current one", async () => {
        vi.useFakeTimers()
        // Random sequence: 0 → start on the first word; 0.5 → the next tick picks a different index.
        const seq = [0, 0.5]
        let i = 0
        vi.spyOn(Math, "random").mockImplementation(() => seq[i++] ?? 0)
        const w = mountThinking()
        const first = w.text()

        vi.advanceTimersByTime(5000)
        await w.vm.$nextTick()
        expect(w.text()).not.toBe(first)
    })

    it("shows the mark alone (no flavour word) while answering", () => {
        const w = mountThinking({phase: "answering"})
        expect(w.find(".copilot-mark").classes()).toContain("copilot-mark-answering")
        // The rotating word only reads during the thinking gap.
        expect(w.text()).toBe("")
    })
})

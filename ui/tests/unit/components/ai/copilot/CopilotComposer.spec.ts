import {describe, it, expect} from "vitest"
import {mount} from "@vue/test-utils"
import CopilotComposer from "../../../../../src/components/ai/copilot/CopilotComposer.vue"
import {mountGlobal} from "./_helpers"

const mountComposer = (props = {}) =>
    mount(CopilotComposer, {props: {mode: "ASK", ...props}, global: mountGlobal})

const input = (w: ReturnType<typeof mountComposer>) => w.find("[data-test=\"copilot-composer-input\"]")
const sendBtn = (w: ReturnType<typeof mountComposer>) => w.find("[data-test=\"copilot-send\"]")

describe("CopilotComposer", () => {
    it("offers the three modes ordered by capability (Ask / Plan / Edit)", () => {
        const labels = mountComposer().findAll(".ks-dropdown-item").map((b) => b.text())
        expect(labels).toEqual(["Ask", "Plan", "Edit"])
    })

    it("disables send until there is non-whitespace input", async () => {
        const w = mountComposer()
        expect(sendBtn(w).attributes("disabled")).toBeDefined()

        await input(w).setValue("   ")
        expect(sendBtn(w).attributes("disabled")).toBeDefined()

        await input(w).setValue("hello")
        expect(sendBtn(w).attributes("disabled")).toBeUndefined()
    })

    it("emits submit with trimmed text and clears the draft on click", async () => {
        const w = mountComposer()
        await input(w).setValue("  make a flow  ")
        await sendBtn(w).trigger("click")

        expect(w.emitted("submit")?.[0]).toEqual(["make a flow"])
        expect((input(w).element as HTMLTextAreaElement).value).toBe("")
    })

    it("submits on Enter but not on Shift+Enter", async () => {
        const w = mountComposer()
        await input(w).setValue("hi")

        await input(w).trigger("keydown", {key: "Enter", shiftKey: true})
        expect(w.emitted("submit")).toBeUndefined()

        await input(w).trigger("keydown", {key: "Enter"})
        expect(w.emitted("submit")?.[0]).toEqual(["hi"])
    })

    it("relays mode changes from the dropdown via update:mode", async () => {
        const w = mountComposer()
        // The dropdown items are Ask / Plan / Edit; clicking "Plan" (index 1) emits PLAN.
        await w.findAll(".ks-dropdown-item")[1].trigger("click")
        expect(w.emitted("update:mode")?.[0]).toEqual(["PLAN"])
    })

    it("does not submit while disabled", async () => {
        const w = mountComposer({disabled: true})
        await input(w).setValue("hi")
        await input(w).trigger("keydown", {key: "Enter"})
        expect(w.emitted("submit")).toBeUndefined()
    })

    it("hides the mic when the Web Speech API is unavailable (e.g. jsdom)", () => {
        // No SpeechRecognition in jsdom → voice input degrades gracefully to no mic button.
        expect(mountComposer().find("[data-test=\"copilot-mic\"]").exists()).toBe(false)
    })

    it("reflects an external v-model value in the textarea (seed/prefill)", () => {
        const w = mountComposer({modelValue: "Fix this error"})
        expect((input(w).element as HTMLTextAreaElement).value).toBe("Fix this error")
        // A seeded prompt is submittable straight away.
        expect(sendBtn(w).attributes("disabled")).toBeUndefined()
    })

    it("emits update:modelValue as the user types", async () => {
        const w = mountComposer()
        await input(w).setValue("hello")
        expect(w.emitted("update:modelValue")?.at(-1)).toEqual(["hello"])
    })
})

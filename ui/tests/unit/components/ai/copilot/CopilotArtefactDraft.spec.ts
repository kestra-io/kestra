import {describe, it, expect, vi, beforeEach} from "vitest"
import {mount} from "@vue/test-utils"
import CopilotArtefactDraft from "../../../../../src/components/ai/copilot/CopilotArtefactDraft.vue"
import {mountGlobal} from "./_helpers"
import type {ArtefactDraftEvent} from "../../../../../src/components/ai/copilot/types"

const mountDraft = (draft: ArtefactDraftEvent) =>
    mount(CopilotArtefactDraft, {props: {draft}, global: mountGlobal})

const writeText = vi.fn().mockResolvedValue(undefined)

describe("CopilotArtefactDraft", () => {
    beforeEach(() => {
        writeText.mockClear()
        vi.stubGlobal("navigator", {clipboard: {writeText}})
    })

    it("renders the kind title, a valid badge and the YAML", () => {
        const w = mountDraft({draftId: "d1", kind: "FLOW", yaml: "id: demo", valid: true, constraints: null})
        expect(w.text()).toContain("Proposed flow")
        expect(w.find(".ks-code-status").attributes("data-status")).toBe("valid")
        expect(w.find("[data-test=\"copilot-draft-yaml\"]").text()).toBe("id: demo")
        // A valid draft shows no constraints alert.
        expect(w.find(".ks-alert").exists()).toBe(false)
    })

    it("shows an error badge and the constraints when the draft is invalid", () => {
        const w = mountDraft({draftId: "d2", kind: "DASHBOARD", yaml: "x: 1", valid: false, constraints: "charts is required"})
        expect(w.text()).toContain("Proposed dashboard")
        expect(w.find(".ks-code-status").attributes("data-status")).toBe("error")
        const alert = w.find(".ks-alert")
        expect(alert.exists()).toBe(true)
        expect(alert.text()).toContain("charts is required")
    })

    it("copies the YAML to the clipboard and flips the button label", async () => {
        const w = mountDraft({draftId: "d3", kind: "APP", yaml: "id: my-app", valid: true, constraints: null})
        const button = w.find("[data-test=\"copilot-draft-copy\"]")
        expect(button.text()).toBe("Copy YAML")
        await button.trigger("click")
        await w.vm.$nextTick()
        expect(writeText).toHaveBeenCalledWith("id: my-app")
        expect(button.text()).toBe("Copied")
    })
})

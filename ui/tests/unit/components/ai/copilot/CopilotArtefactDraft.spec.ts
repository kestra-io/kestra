import {describe, it, expect} from "vitest"
import {mount} from "@vue/test-utils"
import CopilotArtefactDraft from "../../../../../src/components/ai/copilot/CopilotArtefactDraft.vue"
import {mountGlobal} from "./_helpers"
import type {ArtefactDraftEvent} from "../../../../../src/components/ai/copilot/types"

const mountDraft = (draft: ArtefactDraftEvent) =>
    mount(CopilotArtefactDraft, {props: {draft}, global: mountGlobal})

describe("CopilotArtefactDraft", () => {
    it("renders the kind title, a valid badge and the YAML", () => {
        const w = mountDraft({draftId: "d1", kind: "FLOW", yaml: "id: demo", valid: true, constraints: null})
        expect(w.text()).toContain("Proposed flow")
        expect(w.find(".ks-code-status").attributes("data-status")).toBe("valid")
        // Rendered via KsMarkdown as a highlighted yaml fence, so assert the YAML is present.
        expect(w.find("[data-test=\"copilot-draft-yaml\"]").text()).toContain("id: demo")
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

    it("renders the YAML via KsMarkdown and exposes no separate copy button", () => {
        // Copy is handled by KsMarkdown's built-in control, so the card has no copy button of its own.
        const w = mountDraft({draftId: "d3", kind: "APP", yaml: "id: my-app", valid: true, constraints: null})
        expect(w.find("[data-test=\"copilot-draft-copy\"]").exists()).toBe(false)
        expect(w.find("[data-test=\"copilot-draft-yaml\"]").text()).toContain("id: my-app")
    })
})

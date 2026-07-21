import {describe, it, expect, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {ref} from "vue"

// Stub the apply composable (it pulls in the router + flows SDK); assert wiring only.
const openInEditor = vi.fn()
const apply = vi.fn()
// Mutable so a test can simulate the EE app path being present (apps are unsupported in OSS).
const appSupported = {value: false}
vi.mock("../../../../../src/components/ai/copilot/useApplyDraft", () => ({
    useApplyDraft: () => ({applying: ref(false), appSupported: appSupported.value, openInEditor, apply}),
}))

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

    it("offers Open-in-editor + Apply for a valid flow draft and wires them", async () => {
        const w = mountDraft({draftId: "d4", kind: "FLOW", yaml: "id: f", valid: true, constraints: null})
        await w.find("[data-test=\"copilot-draft-open\"]").trigger("click")
        expect(openInEditor).toHaveBeenCalled()
        const applyBtn = w.find("[data-test=\"copilot-draft-apply\"]")
        expect(applyBtn.attributes("disabled")).toBeUndefined()
        await applyBtn.trigger("click")
        expect(apply).toHaveBeenCalled()
    })

    it("disables Apply for an invalid flow draft (Open-in-editor stays available)", () => {
        const w = mountDraft({draftId: "d5", kind: "FLOW", yaml: "id: f", valid: false, constraints: "bad"})
        expect(w.find("[data-test=\"copilot-draft-apply\"]").attributes("disabled")).toBeDefined()
        expect(w.find("[data-test=\"copilot-draft-open\"]").exists()).toBe(true)
    })

    it("offers Open-in-editor + Apply for a valid dashboard draft and wires them", async () => {
        const w = mountDraft({draftId: "d6", kind: "DASHBOARD", yaml: "id: d", valid: true, constraints: null})
        await w.find("[data-test=\"copilot-draft-open\"]").trigger("click")
        expect(openInEditor).toHaveBeenCalled()
        await w.find("[data-test=\"copilot-draft-apply\"]").trigger("click")
        expect(apply).toHaveBeenCalled()
    })

    it("shows no apply actions for an app draft when apps are unsupported (OSS)", () => {
        appSupported.value = false
        const w = mountDraft({draftId: "d7", kind: "APP", yaml: "id: my-app", valid: true, constraints: null})
        expect(w.find("[data-test=\"copilot-draft-open\"]").exists()).toBe(false)
        expect(w.find("[data-test=\"copilot-draft-apply\"]").exists()).toBe(false)
    })

    it("offers Open-in-editor (only) for an app draft when the app path is present (EE)", async () => {
        appSupported.value = true
        const w = mountDraft({draftId: "d8", kind: "APP", yaml: "id: my-app", valid: true, constraints: null})
        await w.find("[data-test=\"copilot-draft-open\"]").trigger("click")
        expect(openInEditor).toHaveBeenCalled()
        // Apps have no direct-apply — only open-in-editor.
        expect(w.find("[data-test=\"copilot-draft-apply\"]").exists()).toBe(false)
        appSupported.value = false
    })
})

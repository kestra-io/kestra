import {describe, it, expect, beforeEach, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {ref} from "vue"

const openInEditor = vi.fn()
const apply = vi.fn()
const appSupported = {value: true}
vi.mock("./useApplyDraft", () => ({
    useApplyDraft: () => ({
        applying: ref(false),
        appSupported: appSupported.value,
        dashboardSupported: ref(true),
        testSuiteSupported: true,
        openInEditor,
        apply,
    }),
}))

import CopilotDraftTray from "./CopilotDraftTray.vue"
import {mountGlobal} from "../../../../tests/unit/components/ai/copilot/_helpers"
import type {PendingDraft} from "./pendingDrafts"

const item = (draftId: string, kind: "FLOW" | "APP", extra: Partial<PendingDraft> = {}): PendingDraft => ({
    draft: {draftId, kind, yaml: `id: ${draftId}`, valid: true, constraints: null},
    namespace: "company.team",
    id: draftId,
    ...extra,
})

const mountTray = (items: PendingDraft[]) => mount(CopilotDraftTray, {props: {items}, global: mountGlobal})

describe("CopilotDraftTray", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        appSupported.value = true
    })

    it("applies a flow from the tray and reports it as applied", async () => {
        apply.mockResolvedValue(true)
        const w = mountTray([item("orders", "FLOW")])

        await w.find("[data-test='copilot-tray-apply']").trigger("click")
        await vi.waitFor(() => expect(w.emitted("applied")).toEqual([["orders"]]))
    })

    it("does not report a flow as applied when the apply was declined", async () => {
        apply.mockResolvedValue(false)
        const w = mountTray([item("orders", "FLOW")])

        await w.find("[data-test='copilot-tray-apply']").trigger("click")
        await vi.waitFor(() => expect(apply).toHaveBeenCalled())
        expect(w.emitted("applied")).toBeUndefined()
    })

    it("holds an app back until the flow it needs is applied, and says which one", () => {
        const w = mountTray([item("orders-app", "APP", {waitsFor: "company.team.orders"})])

        expect(w.find("[data-test='copilot-tray-open']").attributes("disabled")).toBeDefined()
        expect(w.find("[data-test='copilot-tray-hint']").text()).toBe("Apply company.team.orders first")
    })

    it("opens an unblocked app in the editor", async () => {
        const w = mountTray([item("orders-app", "APP")])

        await w.find("[data-test='copilot-tray-open']").trigger("click")
        expect(openInEditor).toHaveBeenCalledWith(expect.objectContaining({draftId: "orders-app"}))
    })

    it("dismisses a draft from the tray", async () => {
        const w = mountTray([item("orders", "FLOW")])

        await w.find("[data-test='copilot-tray-dismiss']").trigger("click")
        expect(w.emitted("dismiss")).toEqual([["orders"]])
    })

    it("leaves out a draft kind this edition cannot act on", () => {
        appSupported.value = false
        const w = mountTray([item("orders-app", "APP")])

        expect(w.find("[data-test='copilot-draft-tray']").exists()).toBe(false)
    })
})

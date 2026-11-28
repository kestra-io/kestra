import {describe, it, expect} from "vitest"
import {collectPendingDrafts} from "./pendingDrafts"
import type {ChatMessage} from "./useAiChat"
import type {ArtefactDraftEvent} from "./types"

const draftMessage = (draftId: string, kind: ArtefactDraftEvent["kind"], yaml: string): ChatMessage => ({
    id: draftId,
    role: "ASSISTANT",
    type: "ARTEFACT_DRAFT",
    draft: {draftId, kind, yaml, valid: true, constraints: null},
})

const FLOW = "id: orders\nnamespace: company.team\ntasks: []"
const APP = "id: orders-app\nnamespace: company.team\nflowId: orders"
const none = () => false

describe("collectPendingDrafts", () => {
    it("lists flows before the resources that depend on them, whatever the transcript order", () => {
        const pending = collectPendingDrafts([draftMessage("a", "APP", APP), draftMessage("f", "FLOW", FLOW)], none)

        expect(pending.map(({draft}) => draft.draftId)).toEqual(["f", "a"])
    })

    it("makes an app wait for the pending flow it targets, and only for that one", () => {
        const pending = collectPendingDrafts([
            draftMessage("f", "FLOW", FLOW),
            draftMessage("a", "APP", APP),
            draftMessage("other", "APP", "id: other\nnamespace: company.team\nflowId: elsewhere"),
        ], none)

        expect(pending.find(({draft}) => draft.draftId === "a")?.waitsFor).toBe("company.team.orders")
        expect(pending.find(({draft}) => draft.draftId === "other")?.waitsFor).toBeUndefined()
    })

    it("stops waiting once the flow is applied", () => {
        const pending = collectPendingDrafts(
            [draftMessage("f", "FLOW", FLOW), draftMessage("a", "APP", APP)],
            (draftId) => draftId === "f",
        )

        expect(pending.map(({draft}) => draft.draftId)).toEqual(["a"])
        expect(pending[0].waitsFor).toBeUndefined()
    })

    it("keeps only the latest draft of the same artefact", () => {
        const revised = FLOW + "\ndescription: revised"
        const pending = collectPendingDrafts([draftMessage("v1", "FLOW", FLOW), draftMessage("v2", "FLOW", revised)], none)

        expect(pending.map(({draft}) => draft.draftId)).toEqual(["v2"])
    })

    it("drops a flow draft that already matches the open editor", () => {
        expect(collectPendingDrafts([draftMessage("f", "FLOW", FLOW)], none, FLOW)).toEqual([])
    })
})

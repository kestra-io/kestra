import {beforeEach, describe, expect, it} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {useTriggerDraftStore} from "./triggerDraft"

const DRAFT = {namespace: "company.team", flowId: "hello", triggerYaml: "id: daily"}

describe("trigger draft store", () => {
    beforeEach(() => setActivePinia(createPinia()))

    /** Guards the one-time handoff, so a leftover draft never reappears in another flow. */
    it("hands the draft to its own flow, and only once", () => {
        const store = useTriggerDraftStore()
        store.setDraft(DRAFT)

        expect(store.consumeDraft("company.team", "other")).toBeUndefined()
        expect(store.consumeDraft("company.team", "hello")).toEqual(DRAFT)
        expect(store.consumeDraft("company.team", "hello")).toBeUndefined()
    })
})

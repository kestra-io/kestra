import {beforeEach, describe, expect, it} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {useTriggerDraftStore} from "../../../src/stores/triggerDraft"

const DRAFT = {namespace: "company.team", flowId: "hello", triggerYaml: "id: daily"}

describe("trigger draft store", () => {
    beforeEach(() => setActivePinia(createPinia()))

    it("hands the draft to its own flow, and only once", () => {
        const store = useTriggerDraftStore()
        store.setDraft(DRAFT)

        expect(store.consumeDraft("company.team", "other")).toBeUndefined()
        expect(store.consumeDraft("company.team", "hello")).toEqual(DRAFT)
        expect(store.consumeDraft("company.team", "hello")).toBeUndefined()
    })
})

import {afterEach, describe, expect, it, vi} from "vitest"
import {clearQueuedDrafts, readQueuedDraft, removeQueuedDraft, writeQueuedDraft, type DraftKey, type QueuedDraft} from "./draftOutbox"

const key: DraftKey = {tenant: "main", namespace: "company.team", id: "hello"}
const draft: QueuedDraft = {source: "id: hello\nnamespace: company.team\n", baseRevision: 3, queuedAt: "2026-11-24T10:00:00.000Z"}

describe("draftOutbox", () => {
    afterEach(() => {
        vi.restoreAllMocks()
        localStorage.clear()
    })

    it("keeps a draft per tenant, so the same flow id in another tenant does not surface it", () => {
        expect(writeQueuedDraft(key, draft)).toBe(true)

        expect(readQueuedDraft(key)).toEqual(draft)
        expect(readQueuedDraft({...key, tenant: "other"})).toBeUndefined()

        removeQueuedDraft(key)
        expect(readQueuedDraft(key)).toBeUndefined()
    })

    it("clears every tenant's drafts on sign-out and nothing else the app keeps in storage", () => {
        writeQueuedDraft(key, draft)
        writeQueuedDraft({...key, tenant: "other", id: "bye"}, draft)
        localStorage.setItem("paginationSize", "50")

        clearQueuedDrafts()

        expect(readQueuedDraft(key)).toBeUndefined()
        expect(readQueuedDraft({...key, tenant: "other", id: "bye"})).toBeUndefined()
        expect(localStorage.getItem("paginationSize")).toBe("50")
    })

    it("reports a refused write instead of throwing, so the save still fails visibly", () => {
        vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
            throw new DOMException("quota", "QuotaExceededError")
        })

        expect(writeQueuedDraft(key, draft)).toBe(false)
    })

    it("ignores an entry it cannot read back as a draft", () => {
        localStorage.setItem("ks-draft-outbox:main:company.team:hello", "{\"source\": 12}")
        expect(readQueuedDraft(key)).toBeUndefined()

        localStorage.setItem("ks-draft-outbox:main:company.team:hello", "not json")
        expect(readQueuedDraft(key)).toBeUndefined()
    })
})

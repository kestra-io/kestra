import {describe, it, expect, vi, beforeEach} from "vitest"

const get = vi.fn()
const patch = vi.fn()
const del = vi.fn()
vi.mock("@kestra-io/kestra-sdk", () => ({useClient: () => ({get, patch, delete: del})}))
vi.mock("override/utils/route", () => ({apiUrl: () => "http://localhost/api/v1/main"}))

import {useAiThreads} from "../../../../../src/components/ai/copilot/useAiThreads"

const BASE = "http://localhost/api/v1/main/ai/threads"

describe("useAiThreads", () => {
    beforeEach(() => {
        get.mockReset()
        patch.mockReset()
        del.mockReset()
    })

    it("lists threads most-recent first (by lastTurnAt, then updatedAt)", async () => {
        get.mockResolvedValue({data: [
            {uid: "a", updatedAt: "2026-01-01T00:00:00Z", lastTurnAt: "2026-01-01T00:00:00Z"},
            {uid: "b", updatedAt: "2026-01-02T00:00:00Z", lastTurnAt: "2026-01-03T00:00:00Z"},
        ]})
        const t = useAiThreads()
        await t.list()
        expect(t.threads.value.map((x) => x.uid)).toEqual(["b", "a"])
        expect(t.error.value).toBe(false)
    })

    it("empties and flags an error when listing fails (endpoint not available yet)", async () => {
        get.mockRejectedValue(new Error("404"))
        const t = useAiThreads()
        await t.list()
        expect(t.threads.value).toEqual([])
        expect(t.error.value).toBe(true)
    })

    it("renames a thread and reflects the new title locally", async () => {
        get.mockResolvedValue({data: [{uid: "a", title: "old", updatedAt: "x"}]})
        patch.mockResolvedValue({})
        const t = useAiThreads()
        await t.list()
        await t.rename("a", "new title")
        expect(patch).toHaveBeenCalledWith(`${BASE}/a/rename`, {title: "new title"})
        expect(t.threads.value[0].title).toBe("new title")
    })

    it("removes a thread and drops it from the list", async () => {
        get.mockResolvedValue({data: [{uid: "a", updatedAt: "x"}, {uid: "b", updatedAt: "y"}]})
        del.mockResolvedValue({})
        const t = useAiThreads()
        await t.list()
        await t.remove("a")
        expect(del).toHaveBeenCalledWith(`${BASE}/a`)
        expect(t.threads.value.map((x) => x.uid)).toEqual(["b"])
    })
})

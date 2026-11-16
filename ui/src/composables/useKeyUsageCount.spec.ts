import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {effectScope, nextTick} from "vue"

const {mockSearchFlowsBySourceCode} = vi.hoisted(() => ({mockSearchFlowsBySourceCode: vi.fn()}))

vi.mock("@kestra-io/kestra-sdk/flows", () => ({searchFlowsBySourceCode: mockSearchFlowsBySourceCode}))

import {resetKeyUsageCounts, useKeyUsageCount} from "./useKeyUsageCount"
import type {KeyTarget} from "./useKeyUsages"

const flow = (namespace: string, id: string, lines: number[]) => ({
    namespace,
    id,
    editable: true,
    matches: lines.map((line) => ({line, column: 0, snippet: "kv('k')"})),
})

const deferred = () => {
    let resolve!: (value: unknown) => void
    const promise = new Promise((r) => resolve = r)
    return {promise, resolve}
}

const settle = async () => {
    for (let i = 0; i < 5; i++) await Promise.resolve()
    await nextTick()
}

const kv = (key: string): KeyTarget => ({kind: "kv", namespace: "company", key})

const mountCount = (target: KeyTarget) => {
    const scope = effectScope()
    const count = scope.run(() => useKeyUsageCount(target))!
    return {count, stop: () => scope.stop()}
}

describe("useKeyUsageCount", () => {
    beforeEach(() => {
        mockSearchFlowsBySourceCode.mockReset()
        resetKeyUsageCounts()
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it("counts distinct flows, not matching lines", async () => {
        mockSearchFlowsBySourceCode.mockResolvedValue({results: [flow("company", "a", [3, 9]), flow("company.child", "b", [4])], total: 2})

        const {count} = mountCount(kv("k"))
        await settle()

        expect(count.value).toMatchObject({status: "done", flows: 2, truncated: false})
    })

    it("runs at most three counts at a time", async () => {
        const pending = deferred()
        mockSearchFlowsBySourceCode.mockReturnValue(pending.promise)

        const counts = ["a", "b", "c", "d", "e"].map((key) => mountCount(kv(key)).count)
        await settle()

        expect(mockSearchFlowsBySourceCode).toHaveBeenCalledTimes(6)
        expect(counts.map((count) => count.value?.status)).toEqual(["loading", "loading", "loading", "queued", "queued"])

        pending.resolve({results: [], total: 0})
        await settle()

        expect(mockSearchFlowsBySourceCode).toHaveBeenCalledTimes(10)
        expect(counts.every((count) => count.value?.status === "done")).toBe(true)
    })

    it("drops a queued count when its row goes away", async () => {
        const pending = deferred()
        mockSearchFlowsBySourceCode.mockReturnValue(pending.promise)

        ;["a", "b", "c"].forEach((key) => mountCount(kv(key)))
        const leaving = mountCount(kv("gone"))
        await settle()
        leaving.stop()

        pending.resolve({results: [], total: 0})
        await settle()

        const queries = mockSearchFlowsBySourceCode.mock.calls.map(([params]) => params.q)
        expect(queries.some((q: string) => q.includes("gone"))).toBe(false)
    })

    it("reuses a recent count and refreshes it after a minute", async () => {
        vi.useFakeTimers({toFake: ["Date"]})
        mockSearchFlowsBySourceCode.mockResolvedValue({results: [flow("company", "a", [1])], total: 1})

        mountCount(kv("k"))
        await settle()
        mountCount(kv("k"))
        await settle()
        expect(mockSearchFlowsBySourceCode).toHaveBeenCalledTimes(2)

        vi.setSystemTime(Date.now() + 61_000)
        mountCount(kv("k"))
        await settle()
        expect(mockSearchFlowsBySourceCode).toHaveBeenCalledTimes(4)
    })

    it("retries a failed count on the next mount", async () => {
        mockSearchFlowsBySourceCode.mockRejectedValueOnce(new Error("boom"))
        mockSearchFlowsBySourceCode.mockResolvedValue({results: [], total: 0})

        const first = mountCount(kv("k"))
        await settle()
        expect(first.count.value?.status).toBe("failed")

        const second = mountCount(kv("k"))
        await settle()
        expect(second.count.value?.status).toBe("done")
    })

    it("flags a count capped by the search page", async () => {
        mockSearchFlowsBySourceCode.mockResolvedValue({results: [flow("company", "a", [1])], total: 250})

        const {count} = mountCount(kv("k"))
        await settle()

        expect(count.value?.truncated).toBe(true)
    })
})

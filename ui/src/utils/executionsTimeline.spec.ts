import {describe, expect, it} from "vitest"
import {
    assignLanes,
    bucketize,
    buildAxisTicks,
    countByState,
    groupByNamespace,
    shouldBucketRow,
    type TimelineExecution,
} from "./executionsTimeline"

const execution = (overrides: Partial<TimelineExecution>): TimelineExecution => ({
    id: "id",
    namespace: "company.team",
    flowId: "flow",
    state: "SUCCESS",
    startMs: 0,
    endMs: 1000,
    ...overrides,
})

describe("groupByNamespace", () => {
    it("should group executions by namespace then by flow with total and failed counts", () => {
        const executions = [
            execution({id: "1", namespace: "company.a", flowId: "flow1", state: "SUCCESS"}),
            execution({id: "2", namespace: "company.a", flowId: "flow1", state: "FAILED"}),
            execution({id: "3", namespace: "company.a", flowId: "flow2", state: "SUCCESS"}),
            execution({id: "4", namespace: "company.b", flowId: "flow3", state: "KILLED"}),
        ]

        const groups = groupByNamespace(executions)

        expect(groups.map(g => g.namespace)).toEqual(["company.a", "company.b"])
        const namespaceA = groups.find(g => g.namespace === "company.a")!
        expect(namespaceA.total).toBe(3)
        expect(namespaceA.failed).toBe(1)
        expect(namespaceA.flows.map(f => f.flowId).sort()).toEqual(["flow1", "flow2"])

        const namespaceB = groups.find(g => g.namespace === "company.b")!
        expect(namespaceB.total).toBe(1)
        expect(namespaceB.failed).toBe(1)
    })

    it("should return an empty list when there are no executions", () => {
        expect(groupByNamespace([])).toEqual([])
    })
})

describe("assignLanes", () => {
    it("should keep non-overlapping executions on the same lane", () => {
        const executions = [
            execution({id: "1", startMs: 0, endMs: 100}),
            execution({id: "2", startMs: 200, endMs: 300}),
        ]

        const lanes = assignLanes(executions)

        expect(lanes.map(e => e.lane)).toEqual([0, 0])
    })

    it("should push overlapping executions onto separate lanes", () => {
        const executions = [
            execution({id: "1", startMs: 0, endMs: 500}),
            execution({id: "2", startMs: 100, endMs: 400}),
            execution({id: "3", startMs: 150, endMs: 200}),
        ]

        const lanes = assignLanes(executions)

        expect(lanes.find(e => e.id === "1")!.lane).toBe(0)
        expect(lanes.find(e => e.id === "2")!.lane).toBe(1)
        expect(lanes.find(e => e.id === "3")!.lane).toBe(2)
    })

    it("should reuse a lane once its previous execution has ended", () => {
        const executions = [
            execution({id: "1", startMs: 0, endMs: 100}),
            execution({id: "2", startMs: 0, endMs: 200}),
            execution({id: "3", startMs: 150, endMs: 250}),
        ]

        const lanes = assignLanes(executions)

        expect(lanes.find(e => e.id === "1")!.lane).toBe(0)
        expect(lanes.find(e => e.id === "2")!.lane).toBe(1)
        // Execution 3 starts after lane 0's execution 1 ended (100 <= 150), so it reuses lane 0.
        expect(lanes.find(e => e.id === "3")!.lane).toBe(0)
    })
})

describe("shouldBucketRow", () => {
    it("should not bucket when executions land in separate slots", () => {
        const executions = [
            execution({id: "1", startMs: 0}),
            execution({id: "2", startMs: 50}),
        ]

        expect(shouldBucketRow(executions, 0, 100, 100)).toBe(false)
    })

    it("should bucket once two executions land in the same slot", () => {
        const executions = Array.from({length: 100}, (_, i) => execution({id: String(i), startMs: i}))

        expect(shouldBucketRow(executions, 0, 100, 100)).toBe(true)
    })

    it("should not bucket an empty row", () => {
        expect(shouldBucketRow([], 0, 100, 100)).toBe(false)
    })

    it("should bucket a tight cluster zoomed out to a much wider range", () => {
        // 15 executions inside a 1-hour window, viewed across a 32-day range: each bar's true
        // (pre-floor) width is a tiny fraction of a pixel, so the row must bucket even though it has
        // far fewer executions than the available width in pixels would otherwise allow.
        const oneHourMs = 60 * 60 * 1000
        const thirtyTwoDaysMs = 32 * 24 * 60 * 60 * 1000
        const executions = Array.from({length: 15}, (_, i) =>
            execution({id: String(i), startMs: i * (oneHourMs / 15), endMs: i * (oneHourMs / 15) + 1000}))

        expect(shouldBucketRow(executions, 0, thirtyTwoDaysMs, 600)).toBe(true)
    })
})

describe("bucketize", () => {
    it("should aggregate executions into buckets carrying per-state counts and a dominant state", () => {
        const executions = [
            execution({id: "1", state: "SUCCESS", startMs: 0}),
            execution({id: "2", state: "SUCCESS", startMs: 10}),
            execution({id: "3", state: "FAILED", startMs: 10}),
            execution({id: "4", state: "SUCCESS", startMs: 90}),
        ]

        const buckets = bucketize(executions, 0, 100, 10)

        expect(buckets.length).toBeGreaterThan(0)
        const total = buckets.reduce((sum, bucket) => sum + bucket.total, 0)
        expect(total).toBe(4)
        expect(buckets.every(bucket => bucket.dominantState.length > 0)).toBe(true)
        // Buckets never carry an execution id, only aggregated per-state counts.
        expect(buckets.every(bucket => !("id" in bucket))).toBe(true)
    })

    it("should return no buckets for an empty or zero-width range", () => {
        expect(bucketize([execution({})], 100, 100, 50)).toEqual([])
        expect(bucketize([execution({})], 0, 100, 0)).toEqual([])
    })
})

describe("countByState", () => {
    it("should count executions per state, sorted by count descending", () => {
        const executions = [
            execution({state: "SUCCESS"}),
            execution({state: "SUCCESS"}),
            execution({state: "FAILED"}),
        ]

        expect(countByState(executions)).toEqual([
            {state: "SUCCESS", count: 2},
            {state: "FAILED", count: 1},
        ])
    })
})

describe("buildAxisTicks", () => {
    it("should return tickCount + 1 evenly spaced ticks across the range", () => {
        const ticks = buildAxisTicks(0, 60_000, 6, 60_000 + 120_000)

        expect(ticks).toHaveLength(7)
        expect(ticks.map(t => t.ms)).toEqual([0, 10_000, 20_000, 30_000, 40_000, 50_000, 60_000])
        expect(ticks.every(t => !t.isNow)).toBe(true)
    })

    it("should flag only the last tick as now when the range end is pinned to now", () => {
        const rangeEndMs = 60_000
        const nowMs = rangeEndMs + 1_000

        const ticks = buildAxisTicks(0, rangeEndMs, 6, nowMs)

        expect(ticks.slice(0, -1).every(t => !t.isNow)).toBe(true)
        expect(ticks.at(-1)?.isNow).toBe(true)
    })

    it("should not flag any tick as now when the range end is far from now", () => {
        const ticks = buildAxisTicks(0, 60_000, 6, 60_000 + 120_000)

        expect(ticks.every(t => !t.isNow)).toBe(true)
    })

    it("should return no ticks for a zero-width range", () => {
        expect(buildAxisTicks(1_000, 1_000, 6, 1_000)).toEqual([])
    })
})

import {describe, it, expect} from "vitest"

import {computeTrace, traceEdgeKey} from "../../../../src/components/dependencies/utils/dagTrace"

const chain = [
    {source: "a", target: "b"},
    {source: "b", target: "c"},
    {source: "c", target: "d"},
]

describe("computeTrace", () => {
    it("returns null when there is no id to trace", () => {
        // Callers skip dimming on null rather than dimming everything.
        expect(computeTrace(chain, undefined)).toBeNull()
    })

    it("collects transitive descendants, not just direct children", () => {
        const trace = computeTrace(chain, "a")!

        expect(trace.nodes).toEqual(new Set(["a", "b", "c", "d"]))
        expect(trace.edges).toEqual(new Set([
            traceEdgeKey("a", "b"),
            traceEdgeKey("b", "c"),
            traceEdgeKey("c", "d"),
        ]))
    })

    it("collects transitive ancestors, not just direct parents", () => {
        const trace = computeTrace(chain, "d")!

        expect(trace.nodes).toEqual(new Set(["a", "b", "c", "d"]))
        expect(trace.edges.size).toBe(3)
    })

    it("reaches ancestors through a node already found downstream in a cyclic graph", () => {
        // X→W→X is a cycle and D feeds W: D is a genuine transitive ancestor of X via
        // D→W→X, and must not be hidden by W having been visited on the downstream walk.
        // This is the regression a single shared visited set caused.
        const trace = computeTrace([
            {source: "X", target: "W"},
            {source: "W", target: "X"},
            {source: "D", target: "W"},
        ], "X")!

        expect(trace.nodes).toEqual(new Set(["X", "W", "D"]))
        expect(trace.edges).toEqual(new Set([
            traceEdgeKey("X", "W"),
            traceEdgeKey("W", "X"),
            traceEdgeKey("D", "W"),
        ]))
    })

    it("skips self-edges entirely", () => {
        const trace = computeTrace([{source: "a", target: "a"}, {source: "a", target: "b"}], "a")!

        expect(trace.nodes).toEqual(new Set(["a", "b"]))
        expect(trace.edges.has(traceEdgeKey("a", "a"))).toBe(false)
    })
})

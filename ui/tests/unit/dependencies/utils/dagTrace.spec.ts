import {describe, it, expect} from "vitest"
import {computeTrace, extendTraceWithDirectEdges, traceEdgeKey} from "../../../../src/components/dependencies/utils/dagTrace"

describe("dagTrace.ts", () => {
    describe("computeTrace", () => {
        it("returns null when there is no focused node", () => {
            expect(computeTrace([{source: "A", target: "B"}], undefined)).toBeNull()
        })

        it("walks transitively through both directions", () => {
            const trace = computeTrace(
                [{source: "A", target: "B"}, {source: "B", target: "C"}],
                "B",
            )
            expect(trace?.nodes).toEqual(new Set(["A", "B", "C"]))
        })
    })

    describe("extendTraceWithDirectEdges", () => {
        // A --lineage--> B, plus a RELATED edge from A to a third asset R, unrelated to B's lineage.
        const lineageEdges = [{source: "A", target: "B", kind: "UPSTREAM_OF"}]
        const allEdges = [...lineageEdges, {source: "A", target: "R", kind: "RELATED"}]
        const isLineage = (kind?: string) => kind !== "RELATED" && kind !== "PART_OF"

        it("lights the direct non-lineage neighbor without pulling in its own lineage", () => {
            const base = computeTrace(lineageEdges, "A")
            const extended = extendTraceWithDirectEdges(base, allEdges, "A", isLineage)

            expect(extended?.nodes.has("R")).toBe(true)
            expect(extended?.edges.has(traceEdgeKey("A", "R"))).toBe(true)
            // Still lineage-only beyond the direct hop: B is reachable from A's own lineage edge, not from R.
            expect(extended?.nodes.has("B")).toBe(true)
        })

        it("does not add anything beyond the direct hop for the non-lineage neighbor", () => {
            const edges = [...allEdges, {source: "R", target: "Z", kind: "UPSTREAM_OF"}]
            const base = computeTrace(lineageEdges, "A")
            const extended = extendTraceWithDirectEdges(base, edges, "A", isLineage)

            // Z is R's own downstream, not A's; only a direct edge from the focus node is folded in.
            expect(extended?.nodes.has("Z")).toBe(false)
        })

        it("returns the base trace unchanged when there is no focused node", () => {
            expect(extendTraceWithDirectEdges(null, allEdges, undefined, isLineage)).toBeNull()
        })
    })
})

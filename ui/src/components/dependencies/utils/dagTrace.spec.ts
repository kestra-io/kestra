import {describe, expect, it} from "vitest"
import {computeTrace, extendTraceWithDirectEdges, traceEdgeKey} from "./dagTrace"
import {isLineageEdge} from "./relationKind"

type Edge = {source: string; target: string; kind?: string}

const lineage = (source: string, target: string): Edge => ({source, target, kind: "UPSTREAM_OF"})
const related = (source: string, target: string): Edge => ({source, target, kind: "RELATED"})
const partOf = (source: string, target: string): Edge => ({source, target, kind: "PART_OF"})

const keys = (...pairs: [string, string][]) => new Set(pairs.map(([source, target]) => traceEdgeKey(source, target)))

describe("traceEdgeKey", () => {
    it("should return the same key for the same ordered pair", () => {
        expect(traceEdgeKey("A", "B")).toBe(traceEdgeKey("A", "B"))
    })

    it("should return different keys for reversed pairs", () => {
        expect(traceEdgeKey("A", "B")).not.toBe(traceEdgeKey("B", "A"))
    })

    it("should return different keys for distinct pairs", () => {
        expect(traceEdgeKey("A", "B")).not.toBe(traceEdgeKey("A", "C"))
    })

    it("should not collide when an id contains the separator", () => {
        expect(traceEdgeKey("a\0b", "c")).not.toBe(traceEdgeKey("a", "b\0c"))
    })

    it("should not collide when an id contains the encoding's own punctuation", () => {
        expect(traceEdgeKey("a\",\"b", "c")).not.toBe(traceEdgeKey("a", "b\",\"c"))
    })

    it("should keep canonically-equivalent but distinct unicode ids apart", () => {
        expect(traceEdgeKey("\u00e9", "B")).not.toBe(traceEdgeKey("e\u0301", "B"))
    })

    it("should handle empty ids deterministically and directionally", () => {
        expect(traceEdgeKey("", "")).toBe(traceEdgeKey("", ""))
        expect(traceEdgeKey("", "A")).not.toBe(traceEdgeKey("A", ""))
    })
})

describe("computeTrace", () => {
    it("should return null when there is no focused node", () => {
        expect(computeTrace([lineage("A", "B")], undefined)).toBeNull()
    })

    it("should return null when the focused id is empty", () => {
        expect(computeTrace([lineage("A", "B")], "")).toBeNull()
    })

    it("should include only the focused node when it has no lineage", () => {
        const trace = computeTrace([lineage("A", "B")], "Z")

        expect(trace?.nodes).toEqual(new Set(["Z"]))
        expect(trace?.edges).toEqual(new Set())
    })

    it("should walk every ancestor and descendant of a node in a chain", () => {
        const edges = [lineage("A", "B"), lineage("B", "C"), lineage("C", "D"), lineage("D", "E")]
        const trace = computeTrace(edges, "C")

        expect(trace?.nodes).toEqual(new Set(["A", "B", "C", "D", "E"]))
        expect(trace?.edges).toEqual(keys(["A", "B"], ["B", "C"], ["C", "D"], ["D", "E"]))
    })

    it("should leave a disconnected node out of the trace", () => {
        const trace = computeTrace([lineage("A", "B"), lineage("B", "C"), lineage("X", "Y")], "B")

        expect(trace?.nodes.has("X")).toBe(false)
        expect(trace?.nodes.has("Y")).toBe(false)
    })

    it("should orient traced keys by the edge's source and target when traversing backwards", () => {
        const trace = computeTrace([lineage("A", "B")], "B")

        expect(trace?.edges.has(traceEdgeKey("A", "B"))).toBe(true)
        expect(trace?.edges.has(traceEdgeKey("B", "A"))).toBe(false)
    })

    it("should ignore a self-edge without adding it to the trace", () => {
        const trace = computeTrace([lineage("A", "A"), lineage("A", "B")], "A")

        expect(trace?.nodes).toEqual(new Set(["A", "B"]))
        expect(trace?.edges).toEqual(keys(["A", "B"]))
    })

    it("should leave a node whose only edge is a self-edge isolated", () => {
        const trace = computeTrace([lineage("A", "A")], "A")

        expect(trace?.nodes).toEqual(new Set(["A"]))
        expect(trace?.edges).toEqual(new Set())
    })

    it("should include an opaque node reached upstream without walking past it", () => {
        const edges = [lineage("A", "F"), lineage("F", "B")]
        const trace = computeTrace(edges, "B", (id) => id === "F")

        expect(trace?.nodes).toEqual(new Set(["F", "B"]))
        expect(trace?.edges).toEqual(keys(["F", "B"]))
    })

    it("should include an opaque node reached downstream without walking past it", () => {
        const edges = [lineage("B", "F"), lineage("F", "D")]
        const trace = computeTrace(edges, "B", (id) => id === "F")

        expect(trace?.nodes).toEqual(new Set(["B", "F"]))
        expect(trace?.edges).toEqual(keys(["B", "F"]))
    })

    it("should still reach a node through another path around an opaque node", () => {
        const edges = [lineage("B", "F"), lineage("F", "D"), lineage("B", "C"), lineage("C", "D")]
        const trace = computeTrace(edges, "B", (id) => id === "F")

        expect(trace?.nodes).toEqual(new Set(["B", "F", "C", "D"]))
    })

    it("should expand the focused node even when it is opaque", () => {
        const edges = [lineage("A", "F"), lineage("F", "B")]
        const trace = computeTrace(edges, "F", (id) => id === "F")

        expect(trace?.nodes).toEqual(new Set(["A", "F", "B"]))
    })

    it("should terminate on a directed cycle and include every member", () => {
        const edges = [lineage("A", "B"), lineage("B", "C"), lineage("C", "A")]
        const trace = computeTrace(edges, "A")

        expect(trace?.nodes).toEqual(new Set(["A", "B", "C"]))
        expect(trace?.edges).toEqual(keys(["A", "B"], ["B", "C"], ["C", "A"]))
    })

    it("should find upstream ancestors reachable only through a cycle member found downstream", () => {
        // B -> C -> X -> B is a cycle; Z feeds X and is an ancestor of B only through that cycle.
        const edges = [lineage("B", "C"), lineage("C", "X"), lineage("X", "B"), lineage("Z", "X")]
        const trace = computeTrace(edges, "B")

        expect(trace?.nodes).toEqual(new Set(["B", "C", "X", "Z"]))
    })
})

describe("extendTraceWithDirectEdges", () => {
    it("should add a direct outgoing non-lineage neighbour", () => {
        const base = computeTrace([], "A")
        const extended = extendTraceWithDirectEdges(base, [related("A", "R")], "A", isLineageEdge)

        expect(extended?.nodes).toEqual(new Set(["A", "R"]))
        expect(extended?.edges).toEqual(keys(["A", "R"]))
    })

    it("should add a direct incoming non-lineage neighbour under its own orientation", () => {
        const base = computeTrace([], "A")
        const extended = extendTraceWithDirectEdges(base, [partOf("R", "A")], "A", isLineageEdge)

        expect(extended?.nodes).toEqual(new Set(["A", "R"]))
        expect(extended?.edges).toEqual(keys(["R", "A"]))
    })

    it("should add neighbours in both directions in one call", () => {
        const base = computeTrace([], "A")
        const extended = extendTraceWithDirectEdges(
            base,
            [related("A", "R1"), partOf("R2", "A")],
            "A",
            isLineageEdge,
        )

        expect(extended?.nodes).toEqual(new Set(["A", "R1", "R2"]))
    })

    it("should not add a second hop beyond a direct non-lineage neighbour", () => {
        const edges = [related("A", "R"), related("R", "Z"), related("Z", "R")]
        const extended = extendTraceWithDirectEdges(computeTrace([], "A"), edges, "A", isLineageEdge)

        expect(extended?.nodes.has("Z")).toBe(false)
        expect(extended?.edges.has(traceEdgeKey("R", "Z"))).toBe(false)
    })

    it("should not add the lineage ancestors or descendants of a direct neighbour", () => {
        const edges = [related("A", "R"), lineage("R", "Q"), lineage("P", "R")]
        const extended = extendTraceWithDirectEdges(computeTrace([], "A"), edges, "A", isLineageEdge)

        expect(extended?.nodes).toEqual(new Set(["A", "R"]))
    })

    it("should not expand a lineage edge touching the focus, leaving that to computeTrace", () => {
        const extended = extendTraceWithDirectEdges(computeTrace([], "A"), [lineage("A", "L")], "A", isLineageEdge)

        expect(extended?.nodes).toEqual(new Set(["A"]))
        expect(extended?.edges).toEqual(new Set())
    })

    it("should treat a kindless edge as lineage and so not add it", () => {
        const extended = extendTraceWithDirectEdges(
            computeTrace([], "A"),
            [{source: "A", target: "K"}],
            "A",
            isLineageEdge,
        )

        expect(extended?.nodes.has("K")).toBe(false)
    })

    it("should keep the members of the base trace", () => {
        const base = computeTrace([lineage("A", "B"), lineage("C", "A")], "A")
        const extended = extendTraceWithDirectEdges(base, [related("A", "R")], "A", isLineageEdge)

        expect(extended?.nodes).toEqual(new Set(["A", "B", "C", "R"]))
        expect(extended?.edges).toEqual(keys(["A", "B"], ["C", "A"], ["A", "R"]))
    })

    it("should add a neighbour already in the base trace without duplicating it", () => {
        const base = computeTrace([lineage("A", "B")], "A")
        const extended = extendTraceWithDirectEdges(base, [related("A", "B"), related("A", "B")], "A", isLineageEdge)

        expect(extended?.nodes).toEqual(new Set(["A", "B"]))
        expect(extended?.edges).toEqual(keys(["A", "B"]))
    })

    it("should return the base trace itself when there is no focused node", () => {
        const base = computeTrace([lineage("A", "B")], "A")

        expect(extendTraceWithDirectEdges(base, [related("A", "R")], undefined, isLineageEdge)).toBe(base)
    })

    it("should return null when there is no focused node and no base trace", () => {
        expect(extendTraceWithDirectEdges(null, [related("A", "R")], undefined, isLineageEdge)).toBeNull()
    })
})

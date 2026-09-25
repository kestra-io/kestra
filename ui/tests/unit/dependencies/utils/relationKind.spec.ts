import {describe, it, expect} from "vitest"
import {edgeKindToken, isLineageEdge, RELATION_KINDS} from "../../../../src/components/dependencies/utils/relationKind"

describe("relationKind.ts", () => {
    it("maps each relation kind that reaches the DAG to its own dependencies-edge token", () => {
        expect(edgeKindToken("PRODUCES")).toBe("--ks-dependencies-edge-produces")
        expect(edgeKindToken("CONSUMED_BY")).toBe("--ks-dependencies-edge-consumed-by")
        expect(edgeKindToken("UPSTREAM_OF")).toBe("--ks-dependencies-edge-upstream-of")
        expect(edgeKindToken("PART_OF")).toBe("--ks-dependencies-edge-part-of")
        expect(edgeKindToken("RELATED")).toBe("--ks-dependencies-edge-related")
    })

    it("returns undefined for an unknown or absent kind, so callers fall back to the default edge color", () => {
        expect(edgeKindToken("UNKNOWN")).toBeUndefined()
        expect(edgeKindToken(undefined)).toBeUndefined()
    })

    it("exposes RELATION_KINDS in the order the legend should render", () => {
        expect(RELATION_KINDS).toEqual(["PRODUCES", "CONSUMED_BY", "UPSTREAM_OF", "PART_OF", "RELATED"])
    })

    it("treats PART_OF and RELATED as non-lineage, and everything else (including no kind) as lineage", () => {
        expect(isLineageEdge("PRODUCES")).toBe(true)
        expect(isLineageEdge("CONSUMED_BY")).toBe(true)
        expect(isLineageEdge("UPSTREAM_OF")).toBe(true)
        expect(isLineageEdge(undefined)).toBe(true)
        expect(isLineageEdge("PART_OF")).toBe(false)
        expect(isLineageEdge("RELATED")).toBe(false)
    })
})

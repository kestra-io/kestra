import {describe, expect, it} from "vitest"
import {edgeKindToken, isLineageEdge, RELATION_KINDS} from "./relationKind"

describe("relationKind", () => {
    it("returns the CSS token for each known relation kind", () => {
        expect(edgeKindToken("PRODUCES")).toBe("--ks-dependencies-edge-produces")
        expect(edgeKindToken("CONSUMED_BY")).toBe("--ks-dependencies-edge-consumed-by")
        expect(edgeKindToken("UPSTREAM_OF")).toBe("--ks-dependencies-edge-upstream-of")
        expect(edgeKindToken("PART_OF")).toBe("--ks-dependencies-edge-part-of")
        expect(edgeKindToken("RELATED")).toBe("--ks-dependencies-edge-related")
    })

    it("returns undefined for an unknown kind or when kind is omitted", () => {
        expect(edgeKindToken("UNKNOWN_KIND")).toBeUndefined()
        expect(edgeKindToken("")).toBeUndefined()
        expect(edgeKindToken()).toBeUndefined()
    })

    it("exposes RELATION_KINDS in legend order with tokens for every entry", () => {
        expect(RELATION_KINDS).toEqual([
            "PRODUCES",
            "CONSUMED_BY",
            "UPSTREAM_OF",
            "PART_OF",
            "RELATED",
        ])
        for (const kind of RELATION_KINDS) {
            expect(edgeKindToken(kind)).toBeDefined()
        }
    })

    it("treats data-flow relation kinds as lineage edges", () => {
        expect(isLineageEdge("PRODUCES")).toBe(true)
        expect(isLineageEdge("CONSUMED_BY")).toBe(true)
        expect(isLineageEdge("UPSTREAM_OF")).toBe(true)
    })

    it("excludes PART_OF and RELATED from lineage edges", () => {
        expect(isLineageEdge("PART_OF")).toBe(false)
        expect(isLineageEdge("RELATED")).toBe(false)
    })

    it("treats kindless and unknown edges as lineage edges", () => {
        expect(isLineageEdge()).toBe(true)
        expect(isLineageEdge("")).toBe(true)
        expect(isLineageEdge("CUSTOM_KIND")).toBe(true)
    })
})

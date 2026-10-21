import {describe, it, expect} from "vitest"
import {edgeKindToken, isLineageEdge} from "../../../../src/components/dependencies/utils/relationKind"

describe("relationKind.ts", () => {
    it("returns undefined for an unknown or absent kind, so callers fall back to the default edge color", () => {
        expect(edgeKindToken("UNKNOWN")).toBeUndefined()
        expect(edgeKindToken(undefined)).toBeUndefined()
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

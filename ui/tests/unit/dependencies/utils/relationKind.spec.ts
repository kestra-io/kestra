import {describe, it, expect} from "vitest"
import {edgeKindToken, RELATION_KINDS} from "../../../../src/components/dependencies/utils/relationKind"

describe("relationKind.ts", () => {
    it("maps each relation kind that reaches the DAG to its own dependencies-edge token", () => {
        expect(edgeKindToken("PRODUCES")).toBe("--ks-dependencies-edge-produces")
        expect(edgeKindToken("CONSUMED_BY")).toBe("--ks-dependencies-edge-consumed-by")
        expect(edgeKindToken("UPSTREAM_OF")).toBe("--ks-dependencies-edge-upstream-of")
    })

    it("returns undefined for an unknown or absent kind, so callers fall back to the default edge color", () => {
        expect(edgeKindToken("PART_OF")).toBeUndefined()
        expect(edgeKindToken(undefined)).toBeUndefined()
    })

    it("exposes RELATION_KINDS in the order the legend should render", () => {
        expect(RELATION_KINDS).toEqual(["PRODUCES", "CONSUMED_BY", "UPSTREAM_OF"])
    })
})

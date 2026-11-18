import {describe, expect, it} from "vitest"
import {edgeKindToken, isLineageEdge, RELATION_KINDS} from "./relationKind"

describe("edgeKindToken", () => {
    it.each([
        ["PRODUCES", "--ks-dependencies-edge-produces"],
        ["CONSUMED_BY", "--ks-dependencies-edge-consumed-by"],
        ["UPSTREAM_OF", "--ks-dependencies-edge-upstream-of"],
        ["PART_OF", "--ks-dependencies-edge-part-of"],
        ["RELATED", "--ks-dependencies-edge-related"],
    ])("should return the token for %s", (kind, token) => {
        expect(edgeKindToken(kind)).toBe(token)
    })

    it("should return undefined for an unknown or missing kind", () => {
        expect(edgeKindToken("UNKNOWN")).toBeUndefined()
        expect(edgeKindToken("")).toBeUndefined()
        expect(edgeKindToken()).toBeUndefined()
    })
})

describe("RELATION_KINDS", () => {
    it("should list all tokenized kinds in legend order", () => {
        expect(RELATION_KINDS).toEqual([
            "PRODUCES",
            "CONSUMED_BY",
            "UPSTREAM_OF",
            "PART_OF",
            "RELATED",
        ])
    })
})

describe("isLineageEdge", () => {
    it.each(["PRODUCES", "CONSUMED_BY", "UPSTREAM_OF"])("should follow %s during the DAG rank walk", (kind) => {
        expect(isLineageEdge(kind)).toBe(true)
    })

    it.each(["PART_OF", "RELATED"])("should skip %s during the DAG rank walk", (kind) => {
        expect(isLineageEdge(kind)).toBe(false)
    })

    it("should follow an unknown or kindless edge", () => {
        expect(isLineageEdge("UNKNOWN")).toBe(true)
        expect(isLineageEdge("")).toBe(true)
        expect(isLineageEdge()).toBe(true)
    })
})

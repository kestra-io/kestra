import {describe, it, expect} from "vitest"
import placeholder from "./flow-schema.json" with {type: "json"}

function refsOf(node: unknown, refs: string[] = []): string[] {
    if (Array.isArray(node)) {
        node.forEach((child) => refsOf(child, refs))
    } else if (node && typeof node === "object") {
        for (const [key, value] of Object.entries(node)) {
            if (key === "$ref" && typeof value === "string") refs.push(value)
            else refsOf(value, refs)
        }
    }
    return refs
}

describe("flow schema placeholder", () => {
    // A dangling $ref renders as an empty field until the full schema lands, then the form changes under the user.
    it("carries every definition it references", () => {
        const known = new Set(Object.keys(placeholder.definitions).map((key) => `#/definitions/${key}`))

        expect(refsOf(placeholder).filter((ref) => !known.has(ref))).toEqual([])
    })
})

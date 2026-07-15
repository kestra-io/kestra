import {describe, expect, it} from "vitest"
import {computeSwScope} from "../../../src/utils/serviceWorker"

describe("computeSwScope", () => {
    it("resolves an empty basePath to the ui root scope", () => {
        expect(computeSwScope("")).toBe("/ui/")
    })

    it("resolves the root basePath to the ui root scope", () => {
        expect(computeSwScope("/")).toBe("/ui/")
    })

    it("resolves null/undefined the same as an empty basePath", () => {
        expect(computeSwScope(null)).toBe("/ui/")
        expect(computeSwScope(undefined)).toBe("/ui/")
    })

    it("appends ui/ under a sub-path basePath without a trailing slash", () => {
        expect(computeSwScope("/kestra")).toBe("/kestra/ui/")
    })

    it("appends ui/ under a sub-path basePath with a trailing slash", () => {
        expect(computeSwScope("/kestra/")).toBe("/kestra/ui/")
    })

    it("adds a leading slash when the basePath is missing one (reverse-proxy edge case)", () => {
        expect(computeSwScope("kestra")).toBe("/kestra/ui/")
    })

    it("collapses repeated trailing slashes", () => {
        expect(computeSwScope("/kestra///")).toBe("/kestra/ui/")
    })
})

import {describe, expect, it} from "vitest"
import {safePath} from "./useBaseNamespaces"

describe("safePath", () => {
    it("preserves an ordinary dotted namespace", () => {
        expect(safePath("company.team.analytics")).toBe("company.team.analytics")
    })

    it("encodes special characters while preserving slash separators", () => {
        expect(safePath("company.team/reports & metrics")).toBe(
            "company.team/reports%20%26%20metrics",
        )
    })

    it("returns an empty string for an empty path", () => {
        expect(safePath("")).toBe("")
    })

    it("preserves leading and trailing path separators", () => {
        expect(safePath("/company.team/")).toBe("/company.team/")
    })

    it("round-trips through URI decoding", () => {
        const path = "company.team/my folder/report#1"

        expect(decodeURIComponent(safePath(path))).toBe(path)
    })
})

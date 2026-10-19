import {describe, it, expect} from "vitest"
import {safePath} from "./useBaseNamespaces"

describe("safePath", () => {
    it("encodes characters that break a query value while preserving slash separators", () => {
        const path = "reports/q&a #1/100%.csv"
        const result = safePath(path)

        expect(result).toBe("reports/q%26a%20%231/100%25.csv")
        expect(decodeURIComponent(result)).toBe(path)
    })
})

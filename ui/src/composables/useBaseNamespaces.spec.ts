import { expect, test } from "vitest"
import { safePath } from "./useBaseNamespaces"

test("namespaces", () => {
    const path = "reports/q&a #1/100%.csv"
    const result = safePath(path)

    expect(result).toBe("reports/q%26a%20%231/100%25.csv")
    expect(decodeURIComponent(result)).toBe(path)
})
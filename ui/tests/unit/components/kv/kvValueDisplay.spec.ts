import {describe, test, expect} from "vitest"

import {formatKvValueForDisplay} from "../../../../src/components/kv/kvValueDisplay"

describe("formatKvValueForDisplay", () => {
    test("STRING is rendered as-is", () => {
        expect(formatKvValueForDisplay("STRING", "hello")).toBe("hello")
    })

    test("NUMBER is stringified", () => {
        expect(formatKvValueForDisplay("NUMBER", 42)).toBe("42")
    })

    test("BOOLEAN is stringified", () => {
        expect(formatKvValueForDisplay("BOOLEAN", true)).toBe("true")
    })

    test("JSON is pretty-printed", () => {
        expect(formatKvValueForDisplay("JSON", {a: 1})).toBe("{\n  \"a\": 1\n}")
    })

    test("DATETIME is formatted in the provided timezone", () => {
        // 2024-01-01T00:00:00Z viewed in UTC stays at midnight
        const utc = formatKvValueForDisplay("DATETIME", "2024-01-01T00:00:00Z", "UTC")
        expect(utc).toContain("2024-01-01")
        // Same instant in a +ahead timezone shifts the local date/time
        const tokyo = formatKvValueForDisplay("DATETIME", "2024-01-01T00:00:00Z", "Asia/Tokyo")
        expect(tokyo).toContain("2024-01-01T09:00:00")
    })
})

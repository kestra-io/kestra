import {describe, test, expect} from "vitest"
import {normalizeRelativeDate} from "./relativeDates"

describe("normalizeRelativeDate", () => {
    test.each([
        ["P1D", "PT24H"],
        ["P2D", "PT48H"],
        ["P7D", "PT168H"],
        ["P1W", "PT168H"],
        ["P30D", "PT720H"],
        ["P365D", "PT8760H"],
    ])("normalizes %s to %s", (value, expected) => {
        expect(normalizeRelativeDate(value)).toBe(expected)
    })

    test("normalizes day and week aliases to the same value", () => {
        expect(normalizeRelativeDate("P7D")).toBe(normalizeRelativeDate("P1W"))
    })

    test.each(["PT24H", "PT48H", "PT168H", "PT720H", "PT8760H"])(
        "returns the canonical value %s unchanged", (value) => {
            expect(normalizeRelativeDate(value)).toBe(value)
        },
    )

    test.each(["P3D", "unknown"])("passes through the unknown value %s", (value) => {
        expect(normalizeRelativeDate(value)).toBe(value)
    })

    test("passes through an empty string", () => {
        expect(normalizeRelativeDate("")).toBe("")
    })
})

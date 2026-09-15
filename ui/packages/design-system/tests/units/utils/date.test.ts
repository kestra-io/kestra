import {describe, test, expect} from "vitest"
import {parseIso, toIsoKeepOffset} from "../../../src/utils/date"
import dayjs from "../../../src/date/dayjs"

describe("parseIso", () => {
    test("should parse an ISO 8601 instant", () => {
        expect(parseIso("2026-07-24T13:16:00.000Z").toISOString()).toBe("2026-07-24T13:16:00.000Z")
        expect(parseIso("2026-07-24").isValid()).toBe(true)
    })

    test.each([
        ["an epoch millisecond timestamp", 1753362960000],
        ["a bare year", "2026"],
        ["a chart category label", "not-a-date"],
        ["a nullish value", null],
    ])("should reject %s, which bare dayjs() would coerce", (_label, value) => {
        expect(parseIso(value).isValid()).toBe(false)
    })
})

describe("toIsoKeepOffset", () => {
    test("should keep the instance offset where toISOString would convert to UTC", () => {
        const inTokyo = dayjs("2026-07-24T13:16:00.000Z").tz("Asia/Tokyo")

        expect(toIsoKeepOffset(inTokyo)).toBe("2026-07-24T22:16:00.000+09:00")
        expect(inTokyo.toISOString()).toBe("2026-07-24T13:16:00.000Z")
    })
})

import {describe, test, expect} from "vitest"
import {parseIso, timezonesWithOffset, toIsoKeepOffset} from "../../../src/utils/date"
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

describe("timezonesWithOffset", () => {
    test("should list the canonical zones with their current offset", () => {
        const zones = timezonesWithOffset()

        expect(zones.length).toBeGreaterThan(100)
        expect(zones.find((z) => z.zone === "Asia/Tokyo")?.formattedOffset).toBe("+09:00")
    })

    // Intl lists canonical zones only, so a stored link name would otherwise vanish from the
    // Settings picker and the user would lose the setting on the next edit.
    test("should append a stored zone that Intl omits, such as a link name", () => {
        expect(timezonesWithOffset().some((z) => z.zone === "US/Eastern")).toBe(false)
        expect(timezonesWithOffset("US/Eastern").some((z) => z.zone === "US/Eastern")).toBe(true)
    })

    test("should not duplicate a stored zone that is already canonical", () => {
        const zones = timezonesWithOffset("Asia/Tokyo")
        expect(zones.filter((z) => z.zone === "Asia/Tokyo")).toHaveLength(1)
    })
})

describe("dayjs instance", () => {
    test("should render the ordinal day token offered by the date format setting", () => {
        expect(dayjs("2026-07-24T13:16:00.000Z").utc().format("dddd, MMMM Do YYYY")).toBe("Friday, July 24th 2026")
    })
})

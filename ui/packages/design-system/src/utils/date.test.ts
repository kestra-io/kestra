import {describe, test, expect, vi, afterEach} from "vitest"
import {
    currentLocale,
    currentTimezone,
    dateFilter,
    parseIso,
    setLocale,
    timezonesWithOffset,
    toIsoKeepOffset,
    TIMEZONE_STORAGE_KEY,
} from "./date"
import dayjs from "../date/dayjs"

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

    test("should refresh another zone's offset after a DST change on the same UTC day", () => {
        vi.useFakeTimers()
        const localOffset = vi.spyOn(Date.prototype, "getTimezoneOffset").mockReturnValue(-330)

        try {
            vi.setSystemTime(new Date("2026-03-08T06:59:00Z"))
            expect(timezonesWithOffset("America/New_York")
                .find(({zone}) => zone === "America/New_York")?.formattedOffset).toBe("-05:00")

            vi.setSystemTime(new Date("2026-03-08T07:01:00Z"))
            expect(timezonesWithOffset("America/New_York")
                .find(({zone}) => zone === "America/New_York")?.formattedOffset).toBe("-04:00")
        } finally {
            localOffset.mockRestore()
            vi.useRealTimers()
        }
    })
})

describe("dayjs instance", () => {
    test("should render the ordinal day token offered by the date format setting", () => {
        expect(dayjs("2026-07-24T13:16:00.000Z").utc().format("dddd, MMMM Do YYYY")).toBe("Friday, July 24th 2026")
    })
})

describe("currentLocale and setLocale", () => {
    afterEach(async () => {
        await setLocale("en")
    })

    test("should return a sensible default when none is explicitly set", () => {
        expect(currentLocale()).toBe("en")
    })

    test("should change what currentLocale returns when setLocale is called", async () => {
        await setLocale("fr")
        expect(currentLocale()).toBe("fr")

        await setLocale("pt_BR")
        expect(currentLocale()).toBe("pt-br")
    })

    test("should affect subsequent date formatting, not just the getter", async () => {
        expect(dayjs("2026-07-24").format("MMMM")).toBe("July")

        await setLocale("fr")
        expect(dayjs("2026-07-24").format("MMMM")).toBe("juillet")
        expect(dateFilter("2026-07-24T12:00:00Z", "dddd")).toBe("vendredi")
    })

    test("should fall back to default when an unknown or malformed locale is provided rather than throwing", async () => {
        await setLocale("unknown-locale")
        expect(currentLocale()).toBe("en")

        await setLocale("")
        expect(currentLocale()).toBe("en")
    })
})

describe("currentTimezone", () => {
    afterEach(() => {
        localStorage.removeItem(TIMEZONE_STORAGE_KEY)
        vi.restoreAllMocks()
    })

    test("should return the stored timezone when one is configured", () => {
        localStorage.setItem(TIMEZONE_STORAGE_KEY, "America/New_York")
        expect(currentTimezone()).toBe("America/New_York")
    })

    test("should fall back to the browser timezone when none is stored", () => {
        localStorage.removeItem(TIMEZONE_STORAGE_KEY)
        vi.spyOn(dayjs.tz, "guess").mockReturnValue("Europe/Paris")

        expect(currentTimezone()).toBe("Europe/Paris")
    })

    test("should fall back to the browser timezone when stored value is malformed or empty rather than throwing", () => {
        localStorage.setItem(TIMEZONE_STORAGE_KEY, "")
        vi.spyOn(dayjs.tz, "guess").mockReturnValue("UTC")

        expect(currentTimezone()).toBe("UTC")
    })
})


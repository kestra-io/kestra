import {describe, test, expect, beforeEach, afterEach} from "vitest"
import {
    parseIso,
    timezonesWithOffset,
    toIsoKeepOffset,
    currentLocale,
    setLocale,
    currentTimezone,
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
})

describe("currentLocale and setLocale", () => {
    beforeEach(async () => {
        await setLocale("en")
    })

    afterEach(async () => {
        await setLocale("en")
    })

    test("should return 'en' by default when none is stored or set", () => {
        expect(currentLocale()).toBe("en")
    })

    test("should change what currentLocale returns when setLocale is called", async () => {
        await setLocale("fr")
        expect(currentLocale()).toBe("fr")

        await setLocale("de")
        expect(currentLocale()).toBe("de")
    })

    test("should normalize case and underscore in locale keys, e.g. pt_BR to pt-br", async () => {
        await setLocale("pt_BR")
        expect(currentLocale()).toBe("pt-br")
    })

    test("should affect subsequent date formatting, not just the getter", async () => {
        await setLocale("fr")
        expect(dayjs("2026-07-24").format("MMMM")).toBe("juillet")

        await setLocale("en")
        expect(dayjs("2026-07-24").format("MMMM")).toBe("July")
    })

    test("should fallback to 'en' rather than throwing on an unknown or malformed locale", async () => {
        await setLocale("unsupported_xyz_locale")
        expect(currentLocale()).toBe("en")
    })
})

describe("currentTimezone", () => {
    beforeEach(() => {
        localStorage.clear()
    })

    afterEach(() => {
        localStorage.clear()
    })

    test("should return the stored timezone when set in localStorage", () => {
        localStorage.setItem(TIMEZONE_STORAGE_KEY, "America/New_York")
        expect(currentTimezone()).toBe("America/New_York")
    })

    test("should fall back to the browser's guessed timezone when none is stored", () => {
        localStorage.removeItem(TIMEZONE_STORAGE_KEY)
        expect(currentTimezone()).toBe(dayjs.tz.guess())
    })

    test("should fall back to guessed timezone when stored value is empty", () => {
        localStorage.setItem(TIMEZONE_STORAGE_KEY, "")
        expect(currentTimezone()).toBe(dayjs.tz.guess())
    })
})

describe("dayjs instance", () => {
    test("should render the ordinal day token offered by the date format setting", () => {
        expect(dayjs("2026-07-24T13:16:00.000Z").utc().format("dddd, MMMM Do YYYY")).toBe("Friday, July 24th 2026")
    })
})

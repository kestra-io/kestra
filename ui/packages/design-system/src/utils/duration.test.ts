import {afterEach, describe, test, expect} from "vitest"
import {duration, humanDuration, isValidDuration} from "./duration"

describe("isValidDuration", () => {
    test.each(["PT1H30M", "P1DT2H", "P1Y2M3W4D", "-PT5S", "PT0.5S"])("accepts %s", (value) => {
        expect(isValidDuration(value)).toBe(true)
    })

    // The no-code editor reads "not a duration" as "a Pebble expression", so these must not pass.
    test.each(["{{ vars.timeout }}", "1h", "P", "PT", "", "PTS"])("rejects %s", (value) => {
        expect(isValidDuration(value)).toBe(false)
    })
})

describe("duration", () => {
    test("should convert an ISO 8601 duration to seconds", () => {
        expect(duration("PT1H30M")).toBe(5400)
    })

    test("should return zero rather than NaN for text that is not a duration", () => {
        expect(duration("{{ vars.timeout }}")).toBe(0)
    })

    test("should keep the sign of a negative duration, which dayjs parses as positive", () => {
        expect(duration("-PT5S")).toBe(-5)
    })
})

describe("humanDuration", () => {
    afterEach(() => {
        localStorage.removeItem("lang")
    })

    test("falls back to the base language for a regional locale humanize-duration does not know", () => {
        localStorage.setItem("lang", "pt_BR")
        expect(() => humanDuration(65)).not.toThrow()
        localStorage.setItem("lang", "pt")
        const portuguese = humanDuration(65)
        localStorage.setItem("lang", "pt_BR")
        expect(humanDuration(65)).toBe(portuguese)
    })

    test("falls back to English for a language humanize-duration does not know at all", () => {
        localStorage.setItem("lang", "xx")
        expect(humanDuration(65)).toBe("1m, 5s")
    })

    test("keeps the custom short units for English", () => {
        localStorage.setItem("lang", "en")
        expect(humanDuration(65)).toBe("1m, 5s")
    })
})

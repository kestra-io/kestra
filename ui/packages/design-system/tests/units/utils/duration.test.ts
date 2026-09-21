import {describe, test, expect} from "vitest"
import {duration, isValidDuration} from "../../../src/utils/duration"

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

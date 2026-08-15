import {describe, it, expect} from "vitest"
import moment from "moment-timezone"

// vite.config.js aliases moment-timezone to the 1970-2030 build to keep the full IANA
// database out of the boot chunk. These guard the two things that trade-off can break:
// the zone list the timezone picker renders, and offset accuracy inside the window.
describe("moment-timezone bundled data", () => {
    const DATA_RANGE_END = 2030

    it("should expose the full zone list for the timezone picker", () => {
        const names = moment.tz.names()

        expect(names.length).toBeGreaterThan(500)
        expect(names).toContain("Europe/Paris")
        expect(names).toContain("America/New_York")
        expect(names).toContain("Asia/Tokyo")
        expect(names).toContain("Australia/Sydney")
    })

    it.each([
        // Historical dates matter: executions and backfills are displayed in the user's timezone.
        ["2015-07-01", "Europe/Paris", "+02:00"],
        ["2015-01-01", "Europe/Paris", "+01:00"],
        ["2015-07-01", "America/New_York", "-04:00"],
        ["2015-01-01", "America/New_York", "-05:00"],
        ["2015-07-01", "Australia/Sydney", "+10:00"],
        // France had no summer time between 1946 and 1976, so this only passes with real
        // historical rules rather than today's rule projected backwards.
        ["1975-07-01", "Europe/Paris", "+01:00"],
        ["1980-07-01", "Europe/Paris", "+02:00"],
        ["2024-07-01", "Europe/Paris", "+02:00"],
        ["2029-07-01", "Europe/Paris", "+02:00"],
    ])("should resolve the DST offset for %s in %s", (date, zone, expected) => {
        expect(moment.tz(date, zone).format("Z")).toBe(expected)
    })

    // The bundled data stops at 2030. Fail while there is still room to react, rather than
    // silently rendering standard time for in-range dates once the window is passed.
    it("should still cover dates two years out", () => {
        const twoYearsOut = moment().add(2, "years")

        expect(
            twoYearsOut.year(),
            `moment-timezone data stops at ${DATA_RANGE_END}; switch the vite.config.js alias to a wider build`,
        ).toBeLessThanOrEqual(DATA_RANGE_END)
    })
})

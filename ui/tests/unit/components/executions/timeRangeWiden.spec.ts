import {describe, expect, it, vi} from "vitest"
import {
    FALLBACK_TIME_RANGE,
    isSameTimeRange,
    queryHasAbsoluteDateFilter,
    queryHasTimeBound,
    readTimeRangeFromQuery,
    remainingWidenWindows,
    timeRangeWidenSequence,
    widenEmptyTimeRange,
} from "../../../../src/components/executions/timeRangeWiden"

describe("timeRangeWidenSequence", () => {
    it("starts at 24 hours and then steps to 7 days and 30 days", () => {
        expect(timeRangeWidenSequence("PT24H")).toEqual(["PT24H", "PT168H", "P30D"])
    })

    it("keeps a shorter configured default and then walks 24 hours, 7 days, 30 days", () => {
        expect(timeRangeWidenSequence("PT1H")).toEqual(["PT1H", "PT24H", "PT168H", "P30D"])
    })

    it("skips 7 days when the start is already that long", () => {
        expect(timeRangeWidenSequence("P7D")).toEqual(["P7D", "P30D"])
        expect(timeRangeWidenSequence("PT168H")).toEqual(["PT168H", "P30D"])
    })

    it("does not walk past a start that is already 30 days or longer", () => {
        expect(timeRangeWidenSequence("P30D")).toEqual(["P30D"])
        expect(timeRangeWidenSequence("PT8760H")).toEqual(["PT8760H"])
    })
})

describe("remainingWidenWindows", () => {
    it("returns the next bounded windows after the current default", () => {
        expect(remainingWidenWindows("PT24H", "PT24H")).toEqual(["PT168H", "P30D"])
    })

    it("treats P7D as the same length as PT168H so the next step is 30 days", () => {
        expect(remainingWidenWindows("P7D", "PT24H")).toEqual(["P30D"])
    })

    it("does not widen a relative range the user picked outside the default ladder", () => {
        expect(remainingWidenWindows("PT5M", "PT24H")).toEqual([])
    })
})

describe("isSameTimeRange", () => {
    it("equates ISO forms that describe the same length", () => {
        expect(isSameTimeRange("P7D", "PT168H")).toBe(true)
        expect(isSameTimeRange("P30D", "PT720H")).toBe(true)
        expect(isSameTimeRange("PT24H", "PT1H")).toBe(false)
    })
})

describe("query time-range readers", () => {
    it("reads the EQUALS timeRange filter, including duplicate-key arrays", () => {
        expect(readTimeRangeFromQuery({"filters[timeRange][EQUALS]": "PT24H"})).toBe("PT24H")
        expect(readTimeRangeFromQuery({"filters[timeRange][EQUALS]": ["PT168H"]})).toBe("PT168H")
        expect(readTimeRangeFromQuery({})).toBeUndefined()
    })

    it("treats relative and absolute date filters as a time bound", () => {
        expect(queryHasTimeBound({"filters[timeRange][EQUALS]": "PT24H"})).toBe(true)
        expect(queryHasTimeBound({"filters[startDate][GREATER_THAN_OR_EQUAL_TO]": "2026-01-01"})).toBe(true)
        expect(queryHasTimeBound({"filters[state][IN]": "FAILED"})).toBe(false)
        expect(queryHasAbsoluteDateFilter({"filters[endDate][LESS_THAN_OR_EQUAL_TO]": "2026-01-02"})).toBe(true)
        expect(queryHasAbsoluteDateFilter({"filters[timeRange][EQUALS]": "PT24H"})).toBe(false)
    })
})

describe("widenEmptyTimeRange", () => {
    it("does not search further when the current window has rows", async () => {
        const search = vi.fn()

        const result = await widenEmptyTimeRange({
            currentTimeRange: "PT24H",
            defaultTimeRange: FALLBACK_TIME_RANGE,
            hasAbsoluteDateFilter: false,
            alreadyAttempted: false,
            currentTotal: 12,
            search,
        })

        expect(search).not.toHaveBeenCalled()
        expect(result).toEqual({timeRange: "PT24H", widened: false})
    })

    it("widens to the first bounded window that has rows and stops there", async () => {
        const search = vi.fn().mockResolvedValueOnce(4)

        const result = await widenEmptyTimeRange({
            currentTimeRange: "PT24H",
            defaultTimeRange: "PT24H",
            hasAbsoluteDateFilter: false,
            alreadyAttempted: false,
            currentTotal: 0,
            search,
        })

        expect(search.mock.calls.map(call => call[0])).toEqual(["PT168H"])
        expect(result).toEqual({timeRange: "PT168H", widened: true})
    })

    it("keeps the last bounded window when every step is empty", async () => {
        const search = vi.fn().mockResolvedValue(0)

        const result = await widenEmptyTimeRange({
            currentTimeRange: "PT24H",
            defaultTimeRange: "PT24H",
            hasAbsoluteDateFilter: false,
            alreadyAttempted: false,
            currentTotal: 0,
            search,
        })

        expect(search.mock.calls.map(call => call[0])).toEqual(["PT168H", "P30D"])
        expect(search.mock.calls.every(call => typeof call[0] === "string" && call[0].startsWith("P"))).toBe(true)
        expect(result).toEqual({timeRange: "P30D", widened: true})
    })

    it("starts from the configured default rather than a hardcoded 24 hours", async () => {
        const search = vi.fn().mockResolvedValueOnce(3)

        const result = await widenEmptyTimeRange({
            currentTimeRange: "PT1H",
            defaultTimeRange: "PT1H",
            hasAbsoluteDateFilter: false,
            alreadyAttempted: false,
            currentTotal: 0,
            search,
        })

        expect(search.mock.calls.map(call => call[0])).toEqual(["PT24H"])
        expect(result).toEqual({timeRange: "PT24H", widened: true})
    })

    it("does not widen a custom relative range, an absolute date filter, or a later load", async () => {
        const search = vi.fn()

        await expect(widenEmptyTimeRange({
            currentTimeRange: "PT5M",
            defaultTimeRange: "PT24H",
            hasAbsoluteDateFilter: false,
            alreadyAttempted: false,
            currentTotal: 0,
            search,
        })).resolves.toEqual({timeRange: "PT5M", widened: false})

        await expect(widenEmptyTimeRange({
            currentTimeRange: undefined,
            defaultTimeRange: "PT24H",
            hasAbsoluteDateFilter: true,
            alreadyAttempted: false,
            currentTotal: 0,
            search,
        })).resolves.toEqual({timeRange: undefined, widened: false})

        await expect(widenEmptyTimeRange({
            currentTimeRange: "PT24H",
            defaultTimeRange: "PT24H",
            hasAbsoluteDateFilter: false,
            alreadyAttempted: true,
            currentTotal: 0,
            search,
        })).resolves.toEqual({timeRange: "PT24H", widened: false})

        expect(search).not.toHaveBeenCalled()
    })
})

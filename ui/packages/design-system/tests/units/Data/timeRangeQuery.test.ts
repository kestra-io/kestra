import {describe, test, expect} from "vitest"
import {normalizeRouteTimeRangeFilter} from "../../../src/components/Data/KsDataTable/filter/utils/timeRangeQuery"

describe("normalizeRouteTimeRangeFilter", () => {
    test("sets a single EQUALS filter for the given value", () => {
        expect(normalizeRouteTimeRangeFilter({}, "PT1H")).toEqual({
            "filters[timeRange][EQUALS]": "PT1H",
        })
    })

    test("replaces any existing timeRange filters with a single EQUALS", () => {
        expect(
            normalizeRouteTimeRangeFilter(
                {"filters[timeRange][IN]": "PT5M", "filters[timeRange][EQUALS]": "PT15M"},
                "PT24H",
            ),
        ).toEqual({"filters[timeRange][EQUALS]": "PT24H"})
    })

    test("removes the legacy timeRange key", () => {
        expect(normalizeRouteTimeRangeFilter({timeRange: "PT12H"}, "PT24H")).toEqual({
            "filters[timeRange][EQUALS]": "PT24H",
        })
    })

    test("strips explicit startDate/endDate (the time-range derives them)", () => {
        expect(
            normalizeRouteTimeRangeFilter(
                {startDate: "2024-01-01", endDate: "2024-01-02"},
                "PT1H",
            ),
        ).toEqual({"filters[timeRange][EQUALS]": "PT1H"})
    })

    test("drops the filter entirely when value is undefined", () => {
        expect(
            normalizeRouteTimeRangeFilter({"filters[timeRange][EQUALS]": "PT1H"}, undefined),
        ).toEqual({})
    })

    test("preserves unrelated filters", () => {
        expect(
            normalizeRouteTimeRangeFilter(
                {"filters[namespace][EQUALS]": "demo", startDate: "x", endDate: "y"},
                "PT7D",
            ),
        ).toEqual({
            "filters[namespace][EQUALS]": "demo",
            "filters[timeRange][EQUALS]": "PT7D",
        })
    })
})

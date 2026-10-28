import {describe, it, expect} from "vitest"
import {dayjs} from "@kestra-io/design-system"
import {
    BRUSH_END_KEY,
    BRUSH_START_KEY,
    bucketLabelToDateRange,
    buildBrushClearQuery,
    buildBrushTimeRangeQuery,
    isBrushableUnit,
    pixelSelectionToBucketIndices,
    readBrushRange,
} from "./logsBrushMappers"
import {getDateGrouping} from "./utils"
import {routeQueryToQueryFilters} from "./queryFilters"

describe("bucketLabelToDateRange", () => {
    it.each([
        ["P7D", "2024-03-15T14:37:12Z", "2024-03-15T00:00:00Z", "2024-03-16T00:00:00Z"],
        ["PT12H", "2024-03-15T14:37:12Z", "2024-03-15T14:00:00Z", "2024-03-15T15:00:00Z"],
        ["PT1H", "2024-03-15T14:37:12Z", "2024-03-15T14:37:00Z", "2024-03-15T14:38:00Z"],
    ])("inverts the label getDateGrouping renders for a %s window", (timeRange, instant, start, end) => {
        const grouping = getDateGrouping(undefined, undefined, timeRange)
        const label = dayjs(instant).tz("UTC").format(grouping.format)

        const result = bucketLabelToDateRange(label, grouping.unit, "UTC")

        expect(result).toEqual({startMs: Date.parse(start), endMs: Date.parse(end)})
    })

    it.each(["P200D", "P400D"])("refuses the lossy %s grouping", (timeRange) => {
        const grouping = getDateGrouping(undefined, undefined, timeRange)
        const label = dayjs("2024-03-15T00:00:00Z").tz("UTC").format(grouping.format)

        expect(isBrushableUnit(grouping.unit)).toBe(false)
        expect(bucketLabelToDateRange(label, grouping.unit, "UTC")).toBeNull()
    })

    it("parses the label in the zone that rendered it", () => {
        const result = bucketLabelToDateRange("2024-03-15 14:00", "hour", "America/New_York")

        expect(result).toEqual({
            startMs: Date.parse("2024-03-15T18:00:00Z"),
            endMs: Date.parse("2024-03-15T19:00:00Z"),
        })
    })

    it("ends a day bucket at the next local midnight across a DST change", () => {
        const result = bucketLabelToDateRange("2024-03-10", "day", "America/New_York")

        expect(result).toEqual({
            startMs: Date.parse("2024-03-10T05:00:00Z"),
            endMs: Date.parse("2024-03-11T04:00:00Z"),
        })
    })

    it("rejects a label that does not match the unit", () => {
        expect(bucketLabelToDateRange("2024-03-15", "hour", "UTC")).toBeNull()
        expect(bucketLabelToDateRange("not-a-date", "day", "UTC")).toBeNull()
    })
})

describe("pixelSelectionToBucketIndices", () => {
    const bucketPx = [10, 30, 50, 70, 90]

    it("maps a left-to-right drag to bucket indices", () => {
        expect(pixelSelectionToBucketIndices(25, 75, bucketPx)).toEqual({start: 1, end: 3})
    })

    it("normalises a right-to-left drag", () => {
        expect(pixelSelectionToBucketIndices(75, 25, bucketPx)).toEqual({start: 1, end: 3})
    })

    it("clamps to the rendered buckets", () => {
        expect(pixelSelectionToBucketIndices(-50, 999, bucketPx)).toEqual({start: 0, end: 4})
    })

    it("selects a single bucket for a short drag", () => {
        expect(pixelSelectionToBucketIndices(8, 12, bucketPx)).toEqual({start: 0, end: 0})
    })

    it("returns null without buckets", () => {
        expect(pixelSelectionToBucketIndices(10, 90, [])).toBeNull()
    })
})

describe("buildBrushTimeRangeQuery", () => {
    const range = {startDate: "2024-03-15T10:00:00.000Z", endDate: "2024-03-15T10:59:59.999Z"}

    it("writes the window as filters the logs request reads", () => {
        const query = buildBrushTimeRangeQuery({"filters[timeRange][EQUALS]": "PT24H", page: "3"}, range, "page")

        expect(routeQueryToQueryFilters(query)).toEqual(expect.arrayContaining([
            expect.objectContaining({field: "startDate", operation: "GREATER_THAN_OR_EQUAL_TO", value: range.startDate}),
            expect.objectContaining({field: "endDate", operation: "LESS_THAN_OR_EQUAL_TO", value: range.endDate}),
        ]))
        expect(routeQueryToQueryFilters(query).some((filter) => filter.field === "timeRange")).toBe(false)
    })

    it("replaces every earlier time key and resets the page", () => {
        const query = buildBrushTimeRangeQuery({
            timeRange: "PT24H",
            "filters[timeRange][EQUALS]": "PT24H",
            startDate: "2024-01-01T00:00:00.000Z",
            endDate: "2024-01-02T00:00:00.000Z",
            [BRUSH_START_KEY]: "2024-01-01T00:00:00.000Z",
            [BRUSH_END_KEY]: "2024-01-02T00:00:00.000Z",
            "filters[level][GREATER_THAN_OR_EQUAL_TO]": "WARN",
            page: "3",
            size: "25",
        }, range, "page")

        expect(query).toEqual({
            [BRUSH_START_KEY]: range.startDate,
            [BRUSH_END_KEY]: range.endDate,
            "filters[level][GREATER_THAN_OR_EQUAL_TO]": "WARN",
            page: "1",
            size: "25",
        })
    })

    it("resets the embedded page key", () => {
        const query = buildBrushTimeRangeQuery({logsPage: "5", logsSize: "50"}, range, "logsPage")

        expect(query.logsPage).toBe("1")
        expect(query.logsSize).toBe("50")
    })
})

describe("buildBrushClearQuery", () => {
    it("drops the window, restores the default interval and resets the page", () => {
        const query = buildBrushClearQuery({
            [BRUSH_START_KEY]: "2024-03-15T10:00:00.000Z",
            [BRUSH_END_KEY]: "2024-03-15T10:59:59.999Z",
            "filters[level][GREATER_THAN_OR_EQUAL_TO]": "WARN",
            page: "4",
        }, "page")

        expect(query).toEqual({
            "filters[timeRange][EQUALS]": "PT24H",
            "filters[level][GREATER_THAN_OR_EQUAL_TO]": "WARN",
            page: "1",
        })
    })
})

describe("readBrushRange", () => {
    it("reads the window the brush wrote", () => {
        const range = {startDate: "2024-03-15T10:00:00.000Z", endDate: "2024-03-15T10:59:59.999Z"}

        expect(readBrushRange(buildBrushTimeRangeQuery({}, range, "page"))).toEqual(range)
    })

    it("returns nothing without a complete window", () => {
        expect(readBrushRange({[BRUSH_START_KEY]: "2024-03-15T10:00:00.000Z"})).toBeUndefined()
    })
})

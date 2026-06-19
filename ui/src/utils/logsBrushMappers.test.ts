import {describe, it, expect} from "vitest"
import {
    bucketLabelToDateRange,
    pixelSelectionToBucketIndices,
    buildBrushTimeRangeQuery,
} from "./logsBrushMappers"

describe("bucketLabelToDateRange", () => {
    it("should parse yyyy-MM-DD format", () => {
        // Given
        const label = "2024-03-15"
        const format = "yyyy-MM-DD"
        const tz = "UTC"

        // When
        const result = bucketLabelToDateRange(label, format, tz)

        // Then
        expect(result).not.toBeNull()
        expect(result!.startMs).toBe(new Date("2024-03-15T00:00:00Z").getTime())
        expect(result!.endMs).toBe(new Date("2024-03-16T00:00:00Z").getTime())
    })

    it("should parse yyyy-MM-DD:HH:00 format", () => {
        // Given
        const label = "2024-03-15:14:00"
        const format = "yyyy-MM-DD:HH:00"
        const tz = "UTC"

        // When
        const result = bucketLabelToDateRange(label, format, tz)

        // Then
        expect(result).not.toBeNull()
        expect(result!.startMs).toBe(new Date("2024-03-15T14:00:00Z").getTime())
        expect(result!.endMs).toBe(new Date("2024-03-15T15:00:00Z").getTime())
    })

    it("should parse yyyy-MM-DD:HH:mm format", () => {
        // Given
        const label = "2024-03-15:14:37"
        const format = "yyyy-MM-DD:HH:mm"
        const tz = "UTC"

        // When
        const result = bucketLabelToDateRange(label, format, tz)

        // Then
        expect(result).not.toBeNull()
        expect(result!.startMs).toBe(new Date("2024-03-15T14:37:00Z").getTime())
        expect(result!.endMs).toBe(new Date("2024-03-15T14:38:00Z").getTime())
    })

    it("should return null for yyyy-MM format (non-invertible)", () => {
        // Given/When/Then
        expect(bucketLabelToDateRange("2024-03", "yyyy-MM", "UTC")).toBeNull()
    })

    it("should return null for ISO week format (non-invertible)", () => {
        // Given/When/Then
        expect(bucketLabelToDateRange("2024-W12", "yyyy-'W'ww", "UTC")).toBeNull()
    })

    it("should handle timezone offset correctly for yyyy-MM-DD", () => {
        // Given
        const label = "2024-03-15"
        const format = "yyyy-MM-DD"
        const tz = "America/New_York"

        // When
        const result = bucketLabelToDateRange(label, format, tz)

        // Then
        expect(result).not.toBeNull()
        // America/New_York in March is UTC-4 (EDT)
        // 2024-03-15 00:00 EST = 2024-03-15 05:00 UTC
        const startUTC = new Date(result!.startMs)
        expect(startUTC.getUTCHours()).toBe(4)
        expect(startUTC.getUTCDate()).toBe(15)
    })

    it("should return null for an invalid/unrecognized format", () => {
        // Given/When/Then
        expect(bucketLabelToDateRange("not-a-date", "unknown-format", "UTC")).toBeNull()
    })
})

describe("pixelSelectionToBucketIndices", () => {
    it("should return bucket indices from left-to-right drag", () => {
        // Given
        const bucketPx = [10, 30, 50, 70, 90]

        // When - drag from 25 to 75 (clearly within buckets 1-3)
        const result = pixelSelectionToBucketIndices(25, 75, bucketPx)

        // Then
        expect(result).toEqual({start: 1, end: 3})
    })

    it("should normalise right-to-left drag", () => {
        // Given
        const bucketPx = [10, 30, 50, 70, 90]

        // When - drag reversed, from 75 to 25
        const result = pixelSelectionToBucketIndices(75, 25, bucketPx)

        // Then
        expect(result).toEqual({start: 1, end: 3})
    })

    it("should clamp to [0, length-1]", () => {
        // Given
        const bucketPx = [10, 30, 50, 70, 90]

        // When
        const result = pixelSelectionToBucketIndices(-50, 999, bucketPx)

        // Then
        expect(result).toEqual({start: 0, end: 4})
    })

    it("should select a single bucket when drag is less than half-bucket", () => {
        // Given
        const bucketPx = [10, 30, 50, 70, 90]

        // When - drag of 4px within first bucket
        const result = pixelSelectionToBucketIndices(8, 12, bucketPx)

        // Then
        expect(result).toEqual({start: 0, end: 0})
    })

    it("should handle an empty axisData array", () => {
        // Given/When/Then
        expect(pixelSelectionToBucketIndices(10, 90, [])).toBeNull()
    })
})

describe("buildBrushTimeRangeQuery", () => {
    it("should clear timeRange and set startDate/endDate and reset page", () => {
        // Given
        const routeQuery = {
            timeRange: "PT24H",
            page: "3",
            size: "25",
        }

        // When
        const result = buildBrushTimeRangeQuery(routeQuery, "2024-03-15T00:00:00.000Z", "2024-03-16T00:00:00.000Z", "page")

        // Then
        expect(result.timeRange).toBeUndefined()
        expect(result.startDate).toBe("2024-03-15T00:00:00.000Z")
        expect(result.endDate).toBe("2024-03-16T00:00:00.000Z")
        expect(result.page).toBe("1")
        expect(result.size).toBe("25")
    })

    it("should clear encoded filter facet form of timeRange", () => {
        // Given
        const routeQuery = {
            "filters[timeRange][EQUALS]": "PT1H",
            page: "2",
        }

        // When
        const result = buildBrushTimeRangeQuery(routeQuery, "2024-03-15T10:00:00.000Z", "2024-03-15T11:00:00.000Z", "page")

        // Then
        expect(result["filters[timeRange][EQUALS]"]).toBeUndefined()
        expect(result.startDate).toBe("2024-03-15T10:00:00.000Z")
        expect(result.endDate).toBe("2024-03-15T11:00:00.000Z")
        expect(result.page).toBe("1")
    })

    it("should work with embed pageKey", () => {
        // Given
        const routeQuery = {
            logsPage: "5",
            logsSize: "50",
        }

        // When
        const result = buildBrushTimeRangeQuery(routeQuery, "2024-03-15T00:00:00.000Z", "2024-03-15T01:00:00.000Z", "logsPage")

        // Then
        expect(result.logsPage).toBe("1")
        expect(result.logsSize).toBe("50")
    })

    it("should clear both forms simultaneously", () => {
        // Given
        const routeQuery = {
            timeRange: "PT24H",
            "filters[timeRange][EQUALS]": "PT24H",
            page: "2",
        }

        // When
        const result = buildBrushTimeRangeQuery(routeQuery, "2024-03-15T00:00:00.000Z", "2024-03-16T00:00:00.000Z", "page")

        // Then
        expect(result.timeRange).toBeUndefined()
        expect(result["filters[timeRange][EQUALS]"]).toBeUndefined()
    })
})

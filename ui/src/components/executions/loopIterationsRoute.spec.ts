import {describe, expect, it} from "vitest"
import {dayjs} from "@kestra-io/design-system"
import type {RouteLocationNamedRaw} from "vue-router"
import {loopIterationsRoute} from "./loopIterationsRoute"
import {queryHasTimeBound} from "./timeRangeWiden"

const NOW = dayjs("2026-10-09T12:00:00.000Z")

function queryFor(state?: {startDate?: string; endDate?: string}, extraQuery?: Record<string, string>) {
    return (loopIterationsRoute({id: "parent", state}, "loop", extraQuery, NOW) as RouteLocationNamedRaw).query
}

describe("loopIterationsRoute", () => {
    it("shouldBoundTheListToTheParentWindowWhenTheParentEndedBeforeTheDefaultTimeRange", () => {
        const query = queryFor({startDate: "2026-10-05T08:00:00.123456Z", endDate: "2026-10-05T08:03:00Z"}, {"filters[state][IN]": "FAILED"})

        expect(query).toEqual({
            "filters[parentId][EQUALS]": "parent",
            "filters[kind][EQUALS]": "LOOP",
            "filters[taskId][EQUALS]": "loop",
            "filters[state][IN]": "FAILED",
            "filters[startDate][GREATER_THAN_OR_EQUAL_TO]": "2026-10-05T08:00:00.123Z",
            "filters[endDate][LESS_THAN_OR_EQUAL_TO]": "2026-10-05T08:03:00.000Z",
        })
        expect(queryHasTimeBound(query!)).toBe(true)
    })

    it.each([
        ["2026-10-09T11:00:00Z", "PT24H"],
        ["2026-10-08T06:00:00Z", "PT48H"],
        ["2026-10-05T12:00:00Z", "PT168H"],
        ["2026-08-01T12:00:00Z", "PT8760H"],
    ])("shouldPickTheSmallestRelativeRangeCoveringARunningParentStartedAt %s", (startDate, timeRange) => {
        expect(queryFor({startDate})).toMatchObject({"filters[timeRange][EQUALS]": timeRange})
    })

    it("shouldFallBackToAnOpenEndedStartDateWhenARunningParentIsOlderThanTheLargestRange", () => {
        expect(queryFor({startDate: "2025-01-01T00:00:00Z"})).toMatchObject({
            "filters[startDate][GREATER_THAN_OR_EQUAL_TO]": "2025-01-01T00:00:00.000Z",
        })
    })

    it("shouldAddNoTimeBoundWhenTheParentStartDateIsUnknown", () => {
        expect(queryHasTimeBound(queryFor(undefined)!)).toBe(false)
    })
})

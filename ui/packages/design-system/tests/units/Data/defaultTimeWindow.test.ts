import {describe, test, expect} from "vitest"
import {applyDefaultFilters} from "../../../src/components/Data/KsDataTable/filter/composables/useDefaultFilter"

// The date fields a resource owns come from its `time-range` chips, so the caller passes them in
// rather than the composable carrying a hardcoded list of every date field in the product.
const EXECUTION_DATES = ["startDate", "endDate"]
const LOG_DATES = ["date"]
const CLUSTER_SERVICE_DATES = ["created"]

const withWindow = (timeRangeFields: string[]) => ({
    namespace: null,
    includeTimeRange: true,
    timeRangeFields,
})

describe("applyDefaultFilters default time window", () => {
    test("writes the default window on the resource's first date field", () => {
        const {query, change} = applyDefaultFilters({}, withWindow(EXECUTION_DATES))

        expect(change).toBe(true)
        expect(query["filters[startDate][GREATER_THAN_OR_EQUAL_TO]"]).toBe("PT24H")
    })

    test("uses the resource's own field rather than assuming startDate", () => {
        const {query} = applyDefaultFilters({}, withWindow(LOG_DATES))

        expect(query["filters[date][GREATER_THAN_OR_EQUAL_TO]"]).toBe("PT24H")
        expect(query["filters[startDate][GREATER_THAN_OR_EQUAL_TO]"]).toBeUndefined()
    })

    test("leaves an existing bound on that field alone", () => {
        const existing = {"filters[date][LESS_THAN_OR_EQUAL_TO]": "2026-07-01T00:00:00.000Z"}

        const {query} = applyDefaultFilters(existing, withWindow(LOG_DATES))

        expect(query).toEqual(existing)
        expect(query["filters[date][GREATER_THAN_OR_EQUAL_TO]"]).toBeUndefined()
    })

    test("counts any of the resource's date fields as an existing window", () => {
        // Executions expose both bounds; an endDate-only query is still a window, so nothing is added.
        const existing = {"filters[endDate][LESS_THAN_OR_EQUAL_TO]": "2026-07-01T00:00:00.000Z"}

        const {query} = applyDefaultFilters(existing, withWindow(EXECUTION_DATES))

        expect(query["filters[startDate][GREATER_THAN_OR_EQUAL_TO]"]).toBeUndefined()
    })

    test("ignores a date field belonging to a different resource", () => {
        // A stale startDate carried over from an executions URL must not suppress the log window.
        const {query} = applyDefaultFilters(
            {"filters[startDate][GREATER_THAN_OR_EQUAL_TO]": "PT24H"},
            withWindow(LOG_DATES),
        )

        expect(query["filters[date][GREATER_THAN_OR_EQUAL_TO]"]).toBe("PT24H")
    })

    test("respects a date field that is neither startDate nor date", () => {
        const existing = {"filters[created][GREATER_THAN_OR_EQUAL_TO]": "P7D"}

        const {query} = applyDefaultFilters(existing, withWindow(CLUSTER_SERVICE_DATES))

        expect(query).toEqual(existing)
    })

    test("honours the configured operation and duration", () => {
        const {query} = applyDefaultFilters({}, {
            namespace: null,
            includeTimeRange: true,
            timeRangeFields: ["nextExecutionDate"],
            timeRangeOperation: "LESS_THAN_OR_EQUAL_TO",
            defaultDuration: "P7D",
        })

        expect(query["filters[nextExecutionDate][LESS_THAN_OR_EQUAL_TO]"]).toBe("P7D")
    })

    test("strips legacy timeRange keys left in stale URLs", () => {
        const {query, change} = applyDefaultFilters(
            {"filters[timeRange][EQUALS]": "PT24H"},
            withWindow(LOG_DATES),
        )

        expect(change).toBe(true)
        expect(query["filters[timeRange][EQUALS]"]).toBeUndefined()
        expect(query["filters[date][GREATER_THAN_OR_EQUAL_TO]"]).toBe("PT24H")
    })

    test("adds nothing when the resource has no date field", () => {
        const {query} = applyDefaultFilters({}, {namespace: null, includeTimeRange: false})

        expect(query).toEqual({})
    })
})

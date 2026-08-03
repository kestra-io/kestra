import {describe, expect, it} from "vitest"
import {keepSupportedFilters, FILTER_FIELD_PATTERN} from "../../../../src/components/executions/utils"

const SUPPORTED = new Set([
    "namespace", "flowId", "kind", "state", "scope", "childFilter",
    "startDate", "endDate", "labels", "triggerExecutionId", "parentId", "q",
])

describe("keepSupportedFilters", () => {
    it("drops the Gantt view's level/task filters that leak via the shared route query", () => {
        // Given the regression URL: a Gantt route query carrying a log-level filter.
        const query = {
            "filters[level][GREATER_THAN_OR_EQUAL_TO]": "INFO",
            "filters[task][EQUALS]": "hold",
            "filters[namespace][PREFIX]": "company.team.qa",
            "filters[flowId][EQUALS]": "qa_flow_concurrency",
        }

        // When sanitizing for the executions search, level/task are removed.
        expect(keepSupportedFilters(query, SUPPORTED)).toEqual({
            "filters[namespace][PREFIX]": "company.team.qa",
            "filters[flowId][EQUALS]": "qa_flow_concurrency",
        })
    })

    it("keeps timeRange's startDate/endDate encoding and full-text q", () => {
        const query = {
            "filters[startDate][GREATER_THAN_OR_EQUAL_TO]": "2025-01-01T00:00:00.000Z",
            "filters[endDate][LESS_THAN_OR_EQUAL_TO]": "2025-01-02T00:00:00.000Z",
            "filters[q][EQUALS]": "search-term",
            "filters[state][IN]": "FAILED",
        }

        expect(keepSupportedFilters(query, SUPPORTED)).toEqual(query)
    })

    it("keeps key-value label filters whose field is supported", () => {
        const query = {"filters[labels][EQUALS][env]": "prod"}

        expect(keepSupportedFilters(query, SUPPORTED)).toEqual(query)
    })

    it("evaluates the field inside nested and/or groups, not the group keyword", () => {
        const query = {
            "filters[and][0][state][IN]": "RUNNING",
            "filters[or][1][level][EQUALS]": "INFO", // unsupported field inside a group -> dropped
        }

        expect(keepSupportedFilters(query, SUPPORTED)).toEqual({
            "filters[and][0][state][IN]": "RUNNING",
        })
    })

    it("passes through non-filter params (paging, sort, dateFilter meta)", () => {
        const query = {
            page: "1",
            size: "25",
            sort: "state.startDate:desc",
            dateFilter: "START_DATE",
            "filters[level][GREATER_THAN_OR_EQUAL_TO]": "INFO",
        }

        expect(keepSupportedFilters(query, SUPPORTED)).toEqual({
            page: "1",
            size: "25",
            sort: "state.startDate:desc",
            dateFilter: "START_DATE",
        })
    })

    it("drops everything when no fields are supported, but keeps non-filter params", () => {
        const query = {
            "filters[state][IN]": "FAILED",
            page: "1",
        }

        expect(keepSupportedFilters(query, new Set())).toEqual({page: "1"})
    })

    it("returns an empty object for an empty query", () => {
        expect(keepSupportedFilters({}, SUPPORTED)).toEqual({})
    })
})

describe("FILTER_FIELD_PATTERN", () => {
    it("captures the field for a plain filter key", () => {
        expect("filters[namespace][IN]".match(FILTER_FIELD_PATTERN)?.[1]).toBe("namespace")
    })

    it("captures the field after an and/or group chain", () => {
        expect("filters[and][0][state][IN]".match(FILTER_FIELD_PATTERN)?.[1]).toBe("state")
    })

    it("does not match non-filter keys", () => {
        expect("page".match(FILTER_FIELD_PATTERN)).toBeNull()
        expect("dateFilter".match(FILTER_FIELD_PATTERN)).toBeNull()
    })
})

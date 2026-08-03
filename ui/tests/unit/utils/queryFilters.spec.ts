import {describe, expect, it} from "vitest"
import {routeQueryToQueryFilters} from "../../../src/utils/queryFilters"

describe("routeQueryToQueryFilters", () => {
    it("builds a simple leaf filter", () => {
        expect(routeQueryToQueryFilters({"filters[namespace][EQUALS]": "io.kestra"})).toEqual([
            {field: "namespace", operation: "EQUALS", value: "io.kestra"},
        ])
    })

    it("keeps the irregular q/groupList field values as-is (already the wire form)", () => {
        expect(routeQueryToQueryFilters({"filters[q][EQUALS]": "hello"})).toEqual([
            {field: "q", operation: "EQUALS", value: "hello"},
        ])
        expect(routeQueryToQueryFilters({"filters[groupList][EQUALS]": "team"})).toEqual([
            {field: "groupList", operation: "EQUALS", value: "team"},
        ])
    })

    it("keeps a comma-joined IN/NOT_IN value as a plain string", () => {
        expect(routeQueryToQueryFilters({"filters[namespace][IN]": "a,b,c"})).toEqual([
            {field: "namespace", operation: "IN", value: "a,b,c"},
        ])
    })

    it("merges multiple label sub-keys sharing an operation into one filter", () => {
        expect(routeQueryToQueryFilters({
            "filters[labels][EQUALS][env]": "prod",
            "filters[labels][EQUALS][team]": "backend",
        })).toEqual([
            {field: "labels", operation: "EQUALS", value: {env: "prod", team: "backend"}},
        ])
    })

    it("builds a top-level OR group", () => {
        expect(routeQueryToQueryFilters({
            "filters[or][0][state][EQUALS]": "RUNNING",
            "filters[or][1][state][EQUALS]": "FAILED",
        })).toEqual([
            {
                logical: "or",
                children: [
                    {field: "state", operation: "EQUALS", value: "RUNNING"},
                    {field: "state", operation: "EQUALS", value: "FAILED"},
                ],
            },
        ])
    })

    it("flattens a single-branch AND group back to a plain leaf", () => {
        expect(routeQueryToQueryFilters({"filters[and][0][state][EQUALS]": "RUNNING"})).toEqual([
            {field: "state", operation: "EQUALS", value: "RUNNING"},
        ])
    })

    it("builds a nested wrapper group (OR containing an AND)", () => {
        expect(routeQueryToQueryFilters({
            "filters[or][0][and][0][namespace][EQUALS]": "io.kestra",
            "filters[or][0][and][1][state][EQUALS]": "RUNNING",
            "filters[or][1][state][EQUALS]": "FAILED",
        })).toEqual([
            {
                logical: "or",
                children: [
                    {
                        logical: "and",
                        children: [
                            {field: "namespace", operation: "EQUALS", value: "io.kestra"},
                            {field: "state", operation: "EQUALS", value: "RUNNING"},
                        ],
                    },
                    {field: "state", operation: "EQUALS", value: "FAILED"},
                ],
            },
        ])
    })

    it("builds the timeRange GTE/LTE pair as two independent leaves", () => {
        expect(routeQueryToQueryFilters({
            "filters[startDate][GREATER_THAN_OR_EQUAL_TO]": "2023-01-01T00:00:00.000Z",
            "filters[endDate][LESS_THAN_OR_EQUAL_TO]": "2023-01-31T23:59:59.000Z",
        })).toEqual([
            {field: "startDate", operation: "GREATER_THAN_OR_EQUAL_TO", value: "2023-01-01T00:00:00.000Z"},
            {field: "endDate", operation: "LESS_THAN_OR_EQUAL_TO", value: "2023-01-31T23:59:59.000Z"},
        ])
    })

    it("ignores non-filters keys", () => {
        expect(routeQueryToQueryFilters({page: "1", size: "25", sort: "id:asc"})).toEqual([])
    })
})

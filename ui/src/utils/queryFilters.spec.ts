import {describe, expect, it} from "vitest"
import {keepTopLevelFilters} from "./queryFilters"

describe("keepTopLevelFilters", () => {
    it("should keep the allowlisted filters", () => {
        const query = {
            "filters[namespace][PREFIX]": "company.team",
            "filters[scope][IN]": "USER",
        }

        expect(keepTopLevelFilters(query, ["namespace", "scope"])).toEqual(query)
    })

    it("should drop the filters that are not allowlisted", () => {
        const query = {
            "filters[namespace][PREFIX]": "company.team",
            "filters[state][IN]": ["RUNNING", "FAILED"],
            "filters[kind][EQUALS]": "NORMAL",
            "filters[flowId][EQUALS]": "my-flow",
        }

        expect(keepTopLevelFilters(query, ["namespace"])).toEqual({
            "filters[namespace][PREFIX]": "company.team",
        })
    })

    it("should drop pagination, sort and other bare params", () => {
        const query = {
            "filters[namespace][PREFIX]": "company.team",
            page: "2",
            size: "50",
            sort: "state.startDate:desc",
            dateFilter: "START_DATE",
            q: "some text",
        }

        expect(keepTopLevelFilters(query, ["namespace"])).toEqual({
            "filters[namespace][PREFIX]": "company.team",
        })
    })

    it("should drop the date filters a custom time range encodes to", () => {
        const query = {
            "filters[namespace][PREFIX]": "company.team",
            "filters[startDate][GREATER_THAN_OR_EQUAL_TO]": "2026-07-01T00:00:00Z",
            "filters[endDate][LESS_THAN_OR_EQUAL_TO]": "2026-07-30T00:00:00Z",
            "filters[timeRange][EQUALS]": "PT12H",
        }

        expect(keepTopLevelFilters(query, ["namespace"])).toEqual({
            "filters[namespace][PREFIX]": "company.team",
        })
    })

    it("should drop a labels filter including its sub-key", () => {
        const query = {
            "filters[namespace][PREFIX]": "company.team",
            "filters[labels][EQUALS][mykey]": "myvalue",
        }

        expect(keepTopLevelFilters(query, ["namespace"])).toEqual({
            "filters[namespace][PREFIX]": "company.team",
        })
    })

    it("should keep a labels filter with its sub-key when allowlisted", () => {
        const query = {"filters[labels][EQUALS][mykey]": "myvalue"}

        expect(keepTopLevelFilters(query, ["labels"])).toEqual(query)
    })

    it("should never forward a filter nested in a logical group, even when allowlisted", () => {
        // Dropping one disjunct of an OR would tighten the lookup and could hide values the list
        // shows, so grouped filters are skipped entirely.
        const query = {
            "filters[or][0][namespace][EQUALS]": "company.team",
            "filters[or][1][namespace][EQUALS]": "company.other",
        }

        expect(keepTopLevelFilters(query, ["namespace"])).toEqual({})
    })

    it("should keep multi-value filters as arrays", () => {
        const query = {"filters[namespace][IN]": ["company.team", "company.other"]}

        expect(keepTopLevelFilters(query, ["namespace"])).toEqual(query)
    })

    it("should drop empty, null and unparsable filter params", () => {
        const query = {
            "filters[namespace][PREFIX]": "",
            "filters[namespace][IN]": null,
            "filters[namespace][PREFIX2": "broken",
            "filters[scope][IN]": "USER",
        }

        expect(keepTopLevelFilters(query, ["namespace", "scope"])).toEqual({
            "filters[scope][IN]": "USER",
        })
    })
})

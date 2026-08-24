import {describe, expect, it} from "vitest"

import {onlyBracketFilters} from "./utils"

describe("onlyBracketFilters", () => {
    it("keeps bracket-format filters, including nested groups", () => {
        expect(
            onlyBracketFilters({
                "filters[state][EQUALS]": "RUNNING",
                "filters[or][0][namespace][EQUALS]": "a",
                "filters[labels][EQUALS][foo]": "bar",
            }),
        ).toEqual({
            "filters[state][EQUALS]": "RUNNING",
            "filters[or][0][namespace][EQUALS]": "a",
            "filters[labels][EQUALS][foo]": "bar",
        })
    })

    it("drops flat legacy params the API now rejects", () => {
        // A bookmarked pre-2.0 URL, or a flat programmatic navigation, must not reach an endpoint that takes
        // nothing but filters - it answers 422 (kestra-io/kestra-ee#10326).
        expect(
            onlyBracketFilters({
                state: "RUNNING",
                namespace: "io.kestra.tests",
                "filters[state][EQUALS]": "RUNNING",
            }),
        ).toEqual({"filters[state][EQUALS]": "RUNNING"})
    })

    it("drops pagination keys, which the export endpoint does not take", () => {
        expect(onlyBracketFilters({page: 1, size: 25, sort: "state.startDate:desc"})).toEqual({})
    })
})

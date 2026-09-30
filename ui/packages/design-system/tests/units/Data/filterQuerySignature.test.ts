import {describe, test, expect} from "vitest"
import {filterQuerySignature, isFilterQueryKey} from "../../../src/components/Data/KsDataTable/filter/utils/helpers"
import {DATE_FILTER_KEY, SEARCH_QUERY_KEY} from "../../../src/components/Data/KsDataTable/filter/utils/constants"

describe("isFilterQueryKey", () => {
    test("accepts encoded filter keys and the dateFilter selector", () => {
        expect(isFilterQueryKey("filters[namespace][EQUALS]")).toBe(true)
        expect(isFilterQueryKey("filters[and][0][or][1][flowId][EQUALS]")).toBe(true)
        expect(isFilterQueryKey(SEARCH_QUERY_KEY)).toBe(true)
        expect(isFilterQueryKey(DATE_FILTER_KEY)).toBe(true)
    })

    test("rejects the pagination and sort keys", () => {
        expect(isFilterQueryKey("page")).toBe(false)
        expect(isFilterQueryKey("size")).toBe(false)
        expect(isFilterQueryKey("sort")).toBe(false)
    })
})

describe("filterQuerySignature", () => {
    const filters = {
        "filters[namespace][EQUALS]": "company.team",
        [DATE_FILTER_KEY]: "PT12H",
    }

    test("is unchanged when only page, size or sort move", () => {
        expect(filterQuerySignature({...filters, page: "2", size: "25", sort: "id:asc"}))
            .toBe(filterQuerySignature({...filters, page: "3", size: "50", sort: "id:desc"}))
    })

    test("changes when a filter value changes", () => {
        expect(filterQuerySignature({...filters, "filters[namespace][EQUALS]": "company.other"}))
            .not.toBe(filterQuerySignature(filters))
    })

    test("changes when a filter is added or removed", () => {
        expect(filterQuerySignature({...filters, "filters[flowId][CONTAINS]": "hello"}))
            .not.toBe(filterQuerySignature(filters))
        expect(filterQuerySignature({[DATE_FILTER_KEY]: "PT12H"})).not.toBe(filterQuerySignature(filters))
    })

    test("ignores the order the keys appear in", () => {
        expect(filterQuerySignature({"filters[a][EQUALS]": "1", "filters[b][EQUALS]": "2"}))
            .toBe(filterQuerySignature({"filters[b][EQUALS]": "2", "filters[a][EQUALS]": "1"}))
    })

    test("tracks the search query, which is encoded as a filter key", () => {
        expect(filterQuerySignature({[SEARCH_QUERY_KEY]: "first"}))
            .not.toBe(filterQuerySignature({[SEARCH_QUERY_KEY]: "second"}))
    })

    test("distinguishes a repeated key from a single value", () => {
        expect(filterQuerySignature({"filters[state][IN]": ["RUNNING", "FAILED"]}))
            .not.toBe(filterQuerySignature({"filters[state][IN]": "RUNNING"}))
    })
})

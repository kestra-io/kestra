import {describe, expect, it} from "vitest"
import {
    decodeSearchParams,
    encodeFiltersToQuery,
    encodeFilterGroupsToQuery,
    isValidFilter,
    getUniqueFilters,
    keyOfComparator,
    Comparators,
    clearFilterQueryParams,
    isSearchPath,
    isUnrenderableFilterKey,
    findUnrenderableFilterKeys,
    serializeFiltersToString,
    parseFiltersFromString,
} from "@kestra-io/design-system"
import type {FilterGroup, LeafFilterGroup, WrapperGroup} from "@kestra-io/design-system"

const leaf = (id: string, filters: any[]): LeafFilterGroup => ({id, kind: "leaf", filters})
const wrapper = (id: string, logical: "AND" | "OR", children: LeafFilterGroup[]): WrapperGroup =>
    ({id, kind: "wrapper", logical, children})

describe("Filter Helpers", () => {
    describe("decodeSearchParams", () => {
        it("should decode standard and label filters", () => {
            expect(decodeSearchParams({"filters[namespace][IN]": "test-namespace"})).toEqual([
                {field: "namespace", value: "test-namespace", operation: "IN"},
            ])

            expect(decodeSearchParams({"filters[labels][EQUALS][env]": "prod"})).toEqual([
                {field: "labels", value: "env:prod", operation: "EQUALS"},
            ])
        })

        it("should decode top-level OR group params", () => {
            expect(decodeSearchParams({"filters[or][0][state][EQUALS]": "RUNNING"})).toEqual([
                {field: "state", value: "RUNNING", operation: "EQUALS", groupIndex: 0, topLogical: "OR"},
            ])
        })

        it("should decode top-level AND group params", () => {
            expect(decodeSearchParams({"filters[and][1][state][EQUALS]": "FAILED"})).toEqual([
                {field: "state", value: "FAILED", operation: "EQUALS", groupIndex: 1, topLogical: "AND"},
            ])
        })

        it("should decode wrapper-nested params with both operators", () => {
            expect(decodeSearchParams({"filters[or][0][and][1][namespace][EQUALS]": "io.kestra"})).toEqual([
                {
                    field: "namespace",
                    value: "io.kestra",
                    operation: "EQUALS",
                    groupIndex: 0,
                    wrapperChildIndex: 1,
                    topLogical: "OR",
                    wrapperLogical: "AND",
                },
            ])
        })
    })

    describe("encodeFiltersToQuery", () => {
        it("should encode standard, timeRange and label filters", () => {
            const filters = [
                {key: "namespace", comparator: Comparators.IN, value: ["test-namespace"]},
                {key: "state", comparator: Comparators.IN, value: ["SUCCESS", "FAILED"]},
            ]
            expect(encodeFiltersToQuery(filters, keyOfComparator)).toEqual({
                "filters[namespace][IN]": "test-namespace",
                "filters[state][IN]": "SUCCESS,FAILED",
            })

            const startDate = new Date("2023-01-01T00:00:00Z")
            const endDate = new Date("2023-01-31T23:59:59Z")
            const timeRangeFilters = [{key: "timeRange", comparator: Comparators.GREATER_THAN_OR_EQUAL_TO, value: {startDate, endDate}}]
            expect(encodeFiltersToQuery(timeRangeFilters, keyOfComparator)).toEqual({
                "filters[startDate][GREATER_THAN_OR_EQUAL_TO]": startDate.toISOString(),
                "filters[endDate][LESS_THAN_OR_EQUAL_TO]": endDate.toISOString(),
            })

            const labelFilters = [{key: "labels", comparator: Comparators.EQUALS, value: ["env:prod", "team:backend"]}]
            expect(encodeFiltersToQuery(labelFilters, keyOfComparator)).toEqual({
                "filters[labels][EQUALS][env]": "prod",
                "filters[labels][EQUALS][team]": "backend",
            })
        })

        it("should encode a custom-field time-range value as a GTE/LTE pair on its own key", () => {
            const startDate = new Date("2023-01-01T00:00:00Z")
            const endDate = new Date("2023-01-31T23:59:59Z")
            // A generic `time-range` field (e.g. service-instance "created") keeps its own key,
            // unlike the dedicated `timeRange` filter which maps to startDate/endDate.
            const filters = [{key: "created", comparator: Comparators.GREATER_THAN_OR_EQUAL_TO, value: {startDate, endDate}}]
            expect(encodeFiltersToQuery(filters, keyOfComparator)).toEqual({
                "filters[created][GREATER_THAN_OR_EQUAL_TO]": startDate.toISOString(),
                "filters[created][LESS_THAN_OR_EQUAL_TO]": endDate.toISOString(),
            })
        })
    })

    describe("encodeFilterGroupsToQuery", () => {
        it("emits the flat legacy format for a single leaf group", () => {
            const groups: FilterGroup[] = [
                leaf("g1", [{key: "namespace", comparator: Comparators.EQUALS, value: "io.kestra"}]),
            ]
            expect(encodeFilterGroupsToQuery(groups, keyOfComparator)).toEqual({
                "filters[namespace][EQUALS]": "io.kestra",
            })
        })

        it("emits filters[or][N] prefix for multiple top-level leaf groups", () => {
            const groups: FilterGroup[] = [
                leaf("g1", [{key: "state", comparator: Comparators.EQUALS, value: "RUNNING"}]),
                leaf("g2", [{key: "state", comparator: Comparators.EQUALS, value: "FAILED"}]),
            ]
            expect(encodeFilterGroupsToQuery(groups, keyOfComparator)).toEqual({
                "filters[or][0][state][EQUALS]": "RUNNING",
                "filters[or][1][state][EQUALS]": "FAILED",
            })
        })

        it("emits filters[and][N] prefix when the top operator is AND", () => {
            const groups: FilterGroup[] = [
                leaf("g1", [{key: "state", comparator: Comparators.EQUALS, value: "RUNNING"}]),
                leaf("g2", [{key: "namespace", comparator: Comparators.EQUALS, value: "io.kestra"}]),
            ]
            expect(encodeFilterGroupsToQuery(groups, keyOfComparator, "AND")).toEqual({
                "filters[and][0][state][EQUALS]": "RUNNING",
                "filters[and][1][namespace][EQUALS]": "io.kestra",
            })
        })

        it("emits filters[or][N][and][M] for a wrapper inside an OR group", () => {
            const groups: FilterGroup[] = [
                wrapper("w1", "AND", [
                    leaf("c1", [{key: "namespace", comparator: Comparators.EQUALS, value: "io.kestra"}]),
                    leaf("c2", [{key: "state", comparator: Comparators.EQUALS, value: "RUNNING"}]),
                ]),
                leaf("g2", [{key: "state", comparator: Comparators.EQUALS, value: "FAILED"}]),
            ]
            expect(encodeFilterGroupsToQuery(groups, keyOfComparator)).toEqual({
                "filters[or][0][and][0][namespace][EQUALS]": "io.kestra",
                "filters[or][0][and][1][state][EQUALS]": "RUNNING",
                "filters[or][1][state][EQUALS]": "FAILED",
            })
        })

        it("emits the wrapper's own operator (OR-inside-OR)", () => {
            const groups: FilterGroup[] = [
                wrapper("w1", "OR", [
                    leaf("c1", [{key: "state", comparator: Comparators.EQUALS, value: "A"}]),
                    leaf("c2", [{key: "state", comparator: Comparators.EQUALS, value: "B"}]),
                ]),
            ]
            // Single top-level unit, but it's a wrapper — so we get the [or][0][or][M] shape.
            expect(encodeFilterGroupsToQuery(groups, keyOfComparator)).toEqual({
                "filters[or][0][or][0][state][EQUALS]": "A",
                "filters[or][0][or][1][state][EQUALS]": "B",
            })
        })
    })

    describe("isUnrenderableFilterKey", () => {
        it("returns false for 0/1/2 nesting pairs", () => {
            expect(isUnrenderableFilterKey("filters[namespace][EQUALS]")).toBe(false)
            expect(isUnrenderableFilterKey("filters[or][0][namespace][EQUALS]")).toBe(false)
            expect(isUnrenderableFilterKey("filters[or][0][and][0][namespace][EQUALS]")).toBe(false)
        })

        it("returns true once nesting reaches 3 pairs", () => {
            expect(isUnrenderableFilterKey("filters[or][0][and][0][or][0][namespace][EQUALS]")).toBe(true)
            expect(isUnrenderableFilterKey("filters[and][0][or][1][and][2][or][3][state][EQUALS]")).toBe(true)
        })

        it("returns false for non-filters keys", () => {
            expect(isUnrenderableFilterKey("page")).toBe(false)
            expect(isUnrenderableFilterKey("sort[asc]")).toBe(false)
        })
    })

    describe("findUnrenderableFilterKeys", () => {
        it("returns only the keys that exceed the nesting cap", () => {
            const query = {
                "filters[namespace][EQUALS]": "io.kestra",
                "filters[or][0][state][EQUALS]": "RUNNING",
                "filters[or][0][and][0][or][0][flowId][EQUALS]": "x",
                page: "1",
            }
            expect(findUnrenderableFilterKeys(query)).toEqual([
                "filters[or][0][and][0][or][0][flowId][EQUALS]",
            ])
        })
    })

    describe("serializeFiltersToString / parseFiltersFromString", () => {
        it("round-trips flat filters losslessly", () => {
            const query = {
                "filters[namespace][EQUALS]": "io.kestra",
                "filters[state][IN]": "RUNNING,FAILED",
            }
            const serialised = serializeFiltersToString(query)
            expect(serialised).toContain("filters[namespace][EQUALS]=io.kestra")
            expect(serialised).toContain("filters[state][IN]=RUNNING,FAILED")
            expect(parseFiltersFromString(serialised)).toEqual(query)
        })

        it("round-trips nested OR/AND prefixed filters", () => {
            const query = {
                "filters[or][0][and][0][namespace][EQUALS]": "io.kestra",
                "filters[or][0][and][1][state][EQUALS]": "RUNNING",
                "filters[or][1][state][EQUALS]": "FAILED",
            }
            expect(parseFiltersFromString(serializeFiltersToString(query))).toEqual(query)
        })

        it("preserves duplicate URL keys as arrays so router emits both values", () => {
            const input = [
                "filters[or][1][namespace][NOT_IN]=system",
                "filters[or][1][namespace][NOT_IN]=systema",
            ].join("\n")
            expect(parseFiltersFromString(input)).toEqual({
                "filters[or][1][namespace][NOT_IN]": ["system", "systema"],
            })
        })

        it("accepts percent-encoded keys and values for paste-from-URL ergonomics", () => {
            const input = "filters%5Bnamespace%5D%5BEQUALS%5D=io.kestra"
            expect(parseFiltersFromString(input)).toEqual({
                "filters[namespace][EQUALS]": "io.kestra",
            })
        })

        it("silently ignores malformed percent-encoding (falls back to raw)", () => {
            const input = "filters[namespace][EQUALS]=50%"
            expect(parseFiltersFromString(input)).toEqual({
                "filters[namespace][EQUALS]": "50%",
            })
        })

        it("ignores non-filters keys and empty lines", () => {
            const input = [
                "",
                "page=1",
                "filters[state][EQUALS]=RUNNING",
                "",
                "sort=asc",
            ].join("\n")
            expect(parseFiltersFromString(input)).toEqual({
                "filters[state][EQUALS]": "RUNNING",
            })
        })

        it("splits on & only when followed by filters[ or %", () => {
            const input = "filters[a][EQUALS]=foo&bar&filters[b][EQUALS]=baz"
            // The `&bar` is preserved as part of the value (an `&` inside a value is valid);
            // only `&filters[...` is treated as a separator between entries.
            const out = parseFiltersFromString(input)
            expect(out["filters[a][EQUALS]"]).toBe("foo&bar")
            expect(out["filters[b][EQUALS]"]).toBe("baz")
        })
    })

    describe("isValidFilter", () => {
        it("should validate filters correctly", () => {
            expect(isValidFilter({key: "namespace", comparator: Comparators.IN, value: ["test"]})).toBe(true)
            expect(isValidFilter({key: "namespace", comparator: Comparators.IN, value: []})).toBe(false)
            expect(isValidFilter({key: "state", comparator: Comparators.IN, value: ["SUCCESS"]})).toBe(true)
            expect(isValidFilter({key: "state", comparator: Comparators.IN, value: []})).toBe(false)

            const startDate = new Date("2023-01-01")
            const endDate = new Date("2023-01-31")
            expect(isValidFilter({key: "timeRange", comparator: Comparators.GREATER_THAN_OR_EQUAL_TO, value: {startDate, endDate}})).toBe(true)
            expect(isValidFilter({key: "timeRange", comparator: Comparators.GREATER_THAN_OR_EQUAL_TO, value: {startDate: null as any, endDate}})).toBe(false)
        })
    })

    describe("getUniqueFilters", () => {
        it("dedupes by key alone when comparator is absent (back-compat)", () => {
            const filters = [
                {key: "namespace", value: "test1"},
                {key: "namespace", value: "test2"},
            ]
            expect(getUniqueFilters(filters)).toEqual([{key: "namespace", value: "test2"}])
        })

        it("keeps same-key/different-comparator pairs", () => {
            // startDate > X AND startDate < Y is a legitimate combo, both should survive
            const filters = [
                {key: "startDate", comparator: Comparators.GREATER_THAN, value: "2023-01-01"},
                {key: "startDate", comparator: Comparators.LESS_THAN, value: "2023-12-31"},
            ]
            expect(getUniqueFilters(filters)).toEqual(filters)
        })

        it("dedupes same key + same comparator, keeping the last", () => {
            const filters = [
                {key: "namespace", comparator: Comparators.EQUALS, value: "test1"},
                {key: "namespace", comparator: Comparators.EQUALS, value: "test2"},
            ]
            expect(getUniqueFilters(filters)).toEqual([
                {key: "namespace", comparator: Comparators.EQUALS, value: "test2"},
            ])
        })
    })

    describe("clearFilterQueryParams", () => {
        it("should remove only filter parameters", () => {
            const query = {
                "filters[namespace][IN]": "test",
                "other[param]": "value",
                q: "search",
            }
            clearFilterQueryParams(query)
            expect(query).toEqual({"other[param]": "value", q: "search"})
        })
    })

    describe("isSearchPath", () => {
        it("should identify search paths correctly", () => {
            expect(isSearchPath("flows/list")).toBe(true)
            expect(isSearchPath("executions/list")).toBe(true)
            expect(isSearchPath("/unknown")).toBe(false)
        })
    })
})

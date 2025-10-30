import {describe, expect, it} from "vitest";
import {
    decodeSearchParams,
    isSearchPath,
    encodeFiltersToQuery,
    isValidFilter,
    getUniqueFilters,
    clearFilterQueryParams,
    keyOfComparator
} from "../../../../src/components/filter/utils/helpers.ts";
import {Comparators} from "../../../../src/components/filter/utils/filterTypes.ts";

describe("Filter Helpers", () => {
    describe("decodeSearchParams", () => {
        it("should decode standard and label filters", () => {
            expect(decodeSearchParams({"filters[namespace][IN]": "test-namespace"})).toEqual([
                {field: "namespace", value: "test-namespace", operation: "IN"}
            ]);

            expect(decodeSearchParams({"filters[labels][EQUALS][env]": "prod"})).toEqual([
                {field: "labels", value: "env:prod", operation: "EQUALS"}
            ]);
        });
    });

    describe("encodeFiltersToQuery", () => {
        it("should encode standard, timeRange and label filters", () => {
            const filters = [
                {key: "namespace", comparator: Comparators.IN, value: ["test-namespace"]},
                {key: "state", comparator: Comparators.IN, value: ["SUCCESS", "FAILED"]}
            ];
            expect(encodeFiltersToQuery(filters, keyOfComparator)).toEqual({
                "filters[namespace][IN]": "test-namespace",
                "filters[state][IN]": "SUCCESS,FAILED"
            });

            const startDate = new Date("2023-01-01T00:00:00Z");
            const endDate = new Date("2023-01-31T23:59:59Z");
            const timeRangeFilters = [{key: "timeRange", comparator: Comparators.GREATER_THAN_OR_EQUAL_TO, value: {startDate, endDate}}];
            expect(encodeFiltersToQuery(timeRangeFilters, keyOfComparator)).toEqual({
                "filters[startDate][GREATER_THAN_OR_EQUAL_TO]": startDate.toISOString(),
                "filters[endDate][LESS_THAN_OR_EQUAL_TO]": endDate.toISOString()
            });

            const labelFilters = [{key: "labels", comparator: Comparators.EQUALS, value: ["env:prod", "team:backend"]}];
            expect(encodeFiltersToQuery(labelFilters, keyOfComparator)).toEqual({
                "filters[labels][EQUALS][env]": "prod",
                "filters[labels][EQUALS][team]": "backend"
            });
        });
    });

    describe("isValidFilter", () => {
        it("should validate filters correctly", () => {
            expect(isValidFilter({key: "namespace", comparator: Comparators.IN, value: ["test"]})).toBe(true);
            expect(isValidFilter({key: "namespace", comparator: Comparators.IN, value: []})).toBe(false);
            expect(isValidFilter({key: "state", comparator: Comparators.IN, value: ["SUCCESS"]})).toBe(true);
            expect(isValidFilter({key: "state", comparator: Comparators.IN, value: []})).toBe(false);

            const startDate = new Date("2023-01-01");
            const endDate = new Date("2023-01-31");
            expect(isValidFilter({key: "timeRange", comparator: Comparators.GREATER_THAN_OR_EQUAL_TO, value: {startDate, endDate}})).toBe(true);
            expect(isValidFilter({key: "timeRange", comparator: Comparators.GREATER_THAN_OR_EQUAL_TO, value: {startDate: null as any, endDate}})).toBe(false);
        });
    });

    describe("getUniqueFilters", () => {
        it("should keep last occurrence of duplicate keys", () => {
            const filters = [
                {key: "namespace", value: "test1"},
                {key: "namespace", value: "test2"}
            ];
            expect(getUniqueFilters(filters)).toEqual([{key: "namespace", value: "test2"}]);
        });
    });

    describe("clearFilterQueryParams", () => {
        it("should remove only filter parameters", () => {
            const query = {
                "filters[namespace][IN]": "test",
                "other[param]": "value",
                q: "search"
            };
            clearFilterQueryParams(query);
            expect(query).toEqual({"other[param]": "value", q: "search"});
        });
    });

    describe("isSearchPath", () => {
        it("should identify search paths correctly", () => {
            expect(isSearchPath("flows/list")).toBe(true);
            expect(isSearchPath("executions/list")).toBe(true);
            expect(isSearchPath("/unknown")).toBe(false);
        });
    });
});

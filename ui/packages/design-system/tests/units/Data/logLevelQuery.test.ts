import {describe, test, expect} from "vitest"
import {
    readRouteLevelFilter,
    hasUnsupportedRouteLevelComparator,
    readAppliedLevelFilter,
    normalizeRouteLevelFilter,
    levelToRequestParams,
} from "../../../src/components/Data/KsDataTable/filter/utils/logLevelQuery"
import {Comparators} from "../../../src/components/Data/KsDataTable/filter/utils/filterTypes"
import type {AppliedFilter} from "../../../src/components/Data/KsDataTable/filter/utils/filterTypes"

const applied = (filters: Array<{key: string; value: unknown; comparator?: unknown}>) =>
    filters as unknown as AppliedFilter[]

describe("logLevelQuery", () => {
    describe("readRouteLevelFilter", () => {
        test("reads the GREATER_THAN_OR_EQUAL_TO filter as a min direction", () => {
            expect(
                readRouteLevelFilter({"filters[level][GREATER_THAN_OR_EQUAL_TO]": "WARN"}),
            ).toEqual({value: "WARN", direction: "min"})
        })

        test("reads the LESS_THAN_OR_EQUAL_TO filter as a max direction", () => {
            expect(
                readRouteLevelFilter({"filters[level][LESS_THAN_OR_EQUAL_TO]": "INFO"}),
            ).toEqual({value: "INFO", direction: "max"})
        })

        test("prefers the LESS_THAN_OR_EQUAL_TO filter over GREATER_THAN_OR_EQUAL_TO", () => {
            expect(
                readRouteLevelFilter({
                    "filters[level][LESS_THAN_OR_EQUAL_TO]": "INFO",
                    "filters[level][GREATER_THAN_OR_EQUAL_TO]": "ERROR",
                }),
            ).toEqual({value: "INFO", direction: "max"})
        })

        test("reads the legacy EQUALS filter as a min direction", () => {
            expect(
                readRouteLevelFilter({"filters[level][EQUALS]": "WARN"}),
            ).toEqual({value: "WARN", direction: "min"})
        })

        test("falls back to the legacy level key as a min direction", () => {
            expect(readRouteLevelFilter({level: "ERROR"})).toEqual({value: "ERROR", direction: "min"})
        })

        test("returns undefined when absent or empty", () => {
            expect(readRouteLevelFilter({})).toBeUndefined()
            expect(readRouteLevelFilter({"filters[level][GREATER_THAN_OR_EQUAL_TO]": ""})).toBeUndefined()
        })
    })

    describe("hasUnsupportedRouteLevelComparator", () => {
        test("flags the legacy level key", () => {
            expect(hasUnsupportedRouteLevelComparator({level: "INFO"})).toBe(true)
        })

        test("flags an unsupported level comparator", () => {
            expect(hasUnsupportedRouteLevelComparator({"filters[level][IN]": "INFO"})).toBe(true)
        })

        test("accepts the GREATER_THAN_OR_EQUAL_TO comparator", () => {
            expect(
                hasUnsupportedRouteLevelComparator({"filters[level][GREATER_THAN_OR_EQUAL_TO]": "INFO"}),
            ).toBe(false)
        })

        test("accepts the LESS_THAN_OR_EQUAL_TO comparator", () => {
            expect(
                hasUnsupportedRouteLevelComparator({"filters[level][LESS_THAN_OR_EQUAL_TO]": "INFO"}),
            ).toBe(false)
        })

        test("accepts the legacy EQUALS comparator", () => {
            expect(
                hasUnsupportedRouteLevelComparator({"filters[level][EQUALS]": "INFO"}),
            ).toBe(false)
        })

        test("accepts a query with no level filter", () => {
            expect(
                hasUnsupportedRouteLevelComparator({"filters[namespace][EQUALS]": "demo"}),
            ).toBe(false)
        })
    })

    describe("readAppliedLevelFilter", () => {
        test("reads a scalar level value as a min direction by default", () => {
            expect(
                readAppliedLevelFilter(applied([{key: "level", value: "WARN"}])),
            ).toEqual({value: "WARN", direction: "min"})
        })

        test("reads a LESS_THAN_OR_EQUAL_TO comparator as a max direction", () => {
            expect(
                readAppliedLevelFilter(
                    applied([{key: "level", value: "INFO", comparator: Comparators.LESS_THAN_OR_EQUAL_TO}]),
                ),
            ).toEqual({value: "INFO", direction: "max"})
        })

        test("reads the first value of an array", () => {
            expect(
                readAppliedLevelFilter(applied([{key: "level", value: ["ERROR", "WARN"]}])),
            ).toEqual({value: "ERROR", direction: "min"})
        })

        test("returns undefined when no level filter is present", () => {
            expect(
                readAppliedLevelFilter(applied([{key: "namespace", value: "demo"}])),
            ).toBeUndefined()
        })

        test("returns undefined for an empty value", () => {
            expect(readAppliedLevelFilter(applied([{key: "level", value: ""}]))).toBeUndefined()
        })
    })

    describe("normalizeRouteLevelFilter", () => {
        test("sets a GREATER_THAN_OR_EQUAL_TO filter for a plain string level (min default)", () => {
            expect(normalizeRouteLevelFilter({}, "WARN")).toEqual({
                "filters[level][GREATER_THAN_OR_EQUAL_TO]": "WARN",
            })
        })

        test("sets a LESS_THAN_OR_EQUAL_TO filter for a max-direction level", () => {
            expect(normalizeRouteLevelFilter({}, {value: "INFO", direction: "max"})).toEqual({
                "filters[level][LESS_THAN_OR_EQUAL_TO]": "INFO",
            })
        })

        test("replaces any existing level filters with the resolved one", () => {
            expect(
                normalizeRouteLevelFilter(
                    {
                        "filters[level][IN]": "INFO",
                        "filters[level][EQUALS]": "DEBUG",
                        "filters[level][LESS_THAN_OR_EQUAL_TO]": "TRACE",
                    },
                    "ERROR",
                ),
            ).toEqual({"filters[level][GREATER_THAN_OR_EQUAL_TO]": "ERROR"})
        })

        test("removes the legacy level key", () => {
            expect(normalizeRouteLevelFilter({level: "INFO"}, "WARN")).toEqual({
                "filters[level][GREATER_THAN_OR_EQUAL_TO]": "WARN",
            })
        })

        test("drops the level filter entirely when level is undefined", () => {
            expect(
                normalizeRouteLevelFilter({"filters[level][GREATER_THAN_OR_EQUAL_TO]": "INFO"}, undefined),
            ).toEqual({})
        })

        test("preserves unrelated filters", () => {
            expect(
                normalizeRouteLevelFilter(
                    {
                        "filters[namespace][EQUALS]": "demo",
                        "filters[level][GREATER_THAN_OR_EQUAL_TO]": "INFO",
                    },
                    "ERROR",
                ),
            ).toEqual({
                "filters[namespace][EQUALS]": "demo",
                "filters[level][GREATER_THAN_OR_EQUAL_TO]": "ERROR",
            })
        })
    })

    describe("levelToRequestParams", () => {
        test("maps a min direction to GREATER_THAN_OR_EQUAL_TO", () => {
            expect(levelToRequestParams({value: "WARN", direction: "min"})).toEqual({
                "filters[level][GREATER_THAN_OR_EQUAL_TO]": "WARN",
            })
        })

        test("maps a max direction to LESS_THAN_OR_EQUAL_TO", () => {
            expect(levelToRequestParams({value: "INFO", direction: "max"})).toEqual({
                "filters[level][LESS_THAN_OR_EQUAL_TO]": "INFO",
            })
        })

        test("returns an empty object when level is undefined", () => {
            expect(levelToRequestParams(undefined)).toEqual({})
        })
    })
})

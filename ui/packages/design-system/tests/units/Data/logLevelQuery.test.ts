import {describe, test, expect} from "vitest"
import {
    readRouteLevelFilter,
    hasUnsupportedRouteLevelComparator,
    readAppliedLevelFilter,
    normalizeRouteLevelFilter,
} from "../../../src/components/Data/KsDataTable/filter/utils/logLevelQuery"
import type {AppliedFilter} from "../../../src/components/Data/KsDataTable/filter/utils/filterTypes"

const applied = (filters: Array<{key: string; value: unknown}>) =>
    filters as unknown as AppliedFilter[]

describe("logLevelQuery", () => {
    describe("readRouteLevelFilter", () => {
        test("reads the EQUALS level filter", () => {
            expect(readRouteLevelFilter({"filters[level][EQUALS]": "WARN"})).toBe("WARN")
        })

        test("falls back to the legacy level key", () => {
            expect(readRouteLevelFilter({level: "ERROR"})).toBe("ERROR")
        })

        test("prefers the EQUALS filter over the legacy key", () => {
            expect(
                readRouteLevelFilter({"filters[level][EQUALS]": "INFO", level: "ERROR"}),
            ).toBe("INFO")
        })

        test("returns undefined when absent or empty", () => {
            expect(readRouteLevelFilter({})).toBeUndefined()
            expect(readRouteLevelFilter({"filters[level][EQUALS]": ""})).toBeUndefined()
        })
    })

    describe("hasUnsupportedRouteLevelComparator", () => {
        test("flags the legacy level key", () => {
            expect(hasUnsupportedRouteLevelComparator({level: "INFO"})).toBe(true)
        })

        test("flags a non-EQUALS level comparator", () => {
            expect(hasUnsupportedRouteLevelComparator({"filters[level][IN]": "INFO"})).toBe(true)
        })

        test("accepts the EQUALS level comparator", () => {
            expect(hasUnsupportedRouteLevelComparator({"filters[level][EQUALS]": "INFO"})).toBe(false)
        })

        test("accepts a query with no level filter", () => {
            expect(
                hasUnsupportedRouteLevelComparator({"filters[namespace][EQUALS]": "demo"}),
            ).toBe(false)
        })
    })

    describe("readAppliedLevelFilter", () => {
        test("reads a scalar level value", () => {
            expect(readAppliedLevelFilter(applied([{key: "level", value: "WARN"}]))).toBe("WARN")
        })

        test("reads the first value of an array", () => {
            expect(
                readAppliedLevelFilter(applied([{key: "level", value: ["ERROR", "WARN"]}])),
            ).toBe("ERROR")
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
        test("sets a single EQUALS filter for the given level", () => {
            expect(normalizeRouteLevelFilter({}, "WARN")).toEqual({
                "filters[level][EQUALS]": "WARN",
            })
        })

        test("replaces any existing level filters with a single EQUALS", () => {
            expect(
                normalizeRouteLevelFilter(
                    {"filters[level][IN]": "INFO", "filters[level][EQUALS]": "DEBUG"},
                    "ERROR",
                ),
            ).toEqual({"filters[level][EQUALS]": "ERROR"})
        })

        test("removes the legacy level key", () => {
            expect(normalizeRouteLevelFilter({level: "INFO"}, "WARN")).toEqual({
                "filters[level][EQUALS]": "WARN",
            })
        })

        test("drops the level filter entirely when level is undefined", () => {
            expect(
                normalizeRouteLevelFilter({"filters[level][EQUALS]": "INFO"}, undefined),
            ).toEqual({})
        })

        test("preserves unrelated filters", () => {
            expect(
                normalizeRouteLevelFilter(
                    {"filters[namespace][EQUALS]": "demo", "filters[level][EQUALS]": "INFO"},
                    "ERROR",
                ),
            ).toEqual({
                "filters[namespace][EQUALS]": "demo",
                "filters[level][EQUALS]": "ERROR",
            })
        })
    })
})

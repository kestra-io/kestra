import {describe, expect, test} from "vitest"
import {hasUserLogFilters, isDefaultLogLevel} from "./logsEmptyState"

const LEVEL_KEY = "filters[level][GREATER_THAN_OR_EQUAL_TO]"
const TIME_RANGE_KEY = "filters[timeRange][EQUALS]"
const NAMESPACE_KEY = "filters[namespace][PREFIX]"
const DEFAULTS = {defaultLevel: "INFO", defaultQuery: {[TIME_RANGE_KEY]: "PT24H", [NAMESPACE_KEY]: "company"}}

describe("hasUserLogFilters", () => {
    test.each([
        ["an empty query", {}],
        ["only the default level", {[LEVEL_KEY]: "INFO"}],
        ["pagination keys", {page: "2", size: "50", [LEVEL_KEY]: "INFO"}],
    ])("shouldReportNoUserFilterFor%s", (_name, query) => {
        expect(hasUserLogFilters(query, {defaultLevel: "INFO"})).toBe(false)
    })

    test.each([
        ["a search", {"filters[q][EQUALS]": "boom"}],
        ["a namespace", {"filters[namespace][IN]": "company.team"}],
        ["an absolute date", {"filters[startDate][GREATER_THAN_OR_EQUAL_TO]": "2026-01-01T00:00:00Z"}],
        ["a non default level", {[LEVEL_KEY]: "ERROR"}],
        ["a level below the default", {"filters[level][LESS_THAN_OR_EQUAL_TO]": "INFO"}],
    ])("shouldReportUserFilterFor%s", (_name, query) => {
        expect(hasUserLogFilters(query, {defaultLevel: "INFO"})).toBe(true)
    })

    test("shouldReportNoUserFilterForTheDefaultsTheFilterWrites", () => {
        const query = {[TIME_RANGE_KEY]: "PT24H", [NAMESPACE_KEY]: "company", [LEVEL_KEY]: "INFO"}

        expect(hasUserLogFilters(query, DEFAULTS)).toBe(false)
    })

    test.each([
        ["a narrowed time range", {[TIME_RANGE_KEY]: "PT1H"}],
        ["another namespace", {[NAMESPACE_KEY]: "other"}],
        ["a namespace comparator the default does not use", {"filters[namespace][IN]": "company"}],
    ])("shouldReportUserFilterNextToTheDefaultsFor%s", (_name, override) => {
        const query = {[TIME_RANGE_KEY]: "PT24H", [NAMESPACE_KEY]: "company", ...override}

        expect(hasUserLogFilters(query, DEFAULTS)).toBe(true)
    })

    test("shouldIgnoreScopeFieldsInjectedByAnEmbeddingTab", () => {
        const query = {"filters[namespace][EQUALS]": "company.team", "filters[flowId][EQUALS]": "hello"}

        expect(hasUserLogFilters(query, {defaultLevel: "INFO", scopeFields: ["namespace", "flowId"]})).toBe(false)
        expect(hasUserLogFilters({...query, "filters[q][EQUALS]": "boom"}, {defaultLevel: "INFO", scopeFields: ["namespace", "flowId"]})).toBe(true)
    })
})

describe("isDefaultLogLevel", () => {
    test("shouldOnlyAcceptTheDefaultLevelInMinDirection", () => {
        expect(isDefaultLogLevel(undefined, "INFO")).toBe(true)
        expect(isDefaultLogLevel({value: "INFO", direction: "min"}, "INFO")).toBe(true)
        expect(isDefaultLogLevel({value: "INFO", direction: "max"}, "INFO")).toBe(false)
        expect(isDefaultLogLevel({value: "WARN", direction: "min"}, "INFO")).toBe(false)
    })
})

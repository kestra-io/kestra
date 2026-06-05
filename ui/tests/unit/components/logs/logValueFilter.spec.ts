import {describe, test, expect} from "vitest"
import {isFilterableLogField, buildValueFilterQuery} from "../../../../src/components/logs/logValueFilter"

describe("isFilterableLogField", () => {
    test("returns true for fields backed by a log filter key", () => {
        expect(isFilterableLogField("flowId")).toBe(true)
        expect(isFilterableLogField("namespace")).toBe(true)
        expect(isFilterableLogField("taskId")).toBe(true)
        expect(isFilterableLogField("taskRunId")).toBe(true)
    })

    test("returns false for fields with no log filter key", () => {
        expect(isFilterableLogField("executionId")).toBe(false)
        expect(isFilterableLogField("thread")).toBe(false)
        expect(isFilterableLogField("message")).toBe(false)
    })
})

describe("buildValueFilterQuery", () => {
    test("filter-for on a text field uses EQUALS and resets the page", () => {
        const query = buildValueFilterQuery({page: "3", size: "25"}, "flowId", "daily-etl", false)
        expect(query).toEqual({
            page: "1",
            size: "25",
            "filters[flowId][EQUALS]": "daily-etl",
        })
    })

    test("filter-out on a text field uses NOT_EQUALS", () => {
        const query = buildValueFilterQuery({}, "taskId", "load_warehouse", true)
        expect(query?.["filters[taskId][NOT_EQUALS]"]).toBe("load_warehouse")
    })

    test("namespace maps to IN / NOT_IN, not EQUALS", () => {
        expect(buildValueFilterQuery({}, "namespace", "company.data", false))
            .toHaveProperty("filters[namespace][IN]", "company.data")
        expect(buildValueFilterQuery({}, "namespace", "company.data", true))
            .toHaveProperty("filters[namespace][NOT_IN]", "company.data")
    })

    test("preserves existing query params and overrides a matching filter", () => {
        const query = buildValueFilterQuery(
            {"filters[level][GREATER_THAN_OR_EQUAL_TO]": "TRACE", "filters[flowId][EQUALS]": "old"},
            "flowId",
            "new",
            false,
        )
        expect(query?.["filters[level][GREATER_THAN_OR_EQUAL_TO]"]).toBe("TRACE")
        expect(query?.["filters[flowId][EQUALS]"]).toBe("new")
    })

    test("replaces an opposing filter-out with filter-for on the same field", () => {
        const existing = {"filters[flowId][NOT_EQUALS]": "old-flow", "size": "25"}
        const result = buildValueFilterQuery(existing, "flowId", "new-flow", false)
        expect(result).not.toHaveProperty("filters[flowId][NOT_EQUALS]")
        expect(result?.["filters[flowId][EQUALS]"]).toBe("new-flow")
        expect(result?.size).toBe("25")
    })

    test("replaces an opposing filter-for with filter-out on the same field", () => {
        const existing = {"filters[flowId][EQUALS]": "my-flow", "size": "25"}
        const result = buildValueFilterQuery(existing, "flowId", "my-flow", true)
        expect(result).not.toHaveProperty("filters[flowId][EQUALS]")
        expect(result?.["filters[flowId][NOT_EQUALS]"]).toBe("my-flow")
    })

    test("returns null for a non-filterable field", () => {
        expect(buildValueFilterQuery({}, "executionId", "abc", false)).toBeNull()
    })

    test("respects a custom page key (embedded view)", () => {
        const query = buildValueFilterQuery({logsPage: "4"}, "flowId", "x", false, "logsPage")
        expect(query?.logsPage).toBe("1")
    })
})

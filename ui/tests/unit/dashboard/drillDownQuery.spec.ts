import {describe, test, expect, afterEach, vi} from "vitest"

// buildFullQuery reads the default chart duration from the misc store; mock it so each test
// controls that value directly instead of depending on a real Pinia + API-backed store.
const miscState = vi.hoisted(() => ({configs: undefined as Record<string, any> | undefined}))
vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: miscState.configs}),
}))

import {buildFullQuery} from "../../../src/components/dashboard/composables/chartDrillDown"

describe("buildFullQuery", () => {
    afterEach(() => {
        miscState.configs = undefined
    })

    test("adds scope + pagination + the default time filter when the target is time-filtered", () => {
        const result = buildFullQuery(
            {name: "executions/list", query: {"filters[state][IN]": "FAILED"}, timeFiltered: true},
            {size: 100, page: 1},
        )

        expect(result).toEqual({
            "filters[state][IN]": "FAILED",
            scope: "USER",
            size: 100,
            page: 1,
            "filters[timeRange][EQUALS]": "PT24H",
        })
    })

    test("honors the configured chartDefaultDuration over the PT24H fallback", () => {
        miscState.configs = {chartDefaultDuration: "PT1H"}

        const result = buildFullQuery({name: "executions/list", query: {}, timeFiltered: true}, {size: 25, page: 2})

        expect(result["filters[timeRange][EQUALS]"]).toBe("PT1H")
    })

    test("omits the time filter when the target is not time-filtered (Flows has no time dimension)", () => {
        const result = buildFullQuery(
            {name: "flows/list", query: {"filters[namespace][IN]": "ns"}, timeFiltered: false},
            {size: 100, page: 1},
        )

        expect(result).toEqual({"filters[namespace][IN]": "ns", scope: "USER", size: 100, page: 1})
    })

    test("omits size/page when no pagination argument is given (the LogsWrapper :filters binding)", () => {
        const result = buildFullQuery({name: "logs/list", query: {"filters[taskId][EQUALS]": "t"}, timeFiltered: true})

        expect(result).toEqual({
            "filters[taskId][EQUALS]": "t",
            scope: "USER",
            "filters[timeRange][EQUALS]": "PT24H",
        })
        expect(result.size).toBeUndefined()
        expect(result.page).toBeUndefined()
    })
})

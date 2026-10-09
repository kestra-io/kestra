import {beforeEach, describe, expect, it, vi} from "vitest"
import * as ExecutionsAPI from "@kestra-io/kestra-sdk/executions"
import {searchLoopIterations} from "./loopIterations"

vi.mock("@kestra-io/kestra-sdk/executions", () => ({searchExecutions: vi.fn(), execution: vi.fn()}))

const root = {id: "root", namespace: "company.team", flowId: "close_lite", startDate: "2026-10-09T09:49:13.994699Z"}

describe("searchLoopIterations", () => {
    beforeEach(() => {
        vi.mocked(ExecutionsAPI.searchExecutions).mockClear()
        vi.mocked(ExecutionsAPI.searchExecutions).mockResolvedValue({results: [], total: 0} as never)
    })

    it("shouldBoundTheSearchByTheRootEndDateWhenTheRootHasFinished", async () => {
        await searchLoopIterations({root: {...root, endDate: "2026-10-09T09:49:17.019006Z"}, state: "FAILED"})

        const {filters} = vi.mocked(ExecutionsAPI.searchExecutions).mock.calls[0][0] as {filters: {field: string; operation: string; value: string}[]}
        expect(filters).toContainEqual({field: "endDate", operation: "LESS_THAN_OR_EQUAL_TO", value: "2026-10-09T09:49:18Z"})
    })

    it("shouldNotBoundTheSearchByAnEndDateWhileTheRootRuns", async () => {
        await searchLoopIterations({root, state: "FAILED"})

        const {filters} = vi.mocked(ExecutionsAPI.searchExecutions).mock.calls[0][0] as {filters: {field: string}[]}
        expect(filters.some((filter) => filter.field === "endDate")).toBe(false)
    })
})

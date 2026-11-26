import {beforeEach, describe, expect, it, vi} from "vitest"
import {setActivePinia} from "pinia"
import {routedPinia} from "../../tests/unit/memoryRouter"
import {useExecutionsStore} from "../stores/executions"
import {findSubflowExecutions} from "./subflowExecutions"

const child = (index: number) => ({
    id: `child-${index}`,
    namespace: "demo",
    flowId: "subflow",
    state: {current: "SUCCESS", histories: []},
    trigger: {variables: {executionId: "parent", taskRunId: `tr-${index}`}},
})

describe("findSubflowExecutions", () => {
    beforeEach(() => setActivePinia(routedPinia()))

    it("shouldFetchEveryPageWhenAnExecutionStartedMoreSubflowsThanOnePageHolds", async () => {
        const all = Array.from({length: 150}, (_, index) => child(index))
        const findExecutions = vi.spyOn(useExecutionsStore(), "findExecutions")
            .mockImplementation((async ({page, size}: {page: number; size: number}) => ({
                results: all.slice((page - 1) * size, page * size),
                total: all.length,
            })) as never)

        const found = await findSubflowExecutions(["parent"])

        expect(findExecutions).toHaveBeenCalledTimes(2)
        expect(found.map(execution => execution.taskRunId)).toEqual(all.map((_, index) => `tr-${index}`))
    })

    it("shouldSearchThePlaygroundKindWhenTheParentIsAPlaygroundRun", async () => {
        const findExecutions = vi.spyOn(useExecutionsStore(), "findExecutions").mockResolvedValue({results: [], total: 0})

        await findSubflowExecutions(["parent"], "PLAYGROUND")

        expect(findExecutions).toHaveBeenCalledTimes(1)
        expect(findExecutions).toHaveBeenCalledWith(expect.objectContaining({"filters[kind][EQUALS]": "PLAYGROUND"}))
    })

    it("shouldLeaveTheKindOutForANormalParentSinceNoKindFilterMatchesNormalExecutions", async () => {
        const findExecutions = vi.spyOn(useExecutionsStore(), "findExecutions").mockResolvedValue({results: [], total: 0})

        await findSubflowExecutions(["parent"], "NORMAL")

        expect(Object.keys(findExecutions.mock.calls[0][0]).filter(key => key.startsWith("filters[kind]"))).toEqual([])
    })
})

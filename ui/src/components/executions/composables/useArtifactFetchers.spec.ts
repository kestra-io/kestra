import {beforeEach, describe, expect, it, vi} from "vitest"

const {loadTaskRunOutputs, callTaskRunEndpoint, searchByExecution} = vi.hoisted(() => ({
    loadTaskRunOutputs: vi.fn(() => Promise.resolve({})),
    callTaskRunEndpoint: vi.fn(() => Promise.resolve({})),
    searchByExecution: vi.fn(() => Promise.resolve({results: [], total: 0})),
}))

vi.mock("../../../composables/useTaskRunOutputs", () => ({loadTaskRunOutputs, callTaskRunEndpoint}))
vi.mock("@kestra-io/kestra-sdk/metrics", () => ({searchByExecution}))

import {useArtifactFetchers} from "./useArtifactFetchers"
import type {Execution} from "../../../stores/executions"

const execution = {
    id: "exec",
    taskRunList: [
        {id: "first", taskId: "dbt"},
        {id: "last", taskId: "dbt"},
    ],
} as unknown as Execution

describe("useArtifactFetchers", () => {
    beforeEach(() => vi.clearAllMocks())

    it("shouldReadTheLastRunWhenNoRunIsSelected", async () => {
        const {fetchOutputs, fetchEndpoint} = useArtifactFetchers(() => execution)

        await fetchOutputs("dbt")()
        await fetchEndpoint({id: "dbt", type: "dbt.Cli"})("graph")

        expect(loadTaskRunOutputs).toHaveBeenCalledWith("exec", "last")
        expect(callTaskRunEndpoint).toHaveBeenCalledWith("dbt.Cli", "graph", "exec", "last", {})
    })

    it("shouldReadTheSelectedRunWhenTheHostPicksOne", async () => {
        const {fetchOutputs, fetchMetrics, fetchEndpoint} = useArtifactFetchers(() => execution, () => "first")

        await fetchOutputs("dbt")()
        await fetchMetrics("dbt")()
        await fetchEndpoint({id: "dbt", type: "dbt.Cli"})("graph")

        expect(loadTaskRunOutputs).toHaveBeenCalledWith("exec", "first")
        expect(searchByExecution).toHaveBeenCalledWith(expect.objectContaining({taskRunId: "first"}))
        expect(callTaskRunEndpoint).toHaveBeenCalledWith("dbt.Cli", "graph", "exec", "first", {})
    })

    it("shouldLetThePluginOverrideTheSelectedRun", async () => {
        const {fetchOutputs} = useArtifactFetchers(() => execution, () => "first")

        await fetchOutputs("dbt")({taskRunId: "last"})

        expect(loadTaskRunOutputs).toHaveBeenCalledWith("exec", "last")
    })
})

import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {ref} from "vue"

const {searchLogs} = vi.hoisted(() => ({
    searchLogs: vi.fn(() => Promise.resolve({results: [], total: 3})),
}))

vi.mock("@kestra-io/kestra-sdk/logs", () => ({searchLogs}))
vi.mock("@kestra-io/kestra-sdk/metrics", () => ({searchByExecution: vi.fn(() => Promise.resolve({total: 0}))}))
vi.mock("../../../composables/useTaskRunOutputs", () => ({loadTaskRunOutputs: vi.fn(() => Promise.resolve({}))}))

import {useTaskRunPopupData} from "./useTaskRunPopupData"
import type {LevelKey} from "../../../utils/logs"

describe("useTaskRunPopupData", () => {
    beforeEach(() => vi.clearAllMocks())

    it("shouldRefetchTheLogsCountWhenTheSelectedLevelChanges", async () => {
        const logLevel = ref<LevelKey>("INFO")
        const {logsCount} = useTaskRunPopupData({
            enabled: () => true,
            executionId: () => "exec",
            taskId: () => "task",
            taskRunId: () => "run",
            logTaskRunId: () => "run",
            logAttemptNumber: () => 0,
            logChildTaskRunIds: () => [],
            logLevel: () => logLevel.value,
            taskRunState: () => "SUCCESS",
        })
        await flushPromises()

        expect(searchLogs).toHaveBeenCalledWith(expect.objectContaining({
            filters: expect.arrayContaining([
                {field: "level", operation: "GREATER_THAN_OR_EQUAL_TO", value: "INFO"},
            ]),
        }))
        expect(logsCount.value.value).toEqual(3)

        searchLogs.mockClear()
        logLevel.value = "DEBUG"
        await flushPromises()

        expect(searchLogs).toHaveBeenCalledWith(expect.objectContaining({
            filters: expect.arrayContaining([
                {field: "level", operation: "GREATER_THAN_OR_EQUAL_TO", value: "DEBUG"},
            ]),
        }))
    })
})

import {afterAll, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nShallowMount} from "../../../tests/unit/i18nMount"
import GanttTaskLogs from "../../components/executions/GanttTaskLogs.vue"
import type {Execution} from "../../stores/executions"
import type {FlowForExecution} from "@kestra-io/kestra-sdk"

const mocks = vi.hoisted(() => ({load: vi.fn()}))
vi.mock("../../composables/useTaskRunOutputs", () => ({loadTaskRunOutputs: mocks.load}))

const flow: FlowForExecution = {id: "parent", namespace: "tests", tasks: [], disabled: false, draft: false, deleted: false}
const execution = {id: "parent-execution", state: {current: "SUCCESS"}} as Execution
const taskRun = {id: "call-a", taskId: "call", state: {current: "SUCCESS" as const}}
const subflow = "io.kestra.plugin.core.flow.Subflow"

function mountLogs(taskType = subflow) {
    return i18nShallowMount(GanttTaskLogs, {props: {execution, taskRun, taskType, flow}})
}

describe("GanttTaskLogs output resolution", () => {
    beforeEach(() => { mocks.load.mockReset() })
    afterAll(() => localStorage.clear())

    it("shouldLoadChildExecutionIdFromOutputsWhenExecutionPayloadOmitsOutputs", async () => {
        mocks.load.mockResolvedValue({executionId: "child-a"})
        const wrapper = mountLogs()
        await flushPromises()

        expect(mocks.load).toHaveBeenCalledWith("parent-execution", "call-a")
        const viewer = wrapper.getComponent({name: "TaskRunDetails"})
        expect(viewer.props("targetExecutionId")).toBe("child-a")
        expect(viewer.props("taskRunId")).toBeUndefined()
        expect(viewer.props("targetFlow")).toBeUndefined()
        expect(viewer.props("hideTaskHeader")).toBe(false)
    })

    it("shouldKeepParentTaskLogsWhenTaskIsNotASubflow", async () => {
        const wrapper = mountLogs("io.kestra.plugin.core.log.Log")
        await flushPromises()

        expect(mocks.load).not.toHaveBeenCalled()
        expect(wrapper.getComponent({name: "TaskRunDetails"}).props("taskRunId")).toBe("call-a")
    })

    it("shouldReloadOutputsWhenSubflowStartsAfterItsRowWasOpened", async () => {
        mocks.load.mockResolvedValueOnce({}).mockResolvedValueOnce({executionId: "child-a"})
        const wrapper = mountLogs()
        await flushPromises()
        expect(wrapper.getComponent({name: "TaskRunDetails"}).props("taskRunId")).toBe("call-a")

        await wrapper.setProps({taskRun: {...taskRun, state: {current: "RUNNING"}}})
        await flushPromises()

        expect(wrapper.getComponent({name: "TaskRunDetails"}).props("targetExecutionId")).toBe("child-a")
    })

    it("shouldIgnorePreviousIterationOutputsWhenSelectionChangesBeforeTheyLoad", async () => {
        let resolveOld!: (value: Record<string, unknown>) => void
        mocks.load.mockReturnValueOnce(new Promise(resolve => {resolveOld = resolve}))
            .mockResolvedValueOnce({executionId: "child-b"})
        const wrapper = mountLogs()
        await wrapper.setProps({taskRun: {...taskRun, id: "call-b"}})
        await flushPromises()
        resolveOld({executionId: "child-a"})
        await flushPromises()

        expect(wrapper.getComponent({name: "TaskRunDetails"}).props("targetExecutionId")).toBe("child-b")
    })
})

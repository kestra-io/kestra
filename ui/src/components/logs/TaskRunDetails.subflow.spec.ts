import {afterAll, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {nextTick, reactive} from "vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

const store = vi.hoisted(() => ({
    executions: {} as {
        execution: unknown
        loadLogs: ReturnType<typeof vi.fn>
        reusedLogsOf: ReturnType<typeof vi.fn>
        isReusedTaskRun: ReturnType<typeof vi.fn>
        followLogs: ReturnType<typeof vi.fn>
        loadFlowForExecution: ReturnType<typeof vi.fn>
        subscribeToExecution: ReturnType<typeof vi.fn>
    },
}))
const outputs = vi.hoisted(() => ({load: vi.fn()}))

vi.mock("../../stores/executions", () => ({useExecutionsStore: () => store.executions}))
vi.mock("../../stores/core", () => ({useCoreStore: () => ({message: undefined})}))
vi.mock("@kestra-io/kestra-sdk", () => ({useClient: () => ({})}))
vi.mock("@kestra-io/kestra-sdk/outputs", () => ({taskRunOutputs: vi.fn().mockResolvedValue({})}))
vi.mock("../../composables/useTaskRunOutputs", () => ({loadTaskRunOutputs: outputs.load}))

import TaskRunDetails from "./TaskRunDetails.vue"

const SUBFLOW = "io.kestra.plugin.core.flow.Subflow"

const flow = (taskType: string) => ({
    id: "parent",
    namespace: "company.team",
    disabled: false,
    draft: false,
    deleted: false,
    tasks: [{id: "call_child", type: taskType}],
})

const execution = (state: string) => ({
    id: "parent-execution",
    namespace: "company.team",
    flowId: "parent",
    flowRevision: 1,
    state: {current: state},
    taskRunList: [{id: "tr-sub", taskId: "call_child", state: {current: state}}],
})

function mountDetails(taskType = SUBFLOW) {
    return i18nMount(TaskRunDetails, {
        props: {targetFlow: flow(taskType)},
        global: {
            stubs: {
                TaskRunLine: true,
                LogLine: true,
                DynamicScroller: {
                    props: ["items"],
                    template: "<div><slot v-for='(item, index) in items' :item='item' :index='index' :active='true' /></div>",
                },
                DynamicScrollerItem: {template: "<div><slot /></div>"},
            },
        },
    })
}

function childViewers(wrapper: ReturnType<typeof mountDetails>) {
    return wrapper.findAllComponents({name: "TaskRunDetails"}).filter((viewer) => viewer.props("targetExecutionId") !== undefined)
}

describe("TaskRunDetails subflow logs", () => {
    afterAll(() => localStorage.clear())

    beforeEach(() => {
        outputs.load.mockReset()
        store.executions = reactive({
            execution: execution("SUCCESS"),
            loadLogs: vi.fn().mockResolvedValue([{level: "INFO", taskRunId: "tr-sub", attemptNumber: 0, message: "child started"}]),
            reusedLogsOf: vi.fn(() => []),
            isReusedTaskRun: vi.fn(() => false),
            followLogs: vi.fn(() => Promise.resolve({close: vi.fn(), onmessage: null, onerror: null})),
            loadFlowForExecution: vi.fn().mockResolvedValue(undefined),
            subscribeToExecution: vi.fn(() => ({close: vi.fn()})),
        })
    })

    it("shouldShowChildLogsWhenTheExecutionPayloadOmitsSubflowOutputs", async () => {
        outputs.load.mockResolvedValue({executionId: "child-a"})
        const wrapper = mountDetails()
        await flushPromises()

        expect(outputs.load).toHaveBeenCalledWith("parent-execution", "tr-sub")
        const viewers = childViewers(wrapper)
        expect(viewers).toHaveLength(1)
        expect(viewers[0].props("targetExecutionId")).toBe("child-a")
        expect(viewers[0].props("allowAutoExpandSubflows")).toBe(false)
        expect(store.executions.subscribeToExecution).toHaveBeenCalledWith("child-a", expect.any(Object))
        wrapper.unmount()
    })

    it("shouldKeepTheChildViewerWithoutRefetchingWhenTheParentExecutionUpdates", async () => {
        outputs.load.mockResolvedValue({executionId: "child-a"})
        store.executions.execution = execution("RESTARTED")
        const wrapper = mountDetails()
        await flushPromises()
        const [viewer] = childViewers(wrapper)
        expect(viewer.props("targetExecutionId")).toBe("child-a")

        store.executions.execution = execution("RUNNING")
        await nextTick()
        await flushPromises()

        expect(outputs.load).toHaveBeenCalledTimes(1)
        expect(childViewers(wrapper)[0].vm.$.uid).toBe(viewer.vm.$.uid)
        expect(store.executions.subscribeToExecution).toHaveBeenCalledTimes(1)
        wrapper.unmount()
    })

    it("shouldRequeryOutputsOnStateChangeUntilTheChildExecutionExists", async () => {
        outputs.load.mockResolvedValueOnce({}).mockResolvedValueOnce({executionId: "child-a"})
        store.executions.execution = execution("RESTARTED")
        const wrapper = mountDetails()
        await flushPromises()
        expect(childViewers(wrapper)).toHaveLength(0)

        store.executions.execution = execution("RUNNING")
        await nextTick()
        await flushPromises()

        expect(outputs.load).toHaveBeenCalledTimes(2)
        expect(childViewers(wrapper)[0].props("targetExecutionId")).toBe("child-a")
        wrapper.unmount()
    })

    it("shouldNotQueryOutputsWhenTheTaskIsNotASubflow", async () => {
        const wrapper = mountDetails("io.kestra.plugin.core.log.Log")
        await flushPromises()

        expect(outputs.load).not.toHaveBeenCalled()
        expect(childViewers(wrapper)).toHaveLength(0)
        wrapper.unmount()
    })
})

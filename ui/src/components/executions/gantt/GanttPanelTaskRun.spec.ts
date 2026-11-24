import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {setActivePinia} from "pinia"
import {ref} from "vue"
import {routedPinia} from "../../../../tests/unit/memoryRouter"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import {useExecutionsStore} from "../../../stores/executions"
import type {ExecutionTaskRun} from "../executionTaskRun"
import GanttPanelTaskRun from "./GanttPanelTaskRun.vue"
import {GANTT_TREE, createGanttTree, createTaskRunNode} from "./ganttTree"

const SUBFLOW_TYPE = "io.kestra.plugin.core.flow.Subflow"
const iteration = {id: "iteration", namespace: "demo.subflows", flowId: "loop_subflows"}
const taskRun = {id: "tr-call", taskId: "call_subflow", state: {current: "SUCCESS", histories: []}} as unknown as ExecutionTaskRun

const child = (id: string, taskRunId: string) => ({
    id,
    namespace: "demo.subflows",
    flowId: `flow_${id}`,
    state: {current: "SUCCESS"},
    trigger: {variables: {executionId: "iteration", taskRunId}},
})

function mountPanel(taskType: string) {
    return i18nMount(GanttPanelTaskRun, {
        messages: {open_subflow_execution: "Open the {flow} subflow execution"},
        props: {node: createTaskRunNode(iteration, taskRun, taskType, [])},
        global: {
            provide: {[GANTT_TREE as symbol]: createGanttTree({compact: ref(false), levelFilter: ref(undefined)})},
            stubs: {GanttTaskLogs: true, TaskRunDetails: true},
        },
    })
}

describe("GanttPanelTaskRun", () => {
    beforeEach(() => setActivePinia(routedPinia()))

    it("shouldLinkToTheSubflowExecutionStartedByTheSelectedTaskRun", async () => {
        const findExecutions = vi.spyOn(useExecutionsStore(), "findExecutions")
            .mockResolvedValue({results: [child("mine", "tr-call"), child("sibling", "tr-other")], total: 2} as never)

        const wrapper = mountPanel(SUBFLOW_TYPE)
        await flushPromises()

        expect(findExecutions).toHaveBeenCalledWith(expect.objectContaining({"filters[triggerExecutionId][IN]": ["iteration"]}))
        const links = wrapper.findAll("[data-test='gantt-subflow-execution']")
        expect(links.map(link => link.text())).toEqual(["Open the flow_mine subflow execution"])
    })

    it("shouldNotLookForSubflowExecutionsOfATaskThatStartsNone", async () => {
        const findExecutions = vi.spyOn(useExecutionsStore(), "findExecutions")

        const wrapper = mountPanel("io.kestra.plugin.core.log.Log")
        await flushPromises()

        expect(findExecutions).not.toHaveBeenCalled()
        expect(wrapper.find("[data-test='gantt-subflow-execution']").exists()).toBe(false)
    })
})

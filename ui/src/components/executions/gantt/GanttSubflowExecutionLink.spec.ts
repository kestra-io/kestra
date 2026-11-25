import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {RouterLink} from "vue-router"
import {setActivePinia} from "pinia"
import {routedPinia} from "../../../../tests/unit/memoryRouter"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import {useExecutionsStore} from "../../../stores/executions"
import type {ExecutionTaskRun} from "../executionTaskRun"
import GanttSubflowExecutionLink from "./GanttSubflowExecutionLink.vue"
import {createTaskRunNode} from "./ganttTree"

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

function mountLink(taskType: string) {
    return i18nMount(GanttSubflowExecutionLink, {
        messages: {open_subflow_execution: "Open the {flow} subflow execution"},
        props: {node: createTaskRunNode(iteration, taskRun, taskType, [])},
    })
}

describe("GanttSubflowExecutionLink", () => {
    beforeEach(() => setActivePinia(routedPinia()))

    it("shouldLinkToTheSubflowExecutionStartedByTheSelectedTaskRun", async () => {
        const findExecutions = vi.spyOn(useExecutionsStore(), "findExecutions")
            .mockResolvedValue({results: [child("mine", "tr-call"), child("sibling", "tr-other")], total: 2} as never)

        const wrapper = mountLink(SUBFLOW_TYPE)
        await flushPromises()

        expect(findExecutions).toHaveBeenCalledWith(expect.objectContaining({"filters[triggerExecutionId][IN]": ["iteration"]}))
        const link = wrapper.get("[data-test='gantt-subflow-execution']")
        expect(link.text()).toBe("flow_mine")
        expect(link.attributes("aria-label")).toBe("Open the flow_mine subflow execution")
        expect(wrapper.findComponent(RouterLink).props("to")).toEqual({
            name: "executions/update",
            params: {namespace: "demo.subflows", flowId: "flow_mine", id: "mine"},
        })
    })

    it("shouldNotLookForSubflowExecutionsOfATaskThatStartsNone", async () => {
        const findExecutions = vi.spyOn(useExecutionsStore(), "findExecutions")

        const wrapper = mountLink("io.kestra.plugin.core.log.Log")
        await flushPromises()

        expect(findExecutions).not.toHaveBeenCalled()
        expect(wrapper.find("[data-test='gantt-subflow-execution']").exists()).toBe(false)
    })
})

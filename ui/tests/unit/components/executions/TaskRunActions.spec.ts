import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"

import TaskRunActions from "../../../../src/components/executions/TaskRunActions.vue"
import {useExecutionsStore} from "../../../../src/stores/executions"

const routerPush = vi.hoisted(() => vi.fn())
vi.mock("vue-router", () => ({
    useRoute: () => ({params: {namespace: "demo"}}),
    useRouter: () => ({push: routerPush}),
}))

const i18n = createI18n({legacy: false, locale: "en", missingWarn: false, fallbackWarn: false, messages: {en: {}}})

const execution = {id: "iteration", namespace: "demo", flowId: "loop_subflows", state: {current: "SUCCESS", histories: []}}
const flow = {tasks: [
    {id: "call_subflow", type: "io.kestra.plugin.core.flow.Subflow"},
    {id: "log_result", type: "io.kestra.plugin.core.log.Log"},
]}

function mountActions(taskId: string) {
    return mount(TaskRunActions, {
        props: {taskRun: {id: "tr-call", taskId, state: {current: "SUCCESS", histories: []}}, execution, flow},
        global: {
            plugins: [i18n],
            stubs: {
                KsDropdown: {template: "<div><slot name=\"dropdown\" /></div>"},
                KsDropdownMenu: {template: "<div><slot /></div>"},
                KsDropdownItem: {template: "<div @click=\"$emit('click', $event)\"><slot /></div>"},
                KsButton: true,
                Metrics: true,
                Outputs: true,
                Restart: true,
                ChangeStatus: true,
                TaskEdit: true,
                WorkerInfo: true,
                AiIcon: true,
            },
        },
    })
}

describe("TaskRunActions", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        routerPush.mockReset()
    })

    it("shouldOpenTheSubflowExecutionStartedByTheTaskRunWhenOutputsAreNotOnTheExecution", async () => {
        const executionsStore = useExecutionsStore()
        executionsStore.findExecutions = vi.fn().mockResolvedValue({
            results: [
                {id: "other", namespace: "demo", flowId: "other_flow", trigger: {variables: {executionId: "iteration", taskRunId: "tr-other"}}},
                {id: "child", namespace: "demo", flowId: "subflow_add", trigger: {variables: {executionId: "iteration", taskRunId: "tr-call"}}},
            ],
            total: 2,
        })

        const wrapper = mountActions("call_subflow")
        await wrapper.get("[data-test='task-run-subflow-execution']").trigger("click")
        await flushPromises()

        expect(executionsStore.findExecutions).toHaveBeenCalledWith(expect.objectContaining({"filters[triggerExecutionId][EQUALS]": "iteration"}))
        expect(routerPush).toHaveBeenCalledWith({name: "executions/update", params: {namespace: "demo", flowId: "subflow_add", id: "child"}})
    })

    it("shouldNotOfferTheSubflowExecutionForATaskThatStartsNone", () => {
        const wrapper = mountActions("log_result")

        expect(wrapper.find("[data-test='task-run-subflow-execution']").exists()).toBe(false)
    })
})

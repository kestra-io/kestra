import {mount} from "@vue/test-utils"
import {describe, it, expect, vi, beforeEach} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"
import TaskRunActions from "./TaskRunActions.vue"
import {useExecutionsStore} from "../../stores/executions"

vi.mock("vue-router", () => ({
    useRoute: vi.fn(() => ({
        params: {namespace: "ns-1"},
    })),
    useRouter: vi.fn(() => ({})),
}))

vi.mock("../../utils/toast", () => ({
    useToast: vi.fn(() => ({
        confirm: vi.fn((_msg: string, callback: () => void) => callback()),
    })),
}))

const i18n = createI18n({
    legacy: false,
    locale: "en",
    missingWarn: false,
    fallbackWarn: false,
    messages: {
        en: {
            iteration_number: "iteration_number",
            delete_log_iteration: "delete_log_iteration",
        },
    },
})

type TaskRunActionsProps = InstanceType<typeof TaskRunActions>["$props"]

function mountActions(propsData: Partial<TaskRunActionsProps>) {
    return mount(TaskRunActions, {
        props: propsData as TaskRunActionsProps,
        global: {
            plugins: [i18n],
            stubs: {
                KsDropdown: {
                    template: "<div><slot name=\"dropdown\" /></div>",
                },
                KsDropdownMenu: {
                    template: "<div><slot /></div>",
                },
                KsSearch: true,
                KsScrollbar: {
                    template: "<div><slot /></div>",
                },
                KsIcon: {
                    template: "<span><slot /></span>",
                },
                KsText: {
                    template: "<span><slot /></span>",
                },
                KsDropdownItem: {
                    template: "<div @click=\"$emit('click', $event)\"><slot /></div>",
                    props: ["divided"],
                },
                KsButton: {
                    template: "<button class=\"ks-button\"><slot /></button>",
                },
                Metrics: true,
                Outputs: true,
                Restart: true,
                ChangeStatus: true,
                TaskEdit: true,
                SubFlowLink: true,
                WorkerInfo: true,
                AiIcon: true,
                DotsVertical: true,
                Download: true,
                Copy: true,
                Delete: true,
                NodeMenuItem: true,
            },
        },
    })
}

describe("TaskRunActions", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        setActivePinia(createPinia())
    })

    const execution = {id: "ex-1", flowId: "flow-1", namespace: "ns-1", state: {current: "SUCCESS"}}

    it("renders options and resolves labels", () => {
        const wrapper = mountActions({
            taskRun: {id: "tr-1", taskId: "task-1", state: {current: "SUCCESS"}},
            taskRuns: [
                {id: "tr-1", taskId: "task-1", value: "Iteration 1", state: {current: "SUCCESS"}},
                {id: "tr-2", taskId: "task-1", value: undefined, state: {current: "SUCCESS"}},
            ],
            execution,
        })

        const options = wrapper.findAll("[data-test='task-run-iteration']")
        expect(options).toHaveLength(2)
        expect(options[0].text()).toBe("Iteration 1")
        expect(options[1].text()).toBe("iteration_number")
    })

    const execution2 = {...execution, id: "ex-2"}
    it("targets delete logs at the selected run", async () => {
        const executionsStore = useExecutionsStore()
        executionsStore.deleteLogs = vi.fn().mockResolvedValue({})

        const wrapper = mountActions({
            taskRun: {id: "tr-1", taskId: "task-1", state: {current: "SUCCESS"}},
            taskRuns: [
                {id: "tr-1", taskId: "task-1", value: "Iter 1", state: {current: "SUCCESS"}},
                {id: "tr-2", taskId: "task-1", value: "Iter 2", state: {current: "SUCCESS"}},
            ],
            execution: execution2,
        })

        const iter2 = wrapper.findAll("[data-test='task-run-iteration']").find(w => w.text().includes("Iter 2") || w.text().includes("iteration_number"))
        await iter2!.trigger("click")
        const deleteBtn = wrapper.find("[data-test='task-run-delete-logs']")
        await deleteBtn.trigger("click")

        expect(executionsStore.deleteLogs).toHaveBeenCalledWith({
            executionId: "ex-2",
            params: {taskRunId: "tr-2"},
        })
    })

    const execution3 = {...execution, id: "ex-3"}
    it("targets download logs at the selected run", async () => {
        const executionsStore = useExecutionsStore()
        executionsStore.downloadLogs = vi.fn().mockResolvedValue("log content")

        const wrapper = mountActions({
            taskRun: {id: "tr-1", taskId: "task-1", state: {current: "SUCCESS"}},
            taskRuns: [
                {id: "tr-1", taskId: "task-1", value: "Iter 1", state: {current: "SUCCESS"}},
                {id: "tr-2", taskId: "task-1", value: "Iter 2", state: {current: "SUCCESS"}},
            ],
            execution: execution3,
        })

        const iter2 = wrapper.findAll("[data-test='task-run-iteration']").find(w => w.text().includes("Iter 2") || w.text().includes("iteration_number"))
        await iter2!.trigger("click")
        const downloadBtn = wrapper.find("[data-test='task-run-download-logs']")
        await downloadBtn.trigger("click")

        expect(executionsStore.downloadLogs).toHaveBeenCalledWith({
            executionId: "ex-3",
            params: {"filters[taskRunId][EQUALS]": "tr-2"},
        })
    })

    const execution4 = {...execution, id: "ex-4"}
    it("targets copy logs at the selected run", async () => {
        const executionsStore = useExecutionsStore()
        executionsStore.downloadLogs = vi.fn().mockResolvedValue("log content")

        const wrapper = mountActions({
            taskRun: {id: "tr-1", taskId: "task-1", state: {current: "SUCCESS"}},
            taskRuns: [
                {id: "tr-1", taskId: "task-1", value: "Iter 1", state: {current: "SUCCESS"}},
                {id: "tr-2", taskId: "task-1", value: "Iter 2", state: {current: "SUCCESS"}},
            ],
            execution: execution4,
        })

        const iter2 = wrapper.findAll("[data-test='task-run-iteration']").find(w => w.text().includes("Iter 2") || w.text().includes("iteration_number"))
        await iter2!.trigger("click")
        const copyBtn = wrapper.find("[data-test='task-run-copy-logs']")
        await copyBtn.trigger("click")

        expect(executionsStore.downloadLogs).toHaveBeenCalledWith({
            executionId: "ex-4",
            params: {"filters[taskRunId][EQUALS]": "tr-2"},
        })
    })

    it("handles attemptIndex > 0 properly", async () => {
        const wrapper = mountActions({
            taskRun: {
                id: "tr-1",
                taskId: "task-1",
                    value: "Iter 1",
                    state: {current: "FAILED"},
                    attempts: [{state: {current: "FAILED"}}, {state: {current: "SUCCESS"}}],
            },
            taskRuns: [
                {
                    id: "tr-1",
                    taskId: "task-1",
                    value: "Iter 1",
                    state: {current: "FAILED"},
                    attempts: [{state: {current: "FAILED"}}, {state: {current: "SUCCESS"}}],
                },
                {
                    id: "tr-2",
                    taskId: "task-1",
                    value: "Iter 2",
                    state: {current: "SUCCESS"},
                    attempts: [{state: {current: "SUCCESS"}}],
                },
            ],
            execution: {id: "ex-5", flowId: "flow-1", namespace: "ns-1", state: {current: "SUCCESS"}},
            attemptIndex: 1,
        })

        expect((wrapper.vm as unknown as { currentAttemptIndex: number }).currentAttemptIndex).toBe(1)

        const iter2 = wrapper.findAll("[data-test='task-run-iteration']").find(w => w.text().includes("Iter 2") || w.text().includes("iteration_number"))
        await iter2!.trigger("click")
        expect((wrapper.vm as unknown as { currentAttemptIndex: number }).currentAttemptIndex).toBe(0)
    })
    it("hides selector for single-iteration tasks", () => {
        const wrapper = mountActions({
            taskRun: {id: "tr-1", taskId: "task-1", state: {current: "SUCCESS"}},
            taskRuns: [
                {id: "tr-1", taskId: "task-1", value: undefined, state: {current: "SUCCESS"}},
            ],
            execution,
        })

        expect(wrapper.find("[data-test='task-run-iteration']").exists()).toBe(false)
    })

    it("persists selection across unmount and remount for the same execution", async () => {
        const props = {
            taskRun: {id: "tr-1", taskId: "task-1", state: {current: "SUCCESS"}},
            taskRuns: [
                {id: "tr-1", taskId: "task-1", value: "Iter 1", state: {current: "SUCCESS"}},
                {id: "tr-2", taskId: "task-1", value: "Iter 2", state: {current: "SUCCESS"}},
            ],
            execution: {id: "ex-store-test", flowId: "flow-1", namespace: "ns-1", state: {current: "SUCCESS"}},
        }

        let wrapper = mountActions(props)

        const iter2 = wrapper.findAll("[data-test='task-run-iteration']").find(w => w.text().includes("Iter 2"))
        await iter2!.trigger("click")
        expect((wrapper.vm as unknown as { selectedTaskRunId: string }).selectedTaskRunId).toBe("tr-2")

        wrapper.unmount()

        // Remount with same props
        wrapper = mountActions(props)
        expect((wrapper.vm as unknown as { selectedTaskRunId: string }).selectedTaskRunId).toBe("tr-2")
    })

    it("starts clean for a different execution id", async () => {
        const props1 = {
            taskRun: {id: "tr-1", taskId: "task-1", state: {current: "SUCCESS"}},
            taskRuns: [
                {id: "tr-1", taskId: "task-1", value: "Iter 1", state: {current: "SUCCESS"}},
                {id: "tr-2", taskId: "task-1", value: "Iter 2", state: {current: "SUCCESS"}},
            ],
            execution: {id: "ex-diff-1", flowId: "flow-1", namespace: "ns-1", state: {current: "SUCCESS"}},
        }

        const wrapper1 = mountActions(props1)
        const iter2 = wrapper1.findAll("[data-test='task-run-iteration']").find(w => w.text().includes("Iter 2"))
        await iter2!.trigger("click")
        expect((wrapper1.vm as unknown as { selectedTaskRunId: string }).selectedTaskRunId).toBe("tr-2")

        // Use a new execution id, but same task
        const props2 = {
            taskRun: {id: "tr-1", taskId: "task-1", state: {current: "SUCCESS"}},
            taskRuns: [
                {id: "tr-1", taskId: "task-1", value: "Iter 1", state: {current: "SUCCESS"}},
                {id: "tr-2", taskId: "task-1", value: "Iter 2", state: {current: "SUCCESS"}},
            ],
            execution: {id: "ex-diff-2", flowId: "flow-1", namespace: "ns-1", state: {current: "SUCCESS"}},
        }

        const wrapper2 = mountActions(props2)
        // Defaults to the first task run since it's a clean execution id
        expect((wrapper2.vm as unknown as { selectedTaskRunId: string }).selectedTaskRunId).toBe("tr-1")
    })
})
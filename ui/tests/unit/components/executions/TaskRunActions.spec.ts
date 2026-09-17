import {mount} from "@vue/test-utils"
import {describe, it, expect, vi, beforeEach} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"
import TaskRunActions from "../../../../src/components/executions/TaskRunActions.vue"
import {useExecutionsStore} from "../../../../src/stores/executions"

vi.mock("vue-router", () => ({
    useRoute: vi.fn(() => ({
        params: {namespace: "ns-1"},
    })),
    useRouter: vi.fn(() => ({})),
}))

vi.mock("../../../../src/utils/toast", () => ({
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
                KsSelect: {
                    template: "<select @change=\"$emit('update:modelValue', $event.target.value)\" :value=\"modelValue\"><slot /></select>",
                    props: ["modelValue"],
                },
                KsOption: {
                    template: "<option :value=\"value\">{{ label }}</option>",
                    props: ["value", "label"],
                },
                KsDropdownItem: {
                    template: "<div class=\"ks-dropdown-item\"><slot /></div>",
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
        
        const options = wrapper.findAll("option")
        expect(options).toHaveLength(2)
        expect(options[0].text()).toBe("Iteration 1")
        expect(options[1].text()).toBe("iteration_number")
    })

    it("targets delete logs at the selected run", async () => {
        const executionsStore = useExecutionsStore()
        executionsStore.deleteLogs = vi.fn().mockResolvedValue({})
        
        const wrapper = mountActions({
            taskRun: {id: "tr-1", taskId: "task-1", state: {current: "SUCCESS"}},
            taskRuns: [
                {id: "tr-1", taskId: "task-1", value: "Iter 1", state: {current: "SUCCESS"}},
                {id: "tr-2", taskId: "task-1", value: "Iter 2", state: {current: "SUCCESS"}},
            ],
            execution,
        })
        
        await wrapper.find("select").setValue("tr-2")
        await (wrapper.vm as unknown as { deleteLogs: Function }).deleteLogs({id: "tr-2"})
        
        expect(executionsStore.deleteLogs).toHaveBeenCalledWith({
            executionId: "ex-1",
            params: {taskRunId: "tr-2"},
        })
    })
    
    it("targets download logs at the selected run", async () => {
        const executionsStore = useExecutionsStore()
        executionsStore.downloadLogs = vi.fn().mockResolvedValue("log content")
        
        const wrapper = mountActions({
            taskRun: {id: "tr-1", taskId: "task-1", state: {current: "SUCCESS"}},
            taskRuns: [
                {id: "tr-1", taskId: "task-1", value: "Iter 1", state: {current: "SUCCESS"}},
                {id: "tr-2", taskId: "task-1", value: "Iter 2", state: {current: "SUCCESS"}},
            ],
            execution,
        })
        
        await wrapper.find("select").setValue("tr-2")
        await (wrapper.vm as unknown as { downloadContent: Function }).downloadContent("tr-2")
        
        expect(executionsStore.downloadLogs).toHaveBeenCalledWith({
            executionId: "ex-1",
            params: {taskRunId: "tr-2"},
        })
    })
    
    it("targets copy logs at the selected run", async () => {
        const executionsStore = useExecutionsStore()
        executionsStore.downloadLogs = vi.fn().mockResolvedValue("log content")
        
        const wrapper = mountActions({
            taskRun: {id: "tr-1", taskId: "task-1", state: {current: "SUCCESS"}},
            taskRuns: [
                {id: "tr-1", taskId: "task-1", value: "Iter 1", state: {current: "SUCCESS"}},
                {id: "tr-2", taskId: "task-1", value: "Iter 2", state: {current: "SUCCESS"}},
            ],
            execution,
        })
        
        await wrapper.find("select").setValue("tr-2")
        await (wrapper.vm as unknown as { copyContent: Function }).copyContent("tr-2")
        
        expect(executionsStore.downloadLogs).toHaveBeenCalledWith({
            executionId: "ex-1",
            params: {taskRunId: "tr-2"},
        })
    })
    
    it("handles attemptIndex > 0 properly", () => {
        const wrapper = mountActions({
            taskRun: { 
                id: "tr-1", 
                taskId: "task-1",
                state: {current: "FAILED"},
                attempts: [{state: {current: "FAILED"}}, {state: {current: "SUCCESS"}}],
            },
            taskRuns: [
                { 
                    id: "tr-1", 
                    taskId: "task-1",
                    state: {current: "FAILED"},
                    attempts: [{state: {current: "FAILED"}}, {state: {current: "SUCCESS"}}],
                },
                { 
                    id: "tr-2", 
                    taskId: "task-1",
                    state: {current: "SUCCESS"},
                    attempts: [{state: {current: "SUCCESS"}}],
                },
            ],
            execution,
            attemptIndex: 1,
        })
        
        expect((wrapper.vm as unknown as { currentAttemptIndex: number }).currentAttemptIndex).toBe(1)
        
        wrapper.find("select").setValue("tr-2")
        expect((wrapper.vm as unknown as { currentAttemptIndex: number }).currentAttemptIndex).toBe(0)
    })
})

import {describe, expect, it, vi, beforeEach} from "vitest"
import {mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"
import TaskRunActions from "../../../../src/components/executions/TaskRunActions.vue"

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {namespace: "test"}}),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => true}}),
}))

export const mockPromptCopilot = vi.fn()
export const mockLoadLogs = vi.fn().mockResolvedValue([])

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({promptCopilot: mockPromptCopilot}),
}))

vi.mock("../../../../src/stores/core", () => ({
    useCoreStore: () => ({}),
}))

vi.mock("../../../../src/stores/executions", () => ({
    useExecutionsStore: () => ({loadLogs: mockLoadLogs}),
}))

vi.mock("../../../../src/utils/toast", () => ({
    useToast: () => ({success: vi.fn(), error: vi.fn()}),
}))

const i18n = createI18n({
    legacy: false,
    locale: "en",
    missingWarn: false,
    fallbackWarn: false,
})

describe("TaskRunActions", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    const execution = {
        id: "exe-1",
        state: {current: "SUCCESS"},
        flowId: "flow-1",
        namespace: "io.kestra.tests",
    }
    const baseTaskRun = {id: "run-1", taskId: "task-1", state: {current: "SUCCESS"}}

    it("should clamp selectedTaskRunIndex when taskRuns shrinks", async () => {
        const taskRuns = [
            {...baseTaskRun, id: "run-1", value: "A"},
            {...baseTaskRun, id: "run-2", value: "B"},
            {...baseTaskRun, id: "run-3", value: "C"},
        ]

        const wrapper = mount(TaskRunActions, {
            global: {
                plugins: [i18n],
                stubs: {
                    KsDropdown: {
                        template: "<div><slot /><slot name=\"dropdown\" /></div>",
                    },
                    KsDropdownMenu: {
                        template: "<div><slot /></div>",
                    },
                    KsDropdownItem: {
                        template: "<div><slot /></div>",
                    },
                    KsButton: {
                        template: "<button><slot /></button>",
                    },
                    KsSelect: {
                        name: "KsSelect",
                        props: ["modelValue"],
                        emits: ["update:modelValue"],
                        template: "<div><slot /></div>",
                    },
                    KsOption: {
                        name: "KsOption",
                        props: ["value"],
                        template: "<div></div>",
                    },
                    Metrics: true,
                    Outputs: true,
                    Restart: true,
                    ChangeStatus: true,
                    TaskEdit: true,
                    WorkerInfo: true,
                    NodeMenuItem: true,
                    SubFlowLink: true,
                },
            },
            props: {
                taskRun: taskRuns[0],
                taskRuns,
                execution,
            },
        })

        const select = wrapper.findComponent({name: "KsSelect"})
        expect(select.exists()).toBe(true)

        // Select the last iteration (index 2)
        await select.vm.$emit("update:modelValue", 2)
        expect((wrapper.vm as unknown as {selectedTaskRunIndex: number}).selectedTaskRunIndex).toBe(2)
        expect((wrapper.vm as unknown as {currentTaskRun: {id: string}}).currentTaskRun.id).toBe("run-3")

        // Shrink taskRuns to 2 items
        await wrapper.setProps({
            taskRuns: [
                {...baseTaskRun, id: "run-1", value: "A"},
                {...baseTaskRun, id: "run-2", value: "B"},
            ],
        })

        // It should clamp to index 1 (the new max)
        expect((wrapper.vm as unknown as {selectedTaskRunIndex: number}).selectedTaskRunIndex).toBe(1)
        expect((wrapper.vm as unknown as {currentTaskRun: {id: string}}).currentTaskRun.id).toBe("run-2")
    })

    it("should display iteration selector only for multi-iteration tasks", async () => {
        const wrapper = mount(TaskRunActions, {
            global: {
                plugins: [i18n],
                stubs: {
                    KsDropdown: {template: "<div><slot /><slot name=\"dropdown\" /></div>"},
                    KsDropdownMenu: {template: "<div><slot /></div>"},
                    KsDropdownItem: true,
                    KsButton: true,
                    KsSelect: true,
                    KsOption: true,
                    Metrics: true,
                    Outputs: true,
                    Restart: true,
                    ChangeStatus: true,
                    TaskEdit: true,
                    WorkerInfo: true,
                    NodeMenuItem: true,
                    SubFlowLink: true,
                },
            },
            props: {
                taskRun: baseTaskRun,
                taskRuns: [baseTaskRun],
                execution,
            },
        })

        // With only 1 taskRun, KsSelect should not be present
        expect(wrapper.findComponent({name: "KsSelect"}).exists()).toBe(false)
        
        // Single taskRun actions should still be present since we removed the guard
        expect(wrapper.findComponent({name: "Outputs"}).exists()).toBe(true)
    })

    it("should pass the selected taskRun to SubFlowLink when iteration changes", async () => {
        const taskRuns = [
            {...baseTaskRun, id: "run-1", outputs: {executionId: "subflow-exe-1"}},
            {...baseTaskRun, id: "run-2", outputs: {executionId: "subflow-exe-2"}},
        ]

        const wrapper = mount(TaskRunActions, {
            global: {
                plugins: [i18n],
                stubs: {
                    KsDropdown: {template: "<div><slot /><slot name=\"dropdown\" /></div>"},
                    KsDropdownMenu: {template: "<div><slot /></div>"},
                    KsDropdownItem: true,
                    KsButton: true,
                    KsSelect: {
                        name: "KsSelect",
                        props: ["modelValue"],
                        emits: ["update:modelValue"],
                        template: "<div><slot /></div>",
                    },
                    KsOption: true,
                    Metrics: true,
                    Outputs: true,
                    Restart: true,
                    ChangeStatus: true,
                    TaskEdit: true,
                    WorkerInfo: true,
                    NodeMenuItem: true,
                    SubFlowLink: {
                        name: "SubFlowLink",
                        props: ["executionId"],
                        template: "<div></div>",
                    },
                },
            },
            props: {
                taskRun: taskRuns[0],
                taskRuns,
                execution,
            },
        })

        const select = wrapper.findComponent({name: "KsSelect"})
        const subFlowLink = wrapper.findComponent({name: "SubFlowLink"})

        // Initially evaluates against taskRuns[0]
        expect(subFlowLink.props("executionId")).toBe("subflow-exe-1")

        // Switch to taskRuns[1]
        await select.vm.$emit("update:modelValue", 1)

        // SubFlowLink should now evaluate against taskRuns[1]
        expect(subFlowLink.props("executionId")).toBe("subflow-exe-2")
    })

    it("should reset selected iteration when taskRuns becomes empty", async () => {
        const taskRuns = [
            {...baseTaskRun, id: "run-1", value: "A"},
            {...baseTaskRun, id: "run-2", value: "B"},
            {...baseTaskRun, id: "run-3", value: "C"},
        ]

        const wrapper = mount(TaskRunActions, {
            global: {
                plugins: [i18n],
                stubs: {
                    KsDropdown: {template: "<div><slot /><slot name=\"dropdown\" /></div>"},
                    KsDropdownMenu: {template: "<div><slot /></div>"},
                    KsDropdownItem: true,
                    KsButton: true,
                    KsSelect: {
                        name: "KsSelect",
                        props: ["modelValue"],
                        emits: ["update:modelValue"],
                        template: "<div><slot /></div>",
                    },
                    KsOption: true,
                    Metrics: true,
                    Outputs: true,
                    Restart: true,
                    ChangeStatus: true,
                    TaskEdit: true,
                    WorkerInfo: true,
                    NodeMenuItem: true,
                    SubFlowLink: true,
                },
            },
            props: {
                taskRun: taskRuns[0],
                taskRuns,
                execution,
            },
        })

        const select = wrapper.findComponent({name: "KsSelect"})
        
        // Select index 2
        await select.vm.$emit("update:modelValue", 2)
        expect((wrapper.vm as unknown as {selectedTaskRunIndex: number}).selectedTaskRunIndex).toBe(2)

        // Empty the array
        await wrapper.setProps({taskRuns: []})
        expect((wrapper.vm as unknown as {selectedTaskRunIndex: number}).selectedTaskRunIndex).toBe(0)
    })

    it("should use the selected taskRun when fixing an error with AI", async () => {
        const taskRuns = [
            {...baseTaskRun, id: "run-1", state: {current: "FAILED"}},
            {...baseTaskRun, id: "run-2", state: {current: "FAILED"}},
        ]

        const wrapper = mount(TaskRunActions, {
            global: {
                plugins: [i18n],
                stubs: {
                    KsDropdown: {template: "<div><slot /><slot name=\"dropdown\" /></div>"},
                    KsDropdownMenu: {template: "<div><slot /></div>"},
                    KsDropdownItem: {
                        name: "KsDropdownItem",
                        template: "<div @click=\"$emit('click')\"><slot /></div>",
                    },
                    KsButton: true,
                    KsSelect: {
                        name: "KsSelect",
                        props: ["modelValue"],
                        emits: ["update:modelValue"],
                        template: "<div><slot /></div>",
                    },
                    KsOption: true,
                    Metrics: true,
                    Outputs: true,
                    Restart: true,
                    ChangeStatus: true,
                    TaskEdit: true,
                    WorkerInfo: true,
                    NodeMenuItem: true,
                    SubFlowLink: true,
                    AiIcon: true,
                },
            },
            props: {
                taskRun: taskRuns[0],
                taskRuns,
                execution,
                attemptIndex: 0,
            },
        })

        const select = wrapper.findComponent({name: "KsSelect"})
        
        // Switch to iteration index 1
        await select.vm.$emit("update:modelValue", 1)

        // Clear mock calls from before
        mockLoadLogs.mockClear()
        mockPromptCopilot.mockClear()

        // Trigger fixErrorWithAi
        // We know it's a method on the component
        await (wrapper.vm as unknown as { fixErrorWithAi: () => Promise<void> }).fixErrorWithAi()

        // Assert loadLogs used the new selected taskRun id
        expect(mockLoadLogs).toHaveBeenCalledWith(expect.objectContaining({
            params: expect.objectContaining({taskRunId: "run-2"}),
        }))

        // Assert prompt uses the taskId
        expect(mockPromptCopilot).toHaveBeenCalledWith(
            expect.stringContaining("task-1"), 
            expect.anything(),
        )
    })
})

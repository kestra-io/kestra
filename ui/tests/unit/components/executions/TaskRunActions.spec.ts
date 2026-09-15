import {mount} from "@vue/test-utils"
import {describe, expect, it, vi} from "vitest"
import TaskRunActions from "../../../../src/components/executions/TaskRunActions.vue"
import {createI18n} from "vue-i18n"
import en from "../../../../src/translations/en.json"

const i18n = createI18n({
    locale: "en",
    messages: en,
    legacy: false,
})

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {namespace: "io.kestra"}}),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => true}}),
}))

export const mockPromptCopilot = vi.fn()
export const mockLoadLogs = vi.fn().mockResolvedValue([])
export const mockDeleteLogs = vi.fn().mockResolvedValue(undefined)
export const mockDownloadLogs = vi.fn().mockResolvedValue(undefined)

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({promptCopilot: mockPromptCopilot}),
}))

vi.mock("../../../../src/stores/core", () => ({
    useCoreStore: () => ({}),
}))

vi.mock("../../../../src/stores/executions", () => ({
    useExecutionsStore: () => ({
        loadLogs: mockLoadLogs,
        deleteLogs: mockDeleteLogs,
        downloadLogs: mockDownloadLogs,
    }),
}))

vi.mock("../../../../src/utils/toast", () => ({
    useToast: () => ({
        confirm: vi.fn((msg, cb) => cb()),
    }),
}))

const execution = {
    id: "exe-1",
    flowId: "flow-1",
    namespace: "io.kestra",
    flowRevision: 1,
    state: {current: "SUCCESS"},
}

const baseTaskRun = {
    id: "run-1",
    taskId: "task-1",
    state: {current: "SUCCESS", startDate: "2024-01-01T00:00:00Z"},
    attempts: [{state: {current: "SUCCESS", startDate: "2024-01-01T00:00:00Z"}}],
}

function mountActions(propsData: Record<string, unknown>) {
    return mount(TaskRunActions, {
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
                    template: "<div><slot /><slot name=\"label\" :value=\"modelValue\" /></div>",
                },
                KsOption: {
                    name: "KsOption",
                    props: ["label", "value"],
                    template: "<div></div>",
                },
                KsDivider: true,
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
        props: propsData,
    })
}

describe("TaskRunActions", () => {
    it("should clamp and fallback selected iteration when taskRuns mutates", async () => {
        const taskRuns = [
            {...baseTaskRun, id: "run-1", value: "A"},
            {...baseTaskRun, id: "run-2", value: "B"},
            {...baseTaskRun, id: "run-3", value: "C"},
        ]

        const wrapper = mountActions({
            taskRun: taskRuns[0],
            taskRuns,
            execution,
        })

        const select = wrapper.findComponent({name: "KsSelect"})
        
        // Select index 2 (id: run-3)
        await select.vm.$emit("update:modelValue", "run-3")

        // Mutate array to remove run-3
        await wrapper.setProps({
            taskRuns: [
                {...baseTaskRun, id: "run-1", value: "A"},
                {...baseTaskRun, id: "run-2", value: "B"},
            ],
        })

        // It should fallback to run-1
        expect((wrapper.vm as unknown as {selectedTaskRunId: string}).selectedTaskRunId).toBe("run-1")
    })

    it("should correctly render KsOption and label based on filteredTaskRuns", async () => {
        const wrapper = mountActions({
            taskRun: baseTaskRun,
            taskRuns: [
                baseTaskRun,
                {...baseTaskRun, id: "run-2"}, // No value, should use Iteration 2
                {...baseTaskRun, id: "run-3", taskId: "other-task"}, // should be filtered out
            ],
            execution,
        })

        const options = wrapper.findAllComponents({name: "KsOption"})
        expect(options).toHaveLength(2)
        expect(options[0].props("label")).toBe("Iteration 1")
        expect(options[1].props("label")).toBe("Iteration 2")
    })

    it("should use the selected taskRun when clicking delete logs", async () => {
        const taskRuns = [
            {...baseTaskRun, id: "run-1", value: "A"},
            {...baseTaskRun, id: "run-2", value: "B"},
        ]

        const wrapper = mountActions({
            taskRun: taskRuns[0],
            taskRuns,
            execution,
        })

        const select = wrapper.findComponent({name: "KsSelect"})
        await select.vm.$emit("update:modelValue", "run-2")

        mockDeleteLogs.mockClear()
        await (wrapper.vm as unknown as { deleteLogs: (id: string) => void }).deleteLogs("run-2")
        expect(mockDeleteLogs).toHaveBeenCalledWith(expect.objectContaining({
            params: expect.objectContaining({taskRunId: "run-2"}),
        }))
    })

    it("should pass the selected taskRun to components when iteration changes", async () => {
        const taskRuns = [
            {...baseTaskRun, id: "run-1", outputs: {executionId: "subflow-1"}, taskId: "task-1"},
            {...baseTaskRun, id: "run-2", outputs: {executionId: "subflow-2"}, taskId: "task-1"},
        ]

        const wrapper = mountActions({
            taskRun: taskRuns[0],
            taskRuns,
            execution,
            attemptIndex: 1, // To test attemptIndex > 0
        })

        const select = wrapper.findComponent({name: "KsSelect"})

        expect(wrapper.findComponent({name: "SubFlowLink"}).props("executionId")).toBe("subflow-1")
        expect(wrapper.findComponent({name: "Restart"}).props("taskRun").id).toBe("run-1")
        expect(wrapper.findComponent({name: "ChangeStatus"}).props("taskRun").id).toBe("run-1")
        expect(wrapper.findComponent({name: "Metrics"}).props("taskRun").id).toBe("run-1")
        expect(wrapper.findComponent({name: "Outputs"}).props("taskRun").id).toBe("run-1")

        // Switch to taskRuns[1]
        await select.vm.$emit("update:modelValue", "run-2")

        expect(wrapper.findComponent({name: "SubFlowLink"}).props("executionId")).toBe("subflow-2")
        expect(wrapper.findComponent({name: "Restart"}).props("taskRun").id).toBe("run-2")
        expect(wrapper.findComponent({name: "ChangeStatus"}).props("taskRun").id).toBe("run-2")
        expect(wrapper.findComponent({name: "Metrics"}).props("taskRun").id).toBe("run-2")
        expect(wrapper.findComponent({name: "Outputs"}).props("taskRun").id).toBe("run-2")
    })

    it("should use the selected taskRun when fixing an error with AI", async () => {
        const taskRuns = [
            {...baseTaskRun, id: "run-1", state: {current: "FAILED"}},
            {...baseTaskRun, id: "run-2", state: {current: "FAILED"}},
        ]

        const wrapper = mountActions({
            taskRun: taskRuns[0],
            taskRuns,
            execution,
            attemptIndex: 0,
        })

        const select = wrapper.findComponent({name: "KsSelect"})
        await select.vm.$emit("update:modelValue", "run-2")

        mockLoadLogs.mockClear()
        mockPromptCopilot.mockClear()

        await (wrapper.vm as unknown as { fixErrorWithAi: () => Promise<void> }).fixErrorWithAi()

        expect(mockLoadLogs).toHaveBeenCalledWith(expect.objectContaining({
            params: expect.objectContaining({taskRunId: "run-2"}),
        }))
    })
})

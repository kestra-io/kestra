import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, waitFor, within} from "storybook/test"
import {mockStoryApiRoutes} from "../../../../../.storybook/apiMock"
import FailureDebugPanel from "../../../../../src/components/executions/gantt/FailureDebugPanel.vue"
import type {Execution} from "../../../../../src/stores/executions"

function history(state: string, date: string) {
    return {state, date}
}

function taskRun(id: string, taskId: string, state: string, startDate: string, parentTaskRunId?: string) {
    return {
        id,
        taskId,
        parentTaskRunId,
        namespace: "company.team",
        flowId: "orders-pipeline",
        executionId: "exec-failed",
        state: {current: state, histories: [history("RUNNING", startDate), history(state, startDate)]},
    }
}

function execution(state: string, taskRunList: ReturnType<typeof taskRun>[]): Execution {
    return {
        id: "exec-failed",
        namespace: "company.team",
        flowId: "orders-pipeline",
        state: {current: state, histories: []},
        taskRunList,
    } as unknown as Execution
}

const meta: Meta<typeof FailureDebugPanel> = {
    title: "Components/Executions/Gantt/FailureDebugPanel",
    component: FailureDebugPanel,
    parameters: {
        docs: {
            description: {
                component: "Focused debug panel opened over the Gantt view for a FAILED/KILLED execution: a scoped timeline, structural impact, and the relevant logs for the failing task.",
            },
        },
    },
    beforeEach() {
        mockStoryApiRoutes({"GET /logs/exec-failed": {results: [], total: 0}})
    },
}
export default meta
type Story = StoryObj<typeof FailureDebugPanel>

/** A successful execution has nothing to debug: the panel renders nothing. */
export const NoFailure: Story = {
    args: {
        execution: execution("SUCCESS", [
            taskRun("tr-1", "extract", "SUCCESS", "2025-01-01T00:00:00Z"),
        ]),
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        await expect(canvas.queryByRole("region")).toBeNull()
    },
}

export const SingleFailure: Story = {
    args: {
        execution: execution("FAILED", [
            taskRun("tr-1", "extract", "SUCCESS", "2025-01-01T00:00:00Z"),
            taskRun("tr-2", "transform", "FAILED", "2025-01-01T00:00:05Z"),
        ]),
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        await waitFor(() => expect(canvas.getByRole("region")).toBeVisible())
        await expect(canvas.queryByRole("tablist")).toBeNull()
        await expect(canvasElement.querySelector(".failure-debug-panel__subtitle")?.textContent).toContain("transform")
    },
}

export const MultipleFailures: Story = {
    args: {
        execution: execution("KILLED", [
            taskRun("tr-1", "extract", "SUCCESS", "2025-01-01T00:00:00Z"),
            taskRun("tr-2", "transform", "FAILED", "2025-01-01T00:00:10Z"),
            taskRun("tr-3", "load", "KILLED", "2025-01-01T00:00:05Z", "tr-1"),
        ]),
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        await waitFor(() => expect(canvas.getByRole("tablist")).toBeVisible())
        // "load" failed at :05s, before "transform" at :10s — it is auto-focused first.
        await expect(canvasElement.querySelector(".failure-debug-panel__subtitle")?.textContent).toContain("load")
        await expect(canvas.getAllByRole("tab")).toHaveLength(2)
    },
}

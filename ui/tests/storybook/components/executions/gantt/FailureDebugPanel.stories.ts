import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, userEvent, waitFor, within} from "storybook/test"
import {vueRouter} from "storybook-vue3-router"
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
        flowRevision: 1,
        state: {current: state, histories: []},
        taskRunList,
    } as unknown as Execution
}

const FLOW_SOURCE = `id: orders-pipeline
namespace: company.team

inputs:
  - id: region
    type: STRING

tasks:
  - id: extract
    type: io.kestra.plugin.core.log.Log
    message: extracting
  - id: transform
    type: io.kestra.plugin.core.log.Log
    message: "transforming {{ outputs.extract.value }}"
  - id: load
    type: io.kestra.plugin.core.log.Log
    message: "{{ secret('WAREHOUSE_URL') }}"
`

const meta: Meta<typeof FailureDebugPanel> = {
    title: "Components/Executions/Gantt/FailureDebugPanel",
    component: FailureDebugPanel,
    render: (args) => ({
        components: {FailureDebugPanel},
        setup: () => ({args}),
        template: `
            <FailureDebugPanel v-bind="args">
                <template #default="{shouldRender, isOpen, reopen, setReopenRef}">
                    <span v-if="shouldRender && !isOpen" :ref="setReopenRef">
                        <button type="button" @click="reopen">Debug this failure</button>
                    </span>
                </template>
            </FailureDebugPanel>
        `,
    }),
    parameters: {
        docs: {
            description: {
                component: "On-demand debug panel over the Gantt view for a FAILED/KILLED execution: a scoped timeline, structural impact, and the relevant logs for the failing task, opened from a reopen affordance rather than automatically.",
            },
        },
    },
    beforeEach() {
        mockStoryApiRoutes({
            "GET /logs/exec-failed": {results: [], total: 0},
            "GET /flows/company.team/orders-pipeline": {source: FLOW_SOURCE, inputs: [{id: "region", type: "STRING"}]},
            "POST /expressions/render": (context: {body?: unknown}) => {
                const {expressions} = JSON.parse((context.body as string) ?? "{}") as {expressions?: string[]}
                const rendered: Record<string, string> = {}
                for (const expression of expressions ?? []) {
                    const inputMatch = /^\{\{ inputs\.(\w+) \}\}$/.exec(expression)
                    rendered[expression] = inputMatch ? "us-east-1" : expression
                }
                return {rendered}
            },
            "GET /outputs/tasks/exec-failed/tr-1": {value: "48203 rows"},
        })
    },
}
export default meta
type Story = StoryObj<typeof FailureDebugPanel>

function editFlowRouterDecorator() {
    return vueRouter(
        [
            {path: "/executions/:namespace/:flowId/:id/:tab?", name: "executions/update", component: {template: "<div/>"}},
            {path: "/flows/edit/:namespace/:id/:tab?", name: "flows/update/edit", component: {template: "<div/>"}},
        ],
        {initialRoute: "/executions/company.team/orders-pipeline/exec-failed"},
    )
}

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
    decorators: [editFlowRouterDecorator()],
    args: {
        execution: execution("FAILED", [
            taskRun("tr-1", "extract", "SUCCESS", "2025-01-01T00:00:00Z"),
            taskRun("tr-2", "transform", "FAILED", "2025-01-01T00:00:05Z"),
        ]),
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        await waitFor(() => expect(canvas.getByRole("button", {name: /Debug this failure/})).toBeVisible())
        await userEvent.click(canvas.getByRole("button", {name: /Debug this failure/}))
        await waitFor(() => expect(canvas.getByRole("region")).toBeVisible())
        await expect(canvasElement.querySelector(".failure-switcher")).toBeNull()
        await expect(canvasElement.querySelector(".failure-debug-panel__subtitle")?.textContent).toContain("transform")
        await waitFor(() => expect(canvasElement.textContent).toContain("transforming"))
        await waitFor(() => expect(canvasElement.textContent).toContain("us-east-1"))
        await waitFor(() => expect(canvasElement.textContent).toContain("48203 rows"))

        const stateHistoryTab = canvas.getByRole("tab", {name: "State history"})
        const inputsOutputsTab = canvas.getByRole("tab", {name: "Inputs & outputs"})
        await expect(stateHistoryTab).toHaveAttribute("aria-selected", "true")
        await userEvent.click(inputsOutputsTab)
        await waitFor(() => expect(inputsOutputsTab).toHaveAttribute("aria-selected", "true"))
        await expect(stateHistoryTab).toHaveAttribute("aria-selected", "false")

        await waitFor(() => expect(canvas.getAllByRole("button", {name: "View task definition"})).toHaveLength(1))
        await userEvent.click(canvas.getByRole("button", {name: "View task definition"}))
        await waitFor(() => expect(canvasElement.textContent).toContain("message: extracting"))
        await userEvent.click(canvas.getByRole("button", {name: "Hide task definition"}))
        await waitFor(() => expect(canvasElement.textContent).not.toContain("message: extracting"))
    },
}

export const MultipleFailures: Story = {
    decorators: [editFlowRouterDecorator()],
    args: {
        execution: execution("KILLED", [
            taskRun("tr-1", "extract", "SUCCESS", "2025-01-01T00:00:00Z"),
            taskRun("tr-2", "transform", "FAILED", "2025-01-01T00:00:10Z"),
            taskRun("tr-3", "load", "KILLED", "2025-01-01T00:00:05Z", "tr-1"),
        ]),
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        await waitFor(() => expect(canvas.getByRole("button", {name: /Debug this failure/})).toBeVisible())
        await userEvent.click(canvas.getByRole("button", {name: /Debug this failure/}))
        await waitFor(() => expect(canvasElement.querySelector(".failure-switcher")).toBeVisible())
        await expect(canvasElement.querySelector(".failure-debug-panel__subtitle")?.textContent).toContain("load")
        await expect(canvasElement.querySelectorAll(".failure-switcher [role=\"tab\"]")).toHaveLength(2)
    },
}

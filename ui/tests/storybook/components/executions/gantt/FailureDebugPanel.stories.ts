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
    // The reopen trigger is placed by the caller (Gantt.vue puts it next to "Copy All Logs")
    // rather than rendered by the panel itself, so the story has to be the caller here too.
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
            // The real backend parses the YAML source server-side into a structured `inputs`
            // array alongside `source` — the mock has to supply both, since nothing here parses
            // FLOW_SOURCE itself.
            "GET /flows/company.team/orders-pipeline": {source: FLOW_SOURCE, inputs: [{id: "region", type: "STRING"}]},
            // A `{{ inputs.<id> }}` expression resolves to a canned value; everything else (the
            // resolved-config task block) echoes back unchanged — good enough for a story, where
            // the point is showing each card renders, not the display-renderer's own masking
            // logic (already covered by ExpressionControllerTest on the backend). context.body is
            // the raw request text, not a parsed object — the mock fetch layer hands it through as-is.
            "POST /expressions/render": (context: {body?: unknown}) => {
                const {expressions} = JSON.parse((context.body as string) ?? "{}") as {expressions?: string[]}
                const rendered: Record<string, string> = {}
                for (const expression of expressions ?? []) {
                    const inputMatch = /^\{\{ inputs\.(\w+) \}\}$/.exec(expression)
                    rendered[expression] = inputMatch ? "us-east-1" : expression
                }
                return {rendered}
            },
            // The "extract" task's own task run id (tr-1) is what "transform" (SingleFailure's
            // focused task) references via outputs.extract — used by "Outputs consumed".
            "GET /outputs/tasks/exec-failed/tr-1": {value: "48203 rows"},
        })
    },
}
export default meta
type Story = StoryObj<typeof FailureDebugPanel>

// The panel reads route.params for its Edit flow shortcut (namespace/flowId) and resolves the
// "flows/update/edit" route via useLink/router-link — both need an active matched route, not
// just the route registered. Must be a per-story decorator (not meta-level) to actually take
// precedence over the global preview router, and a fresh call per story rather than a shared
// decorator reference.
function editFlowRouterDecorator() {
    return vueRouter(
        [
            {path: "/executions/:namespace/:flowId/:id/:tab?", name: "executions/update", component: {template: "<div/>"}},
            {path: "/flows/edit/:namespace/:id/:tab?", name: "flows/update/edit", component: {template: "<div/>"}},
        ],
        {initialRoute: "/executions/company.team/orders-pipeline/exec-failed"},
    )
}

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
    decorators: [editFlowRouterDecorator()],
    args: {
        execution: execution("FAILED", [
            taskRun("tr-1", "extract", "SUCCESS", "2025-01-01T00:00:00Z"),
            taskRun("tr-2", "transform", "FAILED", "2025-01-01T00:00:05Z"),
        ]),
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        // Starts closed; the reopen affordance is the only entry point (no auto-open takeover).
        await waitFor(() => expect(canvas.getByRole("button", {name: /Debug this failure/})).toBeVisible())
        await userEvent.click(canvas.getByRole("button", {name: /Debug this failure/}))
        await waitFor(() => expect(canvas.getByRole("region")).toBeVisible())
        // No failure switcher for a single failure. The context card's own KsTabs also renders a
        // role="tablist", so this checks the switcher specifically rather than a bare role query.
        await expect(canvasElement.querySelector(".failure-switcher")).toBeNull()
        await expect(canvasElement.querySelector(".failure-debug-panel__subtitle")?.textContent).toContain("transform")
        await waitFor(() => expect(canvasElement.textContent).toContain("transforming"))
        // Execution inputs: the flow declares "region", resolved via the mocked render endpoint.
        await waitFor(() => expect(canvasElement.textContent).toContain("us-east-1"))
        // Outputs consumed: "transform" (the focused task) references outputs.extract in its
        // own config, so "extract"'s task run outputs should be pulled in and shown.
        await waitFor(() => expect(canvasElement.textContent).toContain("48203 rows"))

        // State history, resolved configuration, and inputs & outputs (execution inputs +
        // outputs consumed share one tab) are grouped as tabs of one card rather than four
        // separate always-visible cards; switching actually changes the active tab.
        const stateHistoryTab = canvas.getByRole("tab", {name: "State history"})
        const inputsOutputsTab = canvas.getByRole("tab", {name: "Inputs & outputs"})
        await expect(stateHistoryTab).toHaveAttribute("aria-selected", "true")
        await userEvent.click(inputsOutputsTab)
        await waitFor(() => expect(inputsOutputsTab).toHaveAttribute("aria-selected", "true"))
        await expect(stateHistoryTab).toHaveAttribute("aria-selected", "false")

        // Structural impact: "extract" (a sibling of the focused "transform" task) offers its
        // raw, unresolved task definition — not the focused task itself, which already has its
        // own "Resolved configuration" tab. The flow source was already fetched above, so the
        // toggle for the one eligible neighbor is present by now.
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
        // "load" failed at :05s, before "transform" at :10s — it is auto-focused first.
        await expect(canvasElement.querySelector(".failure-debug-panel__subtitle")?.textContent).toContain("load")
        // Scoped to the switcher: the context card's own KsTabs also renders role="tab" elements.
        await expect(canvasElement.querySelectorAll(".failure-switcher [role=\"tab\"]")).toHaveLength(2)
    },
}

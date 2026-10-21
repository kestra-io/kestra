import {defineComponent, ref} from "vue"
import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, userEvent, waitFor, within} from "storybook/test"
import {vueRouter} from "storybook-vue3-router"
import type {FlowForExecution} from "@kestra-io/kestra-sdk"
import {mockStoryApiRoutes} from "../../../../.storybook/apiMock"
import {useExecutionsStore, type Execution} from "../../../stores/executions"
import type {ExecutionTaskRun} from "../executionTaskRun"
import GanttPanelTaskRun from "./GanttPanelTaskRun.vue"
import {createTaskRunNode, provideGanttTree} from "./ganttTree"

const NAMESPACE = "company.team"
const FLOW_ID = "orders-pipeline"
const EXECUTION_ID = "exec-failed"

const FLOW_SOURCE = `id: ${FLOW_ID}
namespace: ${NAMESPACE}

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

function taskRun(id: string, taskId: string, state: string, startDate: string, extra: Record<string, unknown> = {}) {
    return {
        id,
        taskId,
        namespace: NAMESPACE,
        flowId: FLOW_ID,
        executionId: EXECUTION_ID,
        state: {current: state, histories: [{state: "RUNNING", date: startDate}, {state, date: startDate}]},
        ...extra,
    } as unknown as ExecutionTaskRun
}

const extract = taskRun("tr-1", "extract", "SUCCESS", "2025-01-01T00:00:00Z")
const transform = taskRun("tr-2", "transform", "FAILED", "2025-01-01T00:00:05Z", {
    attempts: [
        {state: {current: "FAILED", histories: [{state: "RUNNING", date: "2025-01-01T00:00:05Z"}, {state: "FAILED", date: "2025-01-01T00:00:06Z"}]}, workerId: "worker-a1b2"},
        {state: {current: "FAILED", histories: [{state: "RUNNING", date: "2025-01-01T00:00:20Z"}, {state: "FAILED", date: "2025-01-01T00:00:21Z"}]}, workerId: "worker-c3d4"},
    ],
})

const execution = {
    id: EXECUTION_ID,
    namespace: NAMESPACE,
    flowId: FLOW_ID,
    flowRevision: 1,
    state: {current: "FAILED", histories: []},
    taskRunList: [extract, transform],
} as unknown as Execution

const PanelHost = defineComponent({
    components: {GanttPanelTaskRun},
    props: {taskRunId: {type: String, required: true}},
    setup(props) {
        const executionsStore = useExecutionsStore()
        executionsStore.execution = execution
        executionsStore.flow = {id: FLOW_ID, namespace: NAMESPACE, tasks: [], disabled: false, draft: false, deleted: false} as FlowForExecution
        provideGanttTree({compact: ref(false), levelFilter: ref(undefined)})
        const run = execution.taskRunList?.find(candidate => candidate.id === props.taskRunId) as ExecutionTaskRun
        return {node: createTaskRunNode(execution, run, undefined, [])}
    },
    template: "<div style='width: 32rem; height: 40rem; overflow: auto'><GanttPanelTaskRun :node='node' /></div>",
})

const meta: Meta<typeof PanelHost> = {
    title: "Components/Executions/Gantt/GanttPanelTaskRun",
    component: PanelHost,
    decorators: [
        vueRouter(
            [
                {path: "/executions/:namespace/:flowId/:id/:tab?", name: "executions/update", component: {template: "<div/>"}},
                {path: "/flows/edit/:namespace/:id/:tab?", name: "flows/update/edit", component: {template: "<div/>"}},
            ],
            {initialRoute: `/executions/${NAMESPACE}/${FLOW_ID}/${EXECUTION_ID}`},
        ),
    ],
    parameters: {
        docs: {
            description: {
                component: "Body of the Gantt run panel for a task run: the error summary and actions for a failed one, then Logs, Attempts, Configuration, Inputs & outputs and Structure tabs whose data loads when a tab is first opened.",
            },
        },
    },
    beforeEach() {
        mockStoryApiRoutes({
            "GET /logs/exec-failed": [
                {level: "INFO", message: "transforming 48203 rows", timestamp: "2025-01-01T00:00:05Z"},
                {level: "ERROR", message: "Connection refused: warehouse.internal:5432", timestamp: "2025-01-01T00:00:06Z"},
            ],
            "GET /flows/company.team/orders-pipeline": {source: FLOW_SOURCE, inputs: [{id: "region", type: "STRING"}]},
            "POST /expressions/render": (context: {body?: unknown}) => {
                const {expressions} = JSON.parse((context.body as string) ?? "{}") as {expressions?: string[]}
                const rendered: Record<string, string> = {}
                for (const expression of expressions ?? []) {
                    rendered[expression] = /^\{\{ inputs\.(\w+) \}\}$/.test(expression) ? "us-east-1" : expression
                }
                return {rendered}
            },
            "GET /outputs/tasks/exec-failed/tr-1": {value: "48203 rows"},
        })
    },
}
export default meta
type Story = StoryObj<typeof meta>

export const FailedTaskRun: Story = {
    args: {taskRunId: "tr-2"},
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        await waitFor(() => expect(canvasElement.querySelector(".failure-error-summary__text")?.textContent).toContain("Connection refused: warehouse.internal:5432"))
        await expect(canvas.getByRole("button", {name: "Replay"})).toBeVisible()

        await userEvent.click(canvas.getByRole("tab", {name: "Attempts"}))
        await waitFor(() => expect(canvasElement.querySelectorAll(".failure-attempts__row")).toHaveLength(2))
        await expect(canvasElement.textContent).toContain("worker-c3d4")

        await userEvent.click(canvas.getByRole("tab", {name: "Configuration"}))
        await waitFor(() => expect(canvasElement.textContent).toContain("transforming"))

        await userEvent.click(canvas.getByRole("tab", {name: "Inputs & outputs"}))
        await waitFor(() => expect(canvasElement.textContent).toContain("us-east-1"))
        await waitFor(() => expect(canvasElement.textContent).toContain("48203 rows"))

        await userEvent.click(canvas.getByRole("tab", {name: "Structure"}))
        await waitFor(() => expect(canvas.getAllByRole("button", {name: "View task definition"})).toHaveLength(1))
    },
}

export const SucceededTaskRun: Story = {
    args: {taskRunId: "tr-1"},
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        await expect(canvas.getByRole("tab", {name: "Logs"})).toBeVisible()
        await expect(canvasElement.querySelector(".failure-error-summary__text")).toBeNull()
        await expect(canvas.queryByRole("button", {name: "Replay"})).toBeNull()
    },
}

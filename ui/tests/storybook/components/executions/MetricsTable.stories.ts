import type {Meta, StoryObj} from "@storybook/vue3"
import {expect, waitFor, within} from "storybook/test"
import {vueRouter} from "storybook-vue3-router"

import {mockStoryApiRoutes} from "../../../../.storybook/apiMock"
import MetricsTable from "../../../../src/components/executions/MetricsTable.vue"
import type {Execution} from "../../../../src/stores/executions"

const EXECUTION_ID = "metrics-execution"
const METRIC = {
    tenant: "main",
    namespace: "company.team",
    flowId: "metrics",
    taskId: "metrics",
    executionId: EXECUTION_ID,
    taskRunId: "metrics-task-run",
    type: "counter",
    name: "counter",
    value: 7,
    timestamp: "2025-01-01T00:00:00Z",
    tags: {},
}

const meta: Meta<typeof MetricsTable> = {
    title: "Components/Executions/MetricsTable",
    component: MetricsTable,
    decorators: [
        vueRouter([
            {path: "/", component: {template: "<div />"}},
            {
                path: "/:tenant?/flows/edit/:namespace/:id/metrics",
                name: "flows/update/metrics",
                component: {template: "<div />"},
            },
        ]),
    ],
    beforeEach() {
        localStorage.removeItem("columns_execution-metrics")
        localStorage.removeItem("ks-column-order-v2-execution-metrics")
        mockStoryApiRoutes({
            [`GET /metrics/${EXECUTION_ID}`]: {results: [METRIC], total: 1},
        })
    },
}

export default meta
type Story = StoryObj<typeof meta>

export const OneActionPerMetric: Story = {
    args: {
        execution: {id: EXECUTION_ID} as Execution,
        showTask: true,
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement)

        await waitFor(() => expect(canvas.getByText("counter")).toBeVisible())
        expect(canvasElement.querySelectorAll("button[aria-label=\"View metrics\"]")).toHaveLength(1)
    },
}

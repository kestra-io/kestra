import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, waitFor, within} from "storybook/test"
import {vueRouter} from "storybook-vue3-router"

import {mockStoryApiRoutes} from "../../../.storybook/apiMock"
import TaskRunPopup from "./TaskRunPopup.vue"
import {useExecutionsStore, type Execution} from "../../stores/executions"

const EXECUTION_ID = "popup-execution"

function executionWith(taskRunState: string, attempts: number) {
    return {
        id: EXECUTION_ID,
        namespace: "company.team",
        flowId: "dbt_flow",
        flowRevision: 1,
        state: {current: taskRunState === "RUNNING" ? "RUNNING" : "SUCCESS", histories: []},
        taskRunList: [{
            id: "popup-task-run",
            taskId: "dbt_cloud_job",
            state: {current: taskRunState, histories: [], duration: "PT2S"},
            attempts: Array.from({length: attempts}, () => ({state: {current: taskRunState}})),
        }],
    } as unknown as Execution
}

const meta: Meta<typeof TaskRunPopup> = {
    title: "Components/Executions/TaskRunPopup",
    component: TaskRunPopup,
    decorators: [
        vueRouter([{path: "/", component: {template: "<div />"}}]),
        (_story: unknown, context: {args: {execution: Execution}}) => ({
            setup() {
                useExecutionsStore().execution = context.args.execution
            },
            template: "<story />",
        }),
    ],
    beforeEach() {
        mockStoryApiRoutes({
            [`GET /metrics/${EXECUTION_ID}`]: {results: [], total: 5},
            "GET /logs/search": {results: [], total: 14, type: "OFFSET"},
            [`GET /outputs/tasks/${EXECUTION_ID}/popup-task-run`]: {runId: 512994, manifest: "kestra:///manifest.json"},
        })
    },
    args: {
        modelValue: true,
        taskId: "dbt_cloud_job",
        taskType: "io.kestra.plugin.dbt.cloud.TriggerRun",
        description: "Triggers the nightly dbt Cloud job and waits for it to finish.",
    },
}

export default meta
type Story = StoryObj<typeof meta>

export const Succeeded: Story = {
    args: {execution: executionWith("SUCCESS", 1), initialTab: "outputs"},
    async play() {
        const body = within(document.body)

        await waitFor(() => expect(body.getByText("Success")).toBeVisible())
        expect(body.getByText("Attempt 1")).toBeVisible()
        expect(body.getByText("Interrupt").closest("button")).toBeDisabled()
        await waitFor(() => expect(document.body.querySelector("[data-test='task-run-popup-tab-outputs'] [data-test='tab-count']")?.textContent?.trim()).toBe("2"))
    },
}

export const RetriedAndRunning: Story = {
    args: {execution: executionWith("RUNNING", 2), initialTab: "logs"},
    async play() {
        await waitFor(() => expect(document.body.querySelector("[data-test='task-run-popup-attempt']")?.textContent?.trim()).toBe("Attempt 2"))
    },
}

export const NoArtifacts: Story = {
    args: {execution: executionWith("SUCCESS", 1), initialTab: "artifacts"},
    async play() {
        await waitFor(() => expect(document.body.querySelector("[data-test='task-run-popup-artifacts-empty']")).not.toBeNull())
    },
}

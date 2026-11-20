import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, waitFor, within} from "storybook/test"
import {vueRouter} from "storybook-vue3-router"
import type {FlowForExecution, StateType, TaskRun} from "@kestra-io/kestra-sdk"
import {useExecutionsStore, type Execution} from "../../stores/executions"
import Gantt from "./Gantt.vue"

const WORKING_DIRECTORY = "io.kestra.plugin.core.flow.WorkingDirectory"
const LOG = "io.kestra.plugin.core.log.Log"
const FLOW: FlowForExecution = {
    id: "wd-postexecute-failure",
    namespace: "company.team",
    disabled: false,
    draft: false,
    deleted: false,
    tasks: [
        {id: "wd", type: WORKING_DIRECTORY, tasks: [{id: "s1", type: LOG}]},
        {id: "after", type: LOG},
    ],
    errors: [{id: "onError", type: LOG}],
}

function taskRun(taskId: string, current: StateType, start: number, end: number, parentTaskRunId?: string): TaskRun {
    const startDate = new Date(Date.UTC(2026, 10, 17, 0, 0, 0, start)).toISOString()
    const endDate = new Date(Date.UTC(2026, 10, 17, 0, 0, 0, end)).toISOString()
    return {
        id: `run-${taskId}`, executionId: "execution", namespace: FLOW.namespace, flowId: FLOW.id,
        taskId, parentTaskRunId,
        state: {current, startDate, endDate, histories: [{state: "RUNNING", date: startDate}, {state: current, date: endDate}]},
    }
}

const TASK_RUNS = [
    taskRun("wd", "FAILED", 10, 450),
    taskRun("wd_open", "SUCCESS", 120, 210, "run-wd"),
    taskRun("s1", "SUCCESS", 270, 300, "run-wd"),
    taskRun("wd_close", "FAILED", 340, 390, "run-wd"),
    taskRun("onError", "SUCCESS", 460, 480),
]

const meta: Meta<typeof Gantt> = {
    title: "Components/Executions/Gantt/WorkingDirectory",
    component: Gantt,
    parameters: {layout: "fullscreen"},
    decorators: [
        () => ({
            setup() {
                const store = useExecutionsStore()
                store.flow = FLOW
                store.execution = {
                    id: "execution", namespace: FLOW.namespace, flowId: FLOW.id,
                    state: {...TASK_RUNS[0].state, startDate: "2026-11-17T00:00:00.000Z", endDate: "2026-11-17T00:00:00.520Z"},
                    taskRunList: TASK_RUNS,
                } as Execution
            },
            template: "<div style='height: 100vh'><story /></div>",
        }),
        vueRouter([{path: "/", name: "home", component: {template: "<div/>"}}]),
    ],
    args: {showBanner: false},
}

export default meta
type Story = StoryObj<typeof meta>;

export const LifecycleIcons: Story = {
    play: async ({canvasElement}) => {
        const row = (id: string) => canvasElement.querySelector<HTMLElement>(`[data-row-key="tr:run-${id}"]`)!
        await waitFor(() => expect(row("wd_close")).toBeInTheDocument())
        for (const id of ["wd", "wd_open", "wd_close"]) {
            await expect(within(row(id)).getByRole("img", {name: WORKING_DIRECTORY})).toBeVisible()
        }
        await expect(within(row("s1")).getByRole("img", {name: LOG})).toBeVisible()
        await expect(within(row("onError")).getByRole("img", {name: LOG})).toBeVisible()
        const labelLeft = (id: string) => row(id).querySelector("[data-test='gantt-task-name']")!.getBoundingClientRect().left
        for (const id of ["wd_open", "wd_close"]) {
            await expect(Math.abs(labelLeft(id) - labelLeft("s1"))).toBeLessThanOrEqual(1)
        }
    },
}

export const LifecycleIconsDark: Story = {
    ...LifecycleIcons,
    parameters: {themes: {themeOverride: "dark"}},
    play: async (context) => {
        await waitFor(() => expect(document.documentElement).toHaveClass("dark"))
        await LifecycleIcons.play?.(context)
    },
}

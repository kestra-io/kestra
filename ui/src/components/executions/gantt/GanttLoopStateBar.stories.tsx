import {onBeforeUnmount, ref} from "vue";
import {expect, waitFor} from "storybook/test";
import {vueRouter} from "storybook-vue3-router";
import {configureClient} from "@kestra-io/kestra-sdk";
import type {Meta, StoryObj} from "@storybook/vue3-vite";
import GanttRow from "./GanttRow.vue";
import GanttTaskLabel from "./GanttTaskLabel.vue";
import GanttLoopChips from "./GanttLoopChips.vue";
import GanttLoopStateBar from "./GanttLoopStateBar.vue";
import type {ExecutionTaskRun} from "../executionTaskRun";
import {LOOP_TYPE, createTaskRunNode, loopAncestors, loopRefOf, provideGanttTree, type GanttTree, type TaskRunNode} from "./ganttTree";

const outputs = {
    byTaskRun: {} as Record<string, Record<string, unknown>>,
}

const TASK_RUN_OUTPUTS_URL = /\/outputs\/tasks\/[^/]+\/([^/?]+)/

function stubTaskRunOutputs() {
    const realFetch = globalThis.fetch.bind(globalThis)
    configureClient({
        fetch: (input: URL | RequestInfo, init?: RequestInit) => {
            const url = input instanceof Request ? input.url : String(input)
            const taskRunId = TASK_RUN_OUTPUTS_URL.exec(url)?.[1]
            if (!taskRunId) return realFetch(input, init)
            return Promise.resolve(new Response(JSON.stringify(outputs.byTaskRun[taskRunId] ?? {}), {
                status: 200,
                headers: {"Content-Type": "application/json"},
            }))
        },
    })
    onBeforeUnmount(() => configureClient({fetch: realFetch}))
}

let renderedTree: GanttTree | undefined

const EXECUTION = {id: "root-execution", namespace: "company.team", flowId: "monthly_close"}

function loopTaskRun(id: string, taskId: string, state: string): ExecutionTaskRun {
    return {
        id,
        taskId,
        state: {current: state, histories: [{state: "CREATED", date: "2026-09-30T01:45:58Z"}, {state, date: "2026-09-30T01:45:59Z"}]},
    } as unknown as ExecutionTaskRun
}

function loopNode(id: string, taskId: string, state: string, ancestors: TaskRunNode["ancestors"] = []): TaskRunNode {
    return createTaskRunNode(EXECUTION, loopTaskRun(id, taskId, state), LOOP_TYPE, ancestors)
}

const rowStyle = {
    "--gantt-offset-width": "5rem",
    "--gantt-bar-width": "12rem",
    "--gantt-duration-width": "9rem",
    "--gantt-state-width": "8rem",
    width: "60rem",
}

function renderRows(nodes: TaskRunNode[], compact = false) {
    return {
        setup() {
            const tree = provideGanttTree({compact: ref(compact), levelFilter: ref(undefined)})
            renderedTree = tree
            nodes.forEach((node) => {
                const handle = tree.loopHandle(loopRefOf(node))
                const counts = outputs.byTaskRun[node.taskRun.id]
                handle.totalCount.value = counts?.iterationCount as number | undefined
                handle.failedCount.value = (counts?.terminatedIterations as Record<string, number> | undefined)?.FAILED ?? 0
            })
            return () => (
                <div style={compact ? {...rowStyle, width: "24rem"} : rowStyle} data-test="story-rows">
                    {nodes.map((node) => (
                        <GanttRow
                            key={node.key}
                            nodeKey={node.key}
                            label={node.taskRun.taskId}
                            ancestors={node.ancestors}
                            offset="0ms"
                            barStyle={{left: "0%", width: "60%", background: "var(--ks-status-running)"}}
                            state={node.taskRun.state.current}
                            selected={tree.selected.value?.key === node.key}
                            expandable
                            v-slots={{
                                label: () => (
                                    <GanttTaskLabel name={node.taskRun.taskId} taskType={LOOP_TYPE}>
                                        <GanttLoopChips loop={loopRefOf(node)} state={node.taskRun.state.current} nodeKey={node.key} />
                                    </GanttTaskLabel>
                                ),
                                "label-footer": () => <GanttLoopStateBar node={node} />,
                            }}
                        />
                    ))}
                </div>
            )
        },
    }
}

const waitForBars = (canvasElement: HTMLElement, count: number) =>
    waitFor(() => expect(canvasElement.querySelectorAll("[data-test='loop-state-bar']")).toHaveLength(count))

const meta: Meta = {
    title: "Components/Executions/Gantt/GanttLoopStateBar",
    parameters: {layout: "padded", docs: {disable: true}},
    decorators: [
        () => ({
            setup: stubTaskRunOutputs,
            template: "<story />",
        }),
        vueRouter([{path: "/", name: "home", component: {template: "<div/>"}}], {initialRoute: "/"}),
    ],
}

export default meta

type Story = StoryObj

export const RunningWithNotStartedRemainder: Story = {
    render: () => {
        outputs.byTaskRun = {
            "loop-running": {iterationCount: 5000, runningIterations: 12, terminatedIterations: {SUCCESS: 4812, FAILED: 3}},
        }
        return renderRows([loopNode("loop-running", "per_customer", "RUNNING")])
    },
    play: async ({canvasElement}) => {
        await waitForBars(canvasElement, 1)

        const bar = canvasElement.querySelector("[data-test='loop-state-bar']")!
        expect(bar.getAttribute("aria-label")).toBe("4,812 SUCCESS, 3 FAILED, 12 RUNNING, 173 not started")
    },
}

export const TerminalAndNested: Story = {
    render: () => {
        outputs.byTaskRun = {
            "loop-outer": {iterationCount: 5000, runningIterations: 0, terminatedIterations: {SUCCESS: 4997, FAILED: 3}},
            "loop-inner": {iterationCount: 40, runningIterations: 0, terminatedIterations: {SUCCESS: 40}},
        }
        const outer = loopNode("loop-outer", "per_customer", "FAILED")
        const inner = loopNode("loop-inner", "per_region", "SUCCESS", loopAncestors(outer))
        return renderRows([outer, inner])
    },
    play: async ({canvasElement}) => {
        await waitForBars(canvasElement, 2)

        const labels = [...canvasElement.querySelectorAll("[data-test='loop-state-bar']")].map((bar) => bar.getAttribute("aria-label"))
        expect(labels).toEqual(["4,997 SUCCESS, 3 FAILED", "40 SUCCESS"])
    },
}

export const Compact: Story = {
    render: () => {
        outputs.byTaskRun = {
            "loop-compact": {iterationCount: 5000, runningIterations: 12, terminatedIterations: {SUCCESS: 4812, FAILED: 3}},
        }
        return renderRows([loopNode("loop-compact", "per_customer", "RUNNING")], true)
    },
    play: async ({canvasElement}) => {
        await waitForBars(canvasElement, 1)
    },
}

export const OldExecutionWithoutCounts: Story = {
    render: () => {
        outputs.byTaskRun = {"loop-old": {}}
        return renderRows([loopNode("loop-old", "per_customer", "SUCCESS")])
    },
    play: async ({canvasElement}) => {
        await waitFor(() => expect(renderedTree?.loopOutputs("loop-old")).toBeDefined())

        expect(canvasElement.querySelector("[data-test='loop-state-bar']")).toBeNull()
    },
}

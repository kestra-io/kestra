import {ref} from "vue";
import {userEvent, waitFor} from "storybook/test";
import {vueRouter} from "storybook-vue3-router";
import type {Meta, StoryObj} from "@storybook/vue3-vite";
import {mockStoryApiRoutes, type ApiRouteContext} from "../../../.storybook/apiMock";
import LoopIterationTree from "./LoopIterationTree.vue";
import type {ExecutionTaskRun} from "./executionTaskRun";
import {LOOP_TYPE, createTaskRunNode, provideGanttTree} from "./gantt/ganttTree";

function iterationRow(index: number, value: string) {
    return {
        id: `iteration-${value}`,
        namespace: "company.team",
        flowId: "nested_loop_demo",
        state: {current: "SUCCESS", startDate: "2026-09-30T01:45:58Z", endDate: "2026-09-30T01:45:59Z"},
        loopRun: {taskId: "per_region", taskRunId: "tr1", index, value, parents: []},
    }
}

function mockIterationRoutes(total: number) {
    mockStoryApiRoutes({
        "GET /executions/search": ({query}: ApiRouteContext) => {
            const page = Number(query.get("page") ?? 1)
            const start = (page - 1) * 10
            const limit = Math.min(start + 10, total)

            const results = Array.from({length: Math.max(0, limit - start)}, (_, i) => {
                const index = start + i;
                return iterationRow(index, `REGION-${index}`);
            });

            return {results, total}
        },
        "GET /executions/:executionId": {
            id: "iteration-EMEA",
            namespace: "company.team",
            flowId: "nested_loop_demo",
            taskRunList: [
                {
                    id: "tr-log",
                    taskId: "quarter_log",
                    state: {current: "SUCCESS"},
                    attempts: [{state: {current: "SUCCESS", startDate: "2026-09-30T01:45:58Z", endDate: "2026-09-30T01:45:58Z", duration: 10}}],
                },
            ],
        },
    })
}

const loopTaskRun = {
    id: "tr1",
    taskId: "per_region",
    state: {current: "SUCCESS", histories: [{state: "CREATED", date: "2026-09-30T01:45:58Z"}, {state: "SUCCESS", date: "2026-09-30T01:45:59Z"}]},
} as unknown as ExecutionTaskRun;

const loopNode = createTaskRunNode(
    {id: "root-execution", namespace: "company.team", flowId: "nested_loop_demo"},
    loopTaskRun,
    LOOP_TYPE,
    [],
);

const renderTree = (total: number) => ({
    setup() {
        mockIterationRoutes(total)
        provideGanttTree({compact: ref(false), levelFilter: ref(undefined)})
        return () => <LoopIterationTree node={loopNode} />
    }
});

const meta: Meta<typeof LoopIterationTree> = {
    title: "Components/Executions/LoopIterationTree",
    component: LoopIterationTree,
    parameters: {
        docs: {disable: true},
    },
    decorators: [
        vueRouter([
            {
                path: "/",
                name: "home",
                component: {template: "<div>home</div>"},
            },
            {
                path: "/executions/:id?/:flowId?",
                name: "executions/list",
                component: {template: "<div>executions</div>"},
            },
        ], {
            initialRoute: "/",
        }),
    ],
}

export default meta;

// Under the 10-item page size, so no preview footer appears.
export const Default: StoryObj<{total: number}> = {
    render: (args) => renderTree(args.total),
    args: {
        total: 3,
    },
    play: async ({canvasElement}) => {
        await waitFor(() => {
            const rows = canvasElement.querySelectorAll('[data-test="loop-iteration-row"]')
            if (rows.length !== 3) throw new Error(`expected 3 iteration rows, got ${rows.length}`)
        })
    },
}

// Over the page size — the preview footer appears.
export const OverThePreviewLimit: StoryObj<{total: number}> = {
    render: (args) => renderTree(args.total),
    args: {
        total: 12,
    },
    play: async ({canvasElement}) => {
        await waitFor(() => {
            const rows = canvasElement.querySelectorAll('[data-test="loop-iteration-row"]')
            if (rows.length !== 10) throw new Error(`expected 10 iteration rows on page 1, got ${rows.length}`)
        })

        // Waited for separately: KsButton's data-test attribute can land a render tick late.
        let loadMoreButton: HTMLElement | null = null
        await waitFor(() => {
            loadMoreButton = canvasElement.querySelector('[data-test="loop-load-more"]') as HTMLElement | null
            if (!loadMoreButton) throw new Error("load-more button not yet rendered")
        })
        if (loadMoreButton!.textContent?.trim() !== "Load 10 more") {
            throw new Error(`Load-more button text mismatch: "${loadMoreButton!.textContent}"`)
        }

        await userEvent.click(loadMoreButton!)

        await waitFor(() => {
            const rows = canvasElement.querySelectorAll('[data-test="loop-iteration-row"]')
            if (rows.length !== 12) throw new Error(`expected 12 iteration rows after load more, got ${rows.length}`)
        })
    },
}

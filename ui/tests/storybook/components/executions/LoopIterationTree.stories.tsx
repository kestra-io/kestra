import {vi} from "vitest";
import {userEvent, waitFor} from "storybook/test";

// useLoopIterations (via executionsStore.findExecutions) and IterationTaskRuns both call their
// generated SDK submodule functions directly, which go through the SDK's own internal client
// rather than the axios instance setMockClient() swaps - so each is intercepted at the submodule
// level, the same way Triggers.stories.tsx mocks searchTriggers.
const mockState = vi.hoisted(() => ({total: 3}))

function iterationRow(index: number, value: string) {
    return {
        id: `iteration-${value}`,
        namespace: "company.team",
        flowId: "nested_loop_demo",
        state: {current: "SUCCESS", startDate: "2026-09-30T01:45:58Z", endDate: "2026-09-30T01:45:59Z"},
        loopRun: {taskId: "per_region", taskRunId: "tr1", index, value, parents: []},
    }
}

vi.mock("@kestra-io/kestra-sdk/executions", () => ({
    searchExecutions: async (params: {page?: number}) => {
        const values = ["EMEA", "AMER", "APAC", "LATAM", "MENA", "SEA", "NORDICS", "BENELUX", "DACH", "ANZ", "SASIA", "CAN"]
        const page = params?.page ?? 1
        const start = (page - 1) * 10
        const results = values.slice(start, start + 10).map((v, i) => iterationRow(start + i, v))
        return {results, total: mockState.total}
    },
    execution: async () => ({
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
    }),
}))

import type {Meta, StoryFn, StoryObj} from "@storybook/vue3-vite";
import LoopIterationTree from "../../../../src/components/executions/LoopIterationTree.vue";

const meta: Meta<typeof LoopIterationTree> = {
    title: "Components/Executions/LoopIterationTree",
    component: LoopIterationTree,
}

export default meta;

const Template: StoryFn<{total: number}> = (args) => ({
    setup() {
        mockState.total = args.total

        return () =>
            <LoopIterationTree
                executionId="root-execution"
                taskId="per_region"
                namespace="company.team"
                flowId="nested_loop_demo"
            />
    }
});

// Click "Iterations" to expand — under the 10-item page size, so no preview footer appears.
export const Default: StoryObj<{total: number}> = {
    render: Template,
    args: {
        total: 3,
    },
    play: async ({canvasElement}) => {
        const toggle = canvasElement.querySelector('[data-test="loop-iteration-toggle"]') as HTMLElement
        await userEvent.click(toggle)

        await waitFor(() => {
            const rows = canvasElement.querySelectorAll('[data-test="loop-iteration-row"]')
            if (rows.length !== 3) throw new Error(`expected 3 iteration rows, got ${rows.length}`)
        })
    },
}

// Click "Iterations" to expand — over the page size, so the preview footer (count, Load 10
// more, Show all executions) and the "Failed iterations only" filter both appear.
export const OverThePreviewLimit: StoryObj<{total: number}> = {
    render: Template,
    args: {
        total: 12,
    },
    play: async ({canvasElement}) => {
        const toggle = canvasElement.querySelector('[data-test="loop-iteration-toggle"]') as HTMLElement
        await userEvent.click(toggle)

        await waitFor(() => {
            const rows = canvasElement.querySelectorAll('[data-test="loop-iteration-row"]')
            if (rows.length !== 10) throw new Error(`expected 10 iteration rows on page 1, got ${rows.length}`)
        })

        // Asserts on the actual rendered English label — this is exactly the check that
        // would have caught the raw-key-id rendering bug from #19603's review.
        const loadMoreButton = canvasElement.querySelector('[data-test="loop-load-more"]') as HTMLElement
        if (!loadMoreButton || loadMoreButton.textContent?.trim() !== "Load 10 more") {
            throw new Error(`Load-more button text mismatch: "${loadMoreButton?.textContent}"`)
        }

        await userEvent.click(loadMoreButton)

        await waitFor(() => {
            const rows = canvasElement.querySelectorAll('[data-test="loop-iteration-row"]')
            if (rows.length !== 12) throw new Error(`expected 12 iteration rows after load more, got ${rows.length}`)
        })
    },
}

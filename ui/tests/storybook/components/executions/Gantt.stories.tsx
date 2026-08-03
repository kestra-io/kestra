import { vueRouter } from "storybook-vue3-router";
import type { Meta, StoryObj } from "@storybook/vue3";
import { within, expect, waitFor } from "storybook/test";
import { useExecutionsStore } from "../../../../src/stores/executions";
import { useFlowStore } from "../../../../src/stores/flow";
import Gantt from "../../../../src/components/executions/Gantt.vue";

const NAMESPACE = "company.team.qa";
const FLOW_ID = "qa_flow_concurrency";
const EXECUTION_ID = "12HqIIvMvw5K1k5Zksxgus";

// States the Gantt renders an empty view for (an execution with no task runs).
const STATE_OPTIONS = ["CREATED", "RUNNING", "PAUSED", "CANCELLED", "FAILED", "KILLED", "WARNING", "QUEUED"];

const FLOW = {
    id: FLOW_ID,
    namespace: NAMESPACE,
    tasks: [{ id: "hold", type: "io.kestra.plugin.core.flow.Sleep" }],
};

function executionWithState(current: string) {
    return {
        id: EXECUTION_ID,
        flowId: FLOW_ID,
        namespace: NAMESPACE,
        state: {
            current,
            histories: [
                { state: "CREATED", date: "2025-01-01T00:00:00.000Z" },
                { state: current, date: "2025-01-01T00:00:01.000Z" },
            ],
        },
        taskRunList: [],
    };
}

const ROUTER_ROUTES = [
    { path: "/", name: "home", component: { template: "<div/>" } },
    {
        path: "/executions/:namespace/:flowId/:id/:tab?",
        name: "executions/update",
        component: { template: "<div/>" },
    },
    {
        path: "/flows/edit/:namespace/:id/:tab?",
        name: "flows/update",
        component: { template: "<div/>" },
    },
];

type GanttStoryArgs = { state: string };

const meta = {
    title: "Components/Executions/Gantt",
    component: Gantt,
    parameters: { layout: "fullscreen" },
    argTypes: {
        state: {
            control: "select",
            options: STATE_OPTIONS,
            description:
                "Execution state the Gantt renders an empty view for (no task runs ran).",
        },
    },
    args: { state: "CANCELLED" },
    render: () => ({ components: { Gantt }, template: "<Gantt />" }),
    decorators: [
        (_story: unknown, context: { args: GanttStoryArgs }) => ({
            setup() {
                const state = context.args.state ?? "CANCELLED";

                const executionsStore = useExecutionsStore();
                executionsStore.execution = executionWithState(state) as any;
                executionsStore.flow = FLOW as any;

                const flowStore = useFlowStore();
                flowStore.flow = {
                    ...FLOW,
                    concurrency: { limit: 1, behavior: "QUEUE" },
                } as any;
            },
            template: "<div style='height: 100vh'><story /></div>",
        }),
        vueRouter(ROUTER_ROUTES, {
            initialRoute: `/executions/${NAMESPACE}/${FLOW_ID}/${EXECUTION_ID}`,
        }),
    ],
} satisfies Meta<GanttStoryArgs>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Created: Story = {
    args: { state: "CREATED" },
};

export const Cancelled: Story = {
    args: { state: "CANCELLED" },
};

export const Failed: Story = {
    args: { state: "FAILED" },
};

export const Queued: Story = {
    args: { state: "QUEUED" },
};

/** A progressing execution: the progress bar is shown above the (empty) Gantt. */
export const Running: Story = {
    args: { state: "RUNNING" },
    play: async ({ canvasElement }: { canvasElement: HTMLElement }) => {
        const canvas = within(canvasElement);
        await waitFor(() => expect(canvas.getByRole("progressbar")).toBeInTheDocument());
    },
};

/**
 * PAUSED satisfies State.isRunning() but is not progressing, so the progress bar must stay hidden —
 * otherwise an execution paused on a manual approval climbs to the bar's cap while nothing advances.
 */
export const Paused: Story = {
    args: { state: "PAUSED" },
    play: async ({ canvasElement }: { canvasElement: HTMLElement }) => {
        const canvas = within(canvasElement);
        // Wait for the status badge so the absence below is a real absence, not an unrendered Gantt.
        await waitFor(() => expect(canvas.getByText("Paused")).toBeInTheDocument());
        expect(canvas.queryByRole("progressbar")).toBeNull();
    },
};

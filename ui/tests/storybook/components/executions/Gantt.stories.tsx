import { vueRouter } from "storybook-vue3-router";
import type { Meta, StoryObj } from "@storybook/vue3";
import { useExecutionsStore } from "../../../../src/stores/executions";
import { useFlowStore } from "../../../../src/stores/flow";
import Gantt from "../../../../src/components/executions/Gantt.vue";

const NAMESPACE = "company.team.qa";
const FLOW_ID = "qa_flow_concurrency";
const EXECUTION_ID = "12HqIIvMvw5K1k5Zksxgus";

// States the Gantt renders an empty view for (an execution with no task runs).
const STATE_OPTIONS = ["CREATED", "CANCELLED", "FAILED", "KILLED", "WARNING", "QUEUED"];

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

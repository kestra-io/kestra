import {vueRouter} from "storybook-vue3-router";
import type {Meta, StoryObj} from "@storybook/vue3";
import {waitFor, within, userEvent, expect} from "storybook/test";

import {useExecutionsStore} from "../../../../src/stores/executions";
import ExecutionVariableExplorer from "../../../../src/components/executions/outputs/ExecutionVariableExplorer.vue";

/**
 * The explorer reads everything but task outputs straight from the active
 * execution in the executions store: `variables` → Variables, `trigger` →
 * Triggers, `inputs` → Inputs, `outputs` → Flow outputs. Task outputs are
 * fetched lazily from the backend (`/outputs/{id}`) and therefore only appear
 * against a live API — these stories exercise the store-sourced sections.
 */
const FAKE_EXECUTION = {
    id: "test-exec-id",
    flowId: "notify-customers",
    namespace: "company.team",
    state: {current: "SUCCESS", startDate: "2025-01-01T00:00:00Z", duration: "PT1S"},
    taskRunList: [],
    variables: {
        Api_endpoint: "http://api.kestra.io/v1",
        environment: {name: "production", region: "eu-west-1", tier: "gold"},
        allowedDomains: ["acme.io", "partner.io", "training.acme.io"],
        smtpHost: "smtp.acme.io",
        smtpPort: 587,
        replyTo: "noreply@acme.io",
        maxRetries: 3,
        featureFlags: {betaUi: true, newScheduler: false},
    },
    trigger: {
        id: "schedule",
        type: "io.kestra.plugin.core.trigger.Schedule",
        variables: {cron: "0 9 * * *", timezone: "UTC", next: "2025-01-02T09:00:00Z"},
    },
    inputs: {
        customerId: "cust-42",
        sendCopy: true,
    },
    outputs: {
        notifiedCount: 128,
        report: "kestra:///company/team/report.csv",
    },
};

const ROUTER_ROUTES = [
    {path: "/", name: "home", component: {template: "<div/>"}},
    {path: "/executions/:namespace/:flowId/:id/:tab?", name: "executions/update", component: {template: "<div/>"}},
    {path: "/flows/edit/:namespace/:id/:tab?", name: "flows/update", component: {template: "<div/>"}},
];

function makeDecorators() {
    return [
        () => ({
            setup() {
                const executionsStore = useExecutionsStore();
                executionsStore.execution = FAKE_EXECUTION as any;
            },
            template: "<div style='height:600px'><story /></div>",
        }),
        vueRouter(ROUTER_ROUTES, {initialRoute: "/executions/company.team/notify-customers/test-exec-id"}),
    ];
}

const meta: Meta<typeof ExecutionVariableExplorer> = {
    title: "Components/Executions/ExecutionVariableExplorer",
    component: ExecutionVariableExplorer,
    parameters: {layout: "fullscreen"},
    decorators: makeDecorators(),
};

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default view: the sidebar lists every context source. The Variables section
 * is open by default; selecting an item renders its value as a JSON tree in the
 * centre panel.
 */
export const Default: Story = {};

/**
 * Clicking a variable populates the centre tree view with its value.
 */
export const SelectVariable: Story = {
    play: async ({canvasElement}: {canvasElement: HTMLElement}) => {
        const canvas = within(canvasElement);

        // The Variables section is open by default — wait for an item to render.
        const item = await waitFor(() => canvas.getByText("environment"), {timeout: 5000});
        await userEvent.click(item);

        // The selected object should be expanded in the tree (its keys visible).
        // Tree keys render quoted (e.g. "region"), so match on a substring.
        await waitFor(
            () => {
                expect(canvas.getByText(/"region"/)).toBeTruthy();
            },
            {timeout: 3000},
        );
    },
};

/**
 * Typing in the search box filters items across every section by key or value.
 */
export const SearchFiltersItems: Story = {
    play: async ({canvasElement}: {canvasElement: HTMLElement}) => {
        const canvas = within(canvasElement);

        const search = await waitFor(
            () => canvas.getByPlaceholderText(/search key or value/i),
            {timeout: 5000},
        );
        await userEvent.type(search, "smtp");

        await waitFor(
            () => {
                expect(canvas.getByText("smtpHost")).toBeTruthy();
                // A non-matching variable must be filtered out.
                expect(canvas.queryByText("maxRetries")).toBeNull();
            },
            {timeout: 3000},
        );
    },
};

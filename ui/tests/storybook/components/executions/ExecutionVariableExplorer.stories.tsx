import {vueRouter} from "storybook-vue3-router";
import type {Meta, StoryObj} from "@storybook/vue3";
import {waitFor, within, userEvent, expect} from "storybook/test";

import {mockStoryApiRoutes} from "../../../../.storybook/apiMock";
import {useExecutionsStore} from "../../../../src/stores/executions";
import ExecutionVariableExplorer from "../../../../src/components/executions/outputs/ExecutionVariableExplorer.vue";

// Task-output payloads served through the fetch-layer API double (see meta.beforeEach below):
// `vi.mock("@kestra-io/kestra-sdk/outputs")` would be a silent no-op here, because the SDK is a
// pre-bundled dependency the vitest browser mocker cannot intercept.
const OUTPUTS_INFORMATION = [
    {taskId: "http_request", taskRunId: "run-http", value: null, iteration: null, inline: false},
    {taskId: "check_status", taskRunId: "run-check", value: null, iteration: null, inline: false},
];

const OUTPUTS_BY_TASK_RUN_ID: Record<string, Record<string, unknown>> = {
    "run-http": {code: 200, body: "healthy"},
    "run-check": {passed: true},
};

/**
 * The explorer reads everything but task outputs straight from the active
 * execution in the executions store: `variables` → Variables, `trigger` →
 * Triggers, `inputs` → Flow Inputs. Task outputs are fetched from the outputs
 * API, which is mocked here so stories can exercise the search flow.
 */
const FAKE_EXECUTION = {
    id: "test-exec-id",
    flowId: "notify-customers",
    namespace: "company.team",
    state: {current: "SUCCESS", startDate: "2025-01-01T00:00:00Z", duration: "PT1S"},
    taskRunList: [
        {id: "run-http", taskId: "http_request"},
        {id: "run-check", taskId: "check_status"},
    ],
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
    // Runs after the preview-level beforeEach has reset the previous story's routes.
    beforeEach() {
        mockStoryApiRoutes({
            [`GET /outputs/${FAKE_EXECUTION.id}`]: OUTPUTS_INFORMATION,
            [`GET /outputs/${FAKE_EXECUTION.id}/run-http`]: OUTPUTS_BY_TASK_RUN_ID["run-http"],
            [`GET /outputs/${FAKE_EXECUTION.id}/run-check`]: OUTPUTS_BY_TASK_RUN_ID["run-check"],
        });
    },
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

/**
 * Typing an output value fetches task-run outputs and filters the whole task run.
 */
export const SearchFiltersTaskOutputs: Story = {
    play: async ({canvasElement}: {canvasElement: HTMLElement}) => {
        const canvas = within(canvasElement);

        const search = await waitFor(
            () => canvas.getByPlaceholderText(/search key or value/i),
            {timeout: 5000},
        );
        await userEvent.type(search, "200");

        await waitFor(
            () => {
                expect(canvas.getByText("http_request")).toBeTruthy();
                expect(canvas.queryByText("check_status")).toBeNull();
            },
            {timeout: 5000},
        );
    },
};

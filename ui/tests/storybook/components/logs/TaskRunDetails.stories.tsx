import {vueRouter} from "storybook-vue3-router";
import type {Meta, StoryObj} from "@storybook/vue3";
import {expect, userEvent, waitFor} from "storybook/test";
import {useExecutionsStore} from "../../../../src/stores/executions";
import TaskRunDetails from "../../../../src/components/logs/TaskRunDetails.vue";

const TASK_RUN_ID = "task-run-1";

const BASE_LOG = {
    namespace: "company.team",
    flowId: "test-flow",
    executionId: "test-exec-id",
    thread: "main",
    attemptNumber: 0,
    executionKind: "flow" as const,
    taskRunId: TASK_RUN_ID,
    taskId: "my-task",
    level: "INFO",
};

// The four "Processed record NNN" lines collapse into ONE group item: normalizeLogTemplate
// (src/utils/logs.ts) masks any run of 3+ digits, so they share a template key, and 4 consecutive
// matches is over COLLAPSE_THRESHOLD (3). The surrounding lines have distinct templates and stay
// ungrouped, which is what makes the group's boundaries observable.
const FAKE_LOGS = [
    {...BASE_LOG, index: 0, timestamp: "2025-01-01T00:00:00.000Z", message: "Starting my-task"},
    {...BASE_LOG, index: 1, timestamp: "2025-01-01T00:00:01.000Z", message: "Processed record 100"},
    {...BASE_LOG, index: 2, timestamp: "2025-01-01T00:00:02.000Z", message: "Processed record 200"},
    {...BASE_LOG, index: 3, timestamp: "2025-01-01T00:00:03.000Z", message: "Processed record 300"},
    {...BASE_LOG, index: 4, timestamp: "2025-01-01T00:00:04.000Z", message: "Processed record 400"},
    {...BASE_LOG, index: 5, timestamp: "2025-01-01T00:00:05.000Z", message: "Finished my-task"},
];

const TASK_RUN_STATE = {
    current: "SUCCESS",
    startDate: "2025-01-01T00:00:00Z",
    endDate: "2025-01-01T00:00:06Z",
    duration: "PT6S",
    histories: [
        {state: "CREATED", date: "2025-01-01T00:00:00Z"},
        {state: "RUNNING", date: "2025-01-01T00:00:00Z"},
        {state: "SUCCESS", date: "2025-01-01T00:00:06Z"},
    ],
};

const FAKE_EXECUTION = {
    id: "test-exec-id",
    flowId: "test-flow",
    namespace: "company.team",
    state: TASK_RUN_STATE,
    taskRunList: [{id: TASK_RUN_ID, taskId: "my-task", state: TASK_RUN_STATE, attempts: [{state: TASK_RUN_STATE}]}],
};

const ROUTER_ROUTES = [
    {path: "/", name: "home", component: {template: "<div/>"}},
    {path: "/executions/:namespace/:flowId/:id/:tab?", name: "executions/update", component: {template: "<div/>"}},
    {path: "/flows/edit/:namespace/:id/:tab?", name: "flows/update", component: {template: "<div/>"}},
];

// Note: mounting this component also triggers useTaskRunOutputs -> OutputsAPI.taskOutputsInformation,
// a generated SDK call, which reaches the dev server and logs one rejection. It deliberately is not
// stubbed here: vi.mock() has no effect on @kestra-io/kestra-sdk/* in a story file, because the SDK
// is a pre-bundled dependency that vitest's browser mocker cannot intercept. The fix belongs at the
// fetch layer, where the Storybook API double (PR #17772) answers that route with an empty list.
const decorators = [
    () => ({
        setup() {
            const executionsStore = useExecutionsStore();
            executionsStore.execution = FAKE_EXECUTION as any;
            (executionsStore as any).loadLogs = async () => FAKE_LOGS;
        },
        template: "<div style='padding:1rem'><story /></div>",
    }),
    vueRouter(ROUTER_ROUTES, {initialRoute: "/executions/company.team/test-flow/test-exec-id"}),
];

const meta: Meta<typeof TaskRunDetails> = {
    title: "Components/Logs/TaskRunDetails",
    component: TaskRunDetails,
    parameters: {layout: "fullscreen"},
    decorators,
    // Passing taskRunId renders this taskrun's logs directly, without first expanding an attempt.
    args: {taskRunId: TASK_RUN_ID, showProgressBar: false},
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

/**
 * Collapsed runs of similar log lines are the one place where an item's height changes WITHOUT its
 * `item` prop changing: toggleGroup() mutates an external expandedGroups set, and the item then
 * renders `members.length` lines instead of one. DynamicScroller re-measures it through its own
 * ResizeObserver.
 *
 * Asserts on aria-expanded and on the member lines appearing rather than on measured heights —
 * heights are unreliable to assert in Storybook's sandboxed iframe, since DynamicScroller only
 * renders what is in the viewport.
 */
export const ExpandsCollapsedLogGroup: Story = {
    play: async ({canvasElement}: {canvasElement: HTMLElement}) => {
        const toggle = await waitFor(
            () => {
                const button = canvasElement.querySelector<HTMLButtonElement>(".log-group-more");
                if (!button) throw new Error("collapsed log group toggle not rendered");
                return button;
            },
            {timeout: 5000},
        );

        // Collapsed: the group shows its first member plus a "×4" summary button.
        expect(toggle.getAttribute("aria-expanded")).toBe("false");
        expect(toggle.textContent).toContain("×4");
        expect(canvasElement.textContent).not.toContain("Processed record 400");

        await userEvent.click(toggle);

        // Expanded: every member is rendered and the toggle reports the new state.
        await waitFor(
            () => {
                expect(toggle.getAttribute("aria-expanded")).toBe("true");
                expect(canvasElement.textContent).toContain("Processed record 400");
            },
            {timeout: 3000},
        );

        // Collapsing puts it back, so the group is not a one-way door.
        await userEvent.click(toggle);
        await waitFor(
            () => {
                expect(toggle.getAttribute("aria-expanded")).toBe("false");
                expect(canvasElement.textContent).not.toContain("Processed record 400");
            },
            {timeout: 3000},
        );
    },
};

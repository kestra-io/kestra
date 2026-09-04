import {vueRouter} from "storybook-vue3-router";
import type {Meta, StoryObj} from "@storybook/vue3";
import {userEvent, waitFor, within} from "storybook/test";
import {useExecutionsStore} from "../../../../src/stores/executions";
import {storageKeys} from "../../../../src/utils/constants";
// @ts-ignore — Logs.vue is a JS component without a declaration file
import Logs from "../../../../src/components/executions/Logs.vue";
import {expect} from "storybook/test";

// Severity order: index 0 = highest (ERROR), index 4 = lowest (TRACE).
const LEVEL_ORDER = ["ERROR", "WARN", "INFO", "DEBUG", "TRACE"] as const;
type Level = typeof LEVEL_ORDER[number];

function filteredByMinLevel(logs: Array<(typeof FAKE_LOGS)[number]>, minLevel: string) {
    const minIdx = LEVEL_ORDER.indexOf(minLevel as Level);
    if (minIdx === -1) return logs;
    return logs.filter(log => LEVEL_ORDER.indexOf(log.level as Level) <= minIdx);
}

const BASE = {
    namespace: "company.team",
    flowId: "test-flow",
    executionId: "test-exec-id",
    thread: "main",
    attemptNumber: 0,
    executionKind: "flow" as const,
    taskRunId: "task-run-1",
    taskId: "my-task",
};

const FAKE_LOGS = [
    {...BASE, index: 0, level: "ERROR", timestamp: "2025-01-01T00:00:00.000Z", message: "Task failed: NullPointerException at step 3"},
    {...BASE, index: 1, level: "WARN",  timestamp: "2025-01-01T00:00:01.000Z", message: "Retry attempt 1/3 for task my-task"},
    {...BASE, index: 2, level: "WARN",  timestamp: "2025-01-01T00:00:02.000Z", message: "Connection timeout, retrying in 5s"},
    {...BASE, index: 3, level: "INFO",  timestamp: "2025-01-01T00:00:03.000Z", message: "Starting flow execution"},
    {...BASE, index: 4, level: "INFO",  timestamp: "2025-01-01T00:00:04.000Z", message: "Task my-task completed in 1.2s"},
    {...BASE, index: 5, level: "INFO",  timestamp: "2025-01-01T00:00:05.000Z", message: "Execution completed successfully"},
    {...BASE, index: 6, level: "DEBUG", timestamp: "2025-01-01T00:00:06.000Z", message: "Evaluating Pebble expression: {{ inputs.value }}"},
    {...BASE, index: 7, level: "TRACE", timestamp: "2025-01-01T00:00:07.000Z", message: "Entering task executor loop iteration 42"},
    {
        ...BASE,
        index: 8,
        level: "INFO",
        timestamp: "2025-01-01T00:00:08.000Z",
        message: `Subflow triggered: [[link execution="test-exec-id" flowId="test-flow" namespace="company.team"]]`,
    },
];

const FAKE_EXECUTION = {
    id: "test-exec-id",
    flowId: "test-flow",
    namespace: "company.team",
    state: {current: "SUCCESS", startDate: "2025-01-01T00:00:00Z", duration: "PT1S"},
    taskRunList: [{
        id: "task-run-1",
        taskId: "my-task",
        executionId: "test-exec-id",
        state: {
            current: "SUCCESS",
            startDate: "2025-01-01T00:00:00Z",
            endDate: "2025-01-01T00:00:09Z",
            duration: "PT9S",
            histories: [],
        },
        attempts: [{
            state: {
                current: "SUCCESS",
                startDate: "2025-01-01T00:00:00Z",
                endDate: "2025-01-01T00:00:09Z",
                duration: "PT9S",
                histories: [],
            },
        }],
    }],
};

const LONG_FAKE_LOGS = Array.from({length: 5000}, (_, index) => ({
    ...BASE,
    index,
    level: "INFO",
    timestamp: new Date(Date.UTC(2025, 0, 1, 0, 0, 0, index)).toISOString(),
    message: `Log line ${index}`,
}));

const ROUTER_ROUTES = [
    {path: "/", name: "home", component: {template: "<div/>"}},
    {path: "/executions/:namespace/:flowId/:id/:tab?", name: "executions/update", component: {template: "<div/>"}},
    {path: "/flows/edit/:namespace/:id/:tab?", name: "flows/update", component: {template: "<div/>"}},
    {path: "/flows", name: "flows/list", component: {template: "<div/>"}},
];

function makeDecorators(rawView = true, sourceLogs = FAKE_LOGS) {
    return [
        () => ({
            setup() {
                localStorage.setItem(storageKeys.LOGS_VIEW_TYPE, String(rawView));

                const executionsStore = useExecutionsStore();
                executionsStore.logs = filteredByMinLevel(sourceLogs, "INFO") as any;
                executionsStore.execution = FAKE_EXECUTION as any;
                executionsStore.flow = {tasks: [{id: "my-task", type: "io.kestra.plugin.core.log.Log"}]} as any;

                (executionsStore as any).loadLogs = async ({params}: {executionId: string; params?: Record<string, any>}) => {
                    const gte = params?.["filters[level][GREATER_THAN_OR_EQUAL_TO]"];
                    const lte = params?.["filters[level][LESS_THAN_OR_EQUAL_TO]"];
                    let filtered: typeof sourceLogs;
                    if (lte) {
                        const maxIdx = LEVEL_ORDER.indexOf(lte as Level);
                        filtered = sourceLogs.filter(log => LEVEL_ORDER.indexOf(log.level as Level) >= maxIdx);
                    } else {
                        filtered = filteredByMinLevel(sourceLogs, (gte as string) ?? "TRACE");
                    }
                    executionsStore.logs = filtered as any;
                    return filtered;
                };
            },
            template: "<div style='padding:1rem'><story /></div>",
        }),
        vueRouter(ROUTER_ROUTES, {initialRoute: "/executions/company.team/test-flow/test-exec-id"}),
    ];
}

const meta: Meta<typeof Logs> = {
    title: "Components/Executions/Logs",
    component: Logs,
    parameters: {layout: "fullscreen"},
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
    decorators: makeDecorators(),
};

export const Fullscreen: Story = {
    decorators: makeDecorators(true, LONG_FAKE_LOGS),
    play: async ({canvasElement}: {canvasElement: HTMLElement}) => {
        const iframeBody = canvasElement.ownerDocument.body;
        const fullscreenButton = canvasElement.querySelector<HTMLButtonElement>("[data-test='logs-fullscreen-toggle']");
        if (!fullscreenButton) throw new Error("fullscreen logs button not found");

        const scroller = await waitFor(() => {
            const element = canvasElement.querySelector<HTMLElement>("[data-test='logs-scroller']");
            if (!element || element.scrollHeight <= element.clientHeight) {
                throw new Error("scrollable logs not ready");
            }
            return element;
        });
        scroller.scrollTop = Math.floor(scroller.scrollHeight / 2);
        scroller.dispatchEvent(new Event("scroll"));
        await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())));
        const inlineScrollTop = scroller.scrollTop;
        expect(inlineScrollTop).toBeGreaterThan(0);

        await userEvent.click(fullscreenButton);

        const dialog = await waitFor(() => {
            const element = iframeBody.querySelector<HTMLElement>("[data-test='logs-fullscreen-dialog']");
            if (!element) throw new Error("fullscreen logs dialog not found");
            return element;
        });

        const exitFullscreenButton = within(dialog).getByRole("button", {name: /exit fullscreen/i});
        expect(exitFullscreenButton).toHaveAttribute("aria-pressed", "true");
        expect(dialog.querySelector("[data-test='logs-toolbar']")).toBeInTheDocument();
        expect(dialog.querySelector("[data-test='logs-scroller']")).toBeInTheDocument();
        await waitFor(() => expect(Math.abs(scroller.scrollTop - inlineScrollTop)).toBeLessThan(50));

        const fullscreenScrollTop = scroller.scrollTop;
        await userEvent.click(exitFullscreenButton);

        await waitFor(() => expect(dialog).not.toBeVisible());
        await waitFor(() => expect(Math.abs(scroller.scrollTop - fullscreenScrollTop)).toBeLessThan(50));
        const reopenFullscreenButton = canvasElement.querySelector<HTMLButtonElement>("[data-test='logs-fullscreen-toggle']");
        if (!reopenFullscreenButton) throw new Error("fullscreen logs button not found after closing the dialog");
        expect(reopenFullscreenButton).toHaveAttribute("aria-pressed", "false");

        await userEvent.click(reopenFullscreenButton);
        await waitFor(() => expect(dialog).toBeVisible());
    },
};

export const CompactFullscreen: Story = {
    decorators: makeDecorators(false),
    play: async ({canvasElement}: {canvasElement: HTMLElement}) => {
        const iframeBody = canvasElement.ownerDocument.body;
        const fullscreenButton = canvasElement.querySelector<HTMLButtonElement>("[data-test='logs-fullscreen-toggle']");
        if (!fullscreenButton) throw new Error("fullscreen logs button not found");

        await userEvent.click(fullscreenButton);

        const compactScroller = await waitFor(() => {
            const element = iframeBody.querySelector<HTMLElement>("[data-test='logs-fullscreen-dialog'] [data-test='task-run-log-scroller']");
            if (!element) throw new Error("compact task-run log scroller not found");
            return element;
        });

        expect(getComputedStyle(compactScroller).maxHeight).not.toBe("300px");
    },
};

/**
 * Selects WARN from the level filter chip and asserts the full chain fires:
 * chip label updates, loadLogs is called with minLevel=WARN, and the store
 * ends up with exactly 3 logs (1 ERROR + 2 WARN).
 *
 * Store-state assertions are used instead of DOM-node counts because
 * DynamicScroller only renders items visible in the viewport — counting
 * .line elements is unreliable in Storybook's sandboxed iframe.
 */
export const LevelFilterUpdatesRoute: Story = {
    decorators: makeDecorators(),
    play: async ({canvasElement}: {canvasElement: HTMLElement}) => {
        const iframeBody = canvasElement.ownerDocument.body;

        // 1. Wait for chip to stabilise at INFO (default).
        await waitFor(
            () => {
                const valueEl = canvasElement.querySelector(".chip .value");
                if (!valueEl || !/info/i.test(valueEl.textContent ?? "")) {
                    throw new Error(`chip shows "${valueEl?.textContent}", expected INFO`);
                }
            },
            {timeout: 5000}
        );

        // 2. Open the filter chip popup and grab the value combobox.
        //    The comparator (≥/≤) now renders as a segmented control in the
        //    popup header (not a combobox), so the value select (.select-panel)
        //    is the only combobox; scope to it to drive the level value.
        const combobox = await waitFor(
            async () => {
                const chip = canvasElement.querySelector<HTMLElement>(".chip");
                if (!chip) throw new Error("filter chip not found");

                const popup = iframeBody.querySelector<HTMLElement>(".edit-popup");
                if (!popup) {
                    await userEvent.click(chip);
                    throw new Error("popup not yet open, retrying");
                }

                const valuePanel = popup.querySelector<HTMLElement>(".select-panel");
                if (!valuePanel) throw new Error("value select panel not yet rendered in popup");

                const cb = within(valuePanel).queryByRole("combobox");
                if (!cb) throw new Error("combobox not yet rendered in popup");
                return cb;
            },
            {timeout: 5000, interval: 300}
        );
        await userEvent.click(combobox);

        // 3. Select WARN from the dropdown.
        const warnOption = await waitFor(
            () => within(iframeBody).getByRole("option", {name: /^warn$/i}),
            {timeout: 3000}
        );
        await userEvent.click(warnOption);

        // 4. Selecting the value applies live (the Apply button was removed) —
        //    the chip label updates to WARN on its own.
        await waitFor(
            () => {
                const valueEl = canvasElement.querySelector(".chip .value");
                if (!valueEl || !/warn/i.test(valueEl.textContent ?? "")) {
                    throw new Error(`chip still shows "${valueEl?.textContent}"`);
                }
            },
            {timeout: 3000}
        );

        // 6. Store must hold exactly 3 logs (ERROR + 2×WARN): confirms
        //    the full chain fired — chip → route → loadLogs(WARN) → axios mock.
        await waitFor(
            () => {
                const store = useExecutionsStore();
                const count = (store.logs as unknown as any[])?.length ?? -1;
                if (count !== 3) {
                    throw new Error(`expected 3 logs in store after WARN filter, got ${count}`);
                }
            },
            {timeout: 3000}
        );
    },
};

/**
 * Verifies that [[link ...]] syntax inside a log message is converted to a
 * proper <a> anchor by processLinkTags + linkify. The "Subflow triggered" log
 * line (index 8) contains a [[link execution="test-exec-id" ...]] pattern;
 * after render it must contain an <a> whose href includes "test-exec-id".
 */
export const WithLogLinks: Story = {
    decorators: makeDecorators(),
    play: async ({canvasElement}: {canvasElement: HTMLElement}) => {
        // Wait until the "Subflow triggered" log line is visible.
        const linkLine: HTMLElement = await waitFor(
            () => {
                const lines = Array.from(canvasElement.querySelectorAll<HTMLElement>(".line"));
                const found = lines.find(el => el.textContent?.includes("Subflow triggered"));
                if (!found) throw new Error("Subflow triggered log line not found");
                return found;
            },
            {timeout: 5000}
        );

        // linkify runs in nextTick after renderedHtml — wait for the <a> to appear.
        await waitFor(
            () => {
                const anchor = linkLine.querySelector("a");
                if (!anchor) throw new Error("no <a> anchor in Subflow triggered line");
                expect(anchor.getAttribute("href")).toContain("test-exec-id");
            },
            {timeout: 3000}
        );
    },
};

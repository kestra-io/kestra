import type {Meta, StoryObj} from "@storybook/vue3";
import {within, expect, waitFor} from "storybook/test";
import {configureClient} from "@kestra-io/kestra-sdk";
import ExecutionProgress from "../../../../src/components/executions/ExecutionProgress.vue";

const MINUTE_MS = 60 * 1000;

// The component fetches its baseline through ExecutionsAPI.executionAverageDuration(), a generated SDK
// function that calls the SDK's own fetch client - so neither the axios instance the storybook preview
// stubs nor vi.mock() of the SDK submodule intercepts it (the browser runner resolves the real module).
// configureClient() forwards a `fetch` override to that client, which is the seam that does work.
// Everything other than the average-duration endpoint falls through to the real fetch.
function stubAverageDuration(avgDurationMs: number | null) {
    const realFetch = globalThis.fetch.bind(globalThis);
    configureClient({
        fetch: (input: URL | RequestInfo, init?: RequestInit) => {
            const url = input instanceof Request ? input.url : String(input);
            if (!url.includes("/average-duration")) return realFetch(input, init);
            // Mirrors the backend's non_null serialization: no baseline means the field is absent,
            // not null.
            const body = avgDurationMs === null ? {count: 0} : {avgDurationMs, count: 12};
            return Promise.resolve(new Response(JSON.stringify(body), {
                status: 200,
                headers: {"Content-Type": "application/json"},
            }));
        },
    });
}

function execution(startedMsAgo: number) {
    return {
        id: "5cBZ1Ec74EWvvKcULDMBGJ",
        namespace: "company.team",
        flowId: "hello_world",
        state: {
            current: "RUNNING",
            startDate: new Date(Date.now() - startedMsAgo).toISOString(),
        },
    };
}

type ExecutionProgressArgs = {
    /** Baseline returned by the API, in ms; null renders the "no historical data" state. */
    avgDurationMs: number | null;
    /** How long the execution has been running, in ms. */
    startedMsAgo: number;
};

// No `component` binding: the stories drive the render through their own args (the baseline the API
// answers with, and how long the execution has been running) rather than through the component's
// single `execution` prop, so `render` below builds the prop from those.
const meta = {
    title: "Components/Executions/ExecutionProgress",
    argTypes: {
        avgDurationMs: {control: "number"},
        startedMsAgo: {control: "number"},
    },
    decorators: [
        (_story: unknown, context: {args: ExecutionProgressArgs}) => ({
            setup() {
                stubAverageDuration(context.args.avgDurationMs);
            },
            template: "<div style='margin:2rem'><story /></div>",
        }),
    ],
    render: (args: ExecutionProgressArgs) => ({
        components: {ExecutionProgress},
        setup: () => ({execution: execution(args.startedMsAgo)}),
        template: "<ExecutionProgress :execution=\"execution\" />",
    }),
} satisfies Meta<ExecutionProgressArgs>;

export default meta;

type Story = StoryObj<typeof meta>;

// The baseline only arrives once the request resolves, so every assertion waits: the first render always
// shows the no-baseline text with an empty bar, whatever the story's args.
const expectState = (label: RegExp, [minPercent, maxPercent]: [number, number]) => async ({canvasElement}: {canvasElement: HTMLElement}) => {
    const canvas = within(canvasElement);
    await waitFor(() => expect(canvas.getByText(label)).toBeInTheDocument());
    const value = Number(canvas.getByRole("progressbar").getAttribute("aria-valuenow"));
    // A range, not an exact value: the ticker keeps advancing the bar while the assertion runs.
    expect(value).toBeGreaterThanOrEqual(minPercent);
    expect(value).toBeLessThanOrEqual(maxPercent);
};

/** A quarter of the way through a flow that usually takes 20 minutes. */
export const Default: Story = {
    args: {avgDurationMs: 20 * MINUTE_MS, startedMsAgo: 5 * MINUTE_MS},
    play: expectState(/^Est\. remaining:/, [25, 30]),
};

/** The flow has never run to completion, so there is nothing to estimate from. */
export const NoBaseline: Story = {
    args: {avgDurationMs: null, startedMsAgo: 5 * MINUTE_MS},
    play: expectState(/^No historical data/, [0, 0]),
};

/**
 * Running well past its average duration: the bar caps below 100% instead of claiming completion, and
 * the label says so rather than counting down to a stuck "Est. remaining: 0s".
 */
export const PastEstimate: Story = {
    args: {avgDurationMs: 2 * MINUTE_MS, startedMsAgo: 30 * MINUTE_MS},
    play: expectState(/^Running longer than usual$/, [99, 99]),
};

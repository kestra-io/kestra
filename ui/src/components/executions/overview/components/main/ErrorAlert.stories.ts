import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, userEvent, waitFor, within} from "storybook/test"
import {mockStoryApiRoutes} from "../../../../../../.storybook/apiMock"
import ErrorAlert from "./ErrorAlert.vue"
import type {Execution} from "../../../../../stores/executions"

const execution = {
    id: "exec-123",
    namespace: "company.team",
    flowId: "my-flow",
    tenantId: "default",
    kind: "FLOW",
} as unknown as Execution

const meta: Meta<typeof ErrorAlert> = {
    title: "executions/overview/ErrorAlert",
    component: ErrorAlert,
    decorators: [
        (story) => ({
            components: {story},
            template: "<div style=\"max-width:700px;padding:24px\"><story /></div>",
        }),
    ],
}
export default meta
type Story = StoryObj<typeof ErrorAlert>

/** Collapsed by default: long message is truncated with an ellipsis instead of wrapping. */
export const CollapsedWithLongMessage: Story = {
    beforeEach() {
        mockStoryApiRoutes({
            "GET /logs/:executionId": [
                {
                    level: "ERROR",
                    message:
                        "io.kestra.plugin.core.runner.TaskRunnerException: Process exited with code 1 — this is a deliberately very long error message to verify that the preview truncates with an ellipsis rather than wrapping onto a second line inside the alert banner",
                },
            ],
        })
    },
    render: () => ({
        components: {ErrorAlert},
        setup() {
            return {execution}
        },
        template: "<ErrorAlert :execution=\"execution\" />",
    }),
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        await expect(await canvas.findByText(/Last error was/i)).toBeTruthy()
        const preview = canvasElement.querySelector<HTMLElement>(".error-preview")
        if(!preview) throw new Error("Preview element not found")
        await expect(preview).toBeTruthy()
        await expect(getComputedStyle(preview).textOverflow).toBe("clip")
        await expect(canvasElement.querySelector(".logs")).toBeNull()
    },
}

/** Backtick-delimited spans in the raw message are stripped before rendering. */
export const BacktickStripping: Story = {
    beforeEach() {
        mockStoryApiRoutes({
            "GET /logs/:executionId": [
                {level: "ERROR", message: "Failed to parse `config.yml`: unexpected token at line 42"},
            ],
        })
    },
    render: () => ({
        components: {ErrorAlert},
        setup() {
            return {execution}
        },
        template: "<ErrorAlert :execution=\"execution\" />",
    }),
    async play({canvasElement}) {
        const preview = await waitFor(() => {
            const el = canvasElement.querySelector<HTMLElement>(".error-preview")
            expect(el).toBeTruthy()
            return el!
        })
        await expect(preview.textContent).toContain("config.yml")
        await expect(preview.textContent).not.toContain("`")
    },
}

/** Clicking the chevron expands the alert to show the full list of error log lines. */
export const ExpandedLogLines: Story = {
    beforeEach() {
        mockStoryApiRoutes({
            "GET /logs/:executionId": [
                {level: "ERROR", message: "First error: connection refused"},
                {level: "ERROR", message: "Second error: timeout after 30 s"},
                {level: "ERROR", message: "Third error: resource exhausted"},
            ],
        })
    },
    render: () => ({
        components: {ErrorAlert},
        setup() {
            return {execution}
        },
        template: "<ErrorAlert :execution=\"execution\" />",
    }),
    async play({canvasElement}) {
        const expandBtn = canvasElement.querySelector<HTMLElement>(".expand-btn")!
        await userEvent.click(expandBtn)
        await expect(canvasElement.querySelector(".logs")).toBeTruthy()
        await expect(canvasElement.querySelector(".error-preview")).toBeNull()

        await userEvent.click(expandBtn)
        await expect(canvasElement.querySelector(".error-preview")).toBeTruthy()
        await expect(canvasElement.querySelector(".logs")).toBeNull()
    },
}

/** When the execution has no error logs the component renders nothing visible. */
export const NoLogs: Story = {
    beforeEach() {
        mockStoryApiRoutes({"GET /logs/:executionId": []})
    },
    render: () => ({
        components: {ErrorAlert},
        setup() {
            return {execution}
        },
        template: "<ErrorAlert :execution=\"execution\" />",
    }),
}

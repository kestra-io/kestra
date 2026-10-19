import type {Meta, StoryObj} from "@storybook/vue3"
import {expect, waitFor, within} from "storybook/test"
import HttpSummaryDrawer from "./HttpSummaryDrawer.vue"
import {
    downloadOutputs, downloadTask, encryptedBodyOutputs, execution, noRequestOutputs,
    redactedHeadersOutputs, redirectRetryOutputs, requestTask, successOutputs,
} from "./httpSummary.fixtures"

const meta: Meta<typeof HttpSummaryDrawer> = {
    title: "Components/Executions/HttpSummaryDrawer",
    component: HttpSummaryDrawer,
    args: {task: requestTask, execution},
}

export default meta
type Story = StoryObj<typeof meta>

const outputs = (value: Record<string, unknown>) => () => Promise.resolve(value)

export const Success: Story = {
    args: {fetchOutputs: outputs(successOutputs)},
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        await waitFor(() => expect(canvas.getByText("200 OK")).toBeVisible())
        expect(canvas.getByText("api.example.com")).toBeVisible()
        expect(canvasElement.querySelector("[data-test=\"http-summary-body-text\"]")?.textContent).toContain("\"name\": \"item\"")
        expect(canvasElement.querySelector("[data-test=\"http-summary-redirects\"]")).toBeNull()
    },
}

export const RedirectsAndRetries: Story = {
    args: {fetchOutputs: outputs(redirectRetryOutputs)},
    async play({canvasElement}) {
        await waitFor(() => expect(canvasElement.querySelector("[data-test=\"http-summary-redirects\"]")).not.toBeNull())
        expect(within(canvasElement).getAllByText("301")).toHaveLength(1)
        expect(within(canvasElement).getAllByText("https://api.example.com/v2/items").length).toBeGreaterThan(1)
    },
}

export const RedactedHeaders: Story = {
    args: {fetchOutputs: outputs(redactedHeadersOutputs)},
    async play({canvasElement}) {
        await waitFor(() => expect(within(canvasElement).getByText("set-cookie")).toBeVisible())
        expect(within(canvasElement).getByText("redacted")).toBeVisible()
    },
}

export const EncryptedBody: Story = {
    args: {fetchOutputs: outputs(encryptedBodyOutputs)},
    async play({canvasElement}) {
        await waitFor(() => expect(canvasElement.querySelector("[data-test=\"http-summary-body-encrypted\"]")).not.toBeNull())
        expect(canvasElement.querySelector("[data-test=\"http-summary-body-text\"]")).toBeNull()
        expect(canvasElement.textContent).not.toContain("ciphertext")
    },
}

export const NoRequestOutput: Story = {
    args: {fetchOutputs: outputs(noRequestOutputs)},
    async play({canvasElement}) {
        await waitFor(() => expect(canvasElement.querySelector("[data-test=\"http-summary-no-request\"]")).not.toBeNull())
        expect(within(canvasElement).getByText("204 No Content")).toBeVisible()
    },
}

export const Download: Story = {
    args: {task: downloadTask, fetchOutputs: outputs(downloadOutputs)},
    async play({canvasElement}) {
        await waitFor(() => expect(canvasElement.querySelector("[data-test=\"http-summary-file\"]")).not.toBeNull())
        expect(canvasElement.textContent).toContain("data.csv")
        expect(canvasElement.querySelector("[data-test=\"http-summary-body\"]")).toBeNull()
    },
}

export const NoOutputsYet: Story = {
    args: {fetchOutputs: outputs({})},
    async play({canvasElement}) {
        await waitFor(() => expect(canvasElement.querySelector("[data-test=\"http-summary-empty\"]")).not.toBeNull())
    },
}

export const NoExecution: Story = {
    args: {execution: undefined, fetchOutputs: undefined},
    async play({canvasElement}) {
        await waitFor(() => expect(canvasElement.querySelector("[data-test=\"http-summary-empty\"]")).not.toBeNull())
    },
}

export const Loading: Story = {
    args: {fetchOutputs: () => new Promise(() => undefined)},
    async play({canvasElement}) {
        await waitFor(() => expect(canvasElement.querySelector("[data-test=\"http-summary-loading\"]")).not.toBeNull())
    },
}

export const LoadError: Story = {
    args: {fetchOutputs: () => Promise.reject(new Error("boom"))},
    async play({canvasElement}) {
        await waitFor(() => expect(canvasElement.querySelector("[data-test=\"http-summary-error\"]")).not.toBeNull())
    },
}

export const Dark: Story = {
    args: {fetchOutputs: outputs(redirectRetryOutputs)},
    globals: {theme: "dark"},
}

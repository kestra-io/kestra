import type {Meta, StoryObj} from "@storybook/vue3"
import {expect, waitFor, within} from "storybook/test"
import HttpSummaryCard from "./HttpSummaryCard.vue"
import {
    downloadOutputs, downloadTask, execution, noRequestOutputs, requestTask, successOutputs,
} from "./httpSummary.fixtures"

const meta: Meta<typeof HttpSummaryCard> = {
    title: "Components/Executions/HttpSummaryCard",
    component: HttpSummaryCard,
    args: {task: requestTask, execution},
}

export default meta
type Story = StoryObj<typeof meta>

const outputs = (value: Record<string, unknown>) => () => Promise.resolve(value)

export const Success: Story = {
    args: {fetchOutputs: outputs(successOutputs)},
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        await waitFor(() => expect(canvas.getByText("POST")).toBeVisible())
        expect(canvas.getByText("api.example.com/v1/items")).toBeVisible()
        expect(canvas.getByText("200 OK")).toBeVisible()
        expect(canvas.getByText("application/json")).toBeVisible()
        expect(canvas.getByText("2.0 KiB")).toBeVisible()
        expect(canvas.getByText("0.18s")).toBeVisible()
    },
}

export const NoRequestOutput: Story = {
    args: {fetchOutputs: outputs(noRequestOutputs)},
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        await waitFor(() => expect(canvas.getByText("204 No Content")).toBeVisible())
        expect(canvas.queryByText("Method")).toBeNull()
    },
}

export const Download: Story = {
    args: {task: downloadTask, fetchOutputs: outputs(downloadOutputs)},
    async play({canvasElement}) {
        await waitFor(() => expect(within(canvasElement).getByText("text/csv")).toBeVisible())
        expect(within(canvasElement).getByText("1.0 MiB")).toBeVisible()
    },
}

export const NoOutputsYet: Story = {
    args: {fetchOutputs: outputs({})},
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
    args: {fetchOutputs: outputs(successOutputs)},
    globals: {theme: "dark"},
}

import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {within, expect, fn, userEvent} from "storybook/test"
import {vi} from "vitest"
import OperationCard from "./OperationCard.vue"
import type {Notification} from "../../stores/notifications"

const replay: Notification = {
    id: "n1",
    userId: "u1",
    tenantId: "main",
    type: "ASYNC_OPERATION",
    asyncOperationType: "EXECUTION_REPLAY",
    resourceType: "EXECUTION",
    title: "Execution replay requested for 4000 items",
    referenceId: "op-1",
    succeededItems: 2847,
    failedItems: 0,
    totalItems: 4000,
    ongoing: true,
    read: true,
    createdDate: "2026-09-30T10:00:00Z",
    updatedDate: "2026-09-30T10:04:00Z",
}

const meta: Meta<typeof OperationCard> = {
    title: "Components/Layout/OperationCard",
    component: OperationCard,
    tags: ["autodocs"],
    args: {onClick: fn()},
    render: (args) => ({
        components: {OperationCard},
        setup() {
            return {args}
        },
        template: "<div style=\"padding:24px;max-width:400px\"><operation-card v-bind=\"args\" /></div>",
    }),
    beforeEach: async () => {
        vi.useFakeTimers({toFake: ["Date"]})
        vi.setSystemTime(new Date("2026-09-30T10:04:12Z"))
        return () => vi.useRealTimers()
    },
}
export default meta
type Story = StoryObj<typeof OperationCard>

function barColor(canvasElement: HTMLElement) {
    return within(canvasElement).getByRole("progressbar").querySelector("[style*='background-color']")?.getAttribute("style")
}

export const Running: Story = {
    args: {notification: replay},
    async play({canvasElement, args}) {
        const canvas = within(canvasElement)
        await expect(await canvas.findByText("Replaying 4,000 executions")).toBeVisible()
        await expect(await canvas.findByText("2,847 processed · running for 4m, 12s")).toBeVisible()
        await expect(canvas.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "71")
        await expect(barColor(canvasElement)).toContain("--ks-status-running")

        await userEvent.click(canvas.getByRole("button"))
        await expect(args.onClick).toHaveBeenCalledTimes(1)
    },
}

export const Succeeded: Story = {
    args: {notification: {...replay, succeededItems: 4000, ongoing: false, outcome: "SUCCEEDED"}},
    async play({canvasElement}) {
        await expect(barColor(canvasElement)).toContain("--ks-status-success")
    },
}

export const Partial: Story = {
    args: {notification: {...replay, succeededItems: 3852, failedItems: 148, ongoing: false, outcome: "PARTIAL"}},
    async play({canvasElement}) {
        await expect(barColor(canvasElement)).toContain("--ks-status-warning")
    },
}

export const Failed: Story = {
    args: {notification: {...replay, succeededItems: 0, failedItems: 4000, ongoing: false, outcome: "FAILED"}},
    async play({canvasElement}) {
        await expect(barColor(canvasElement)).toContain("--ks-status-failed")
    },
}

export const WithRejectedItems: Story = {
    args: {notification: {...replay, succeededItems: 3852, failedItems: 100}},
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        await expect(await canvas.findByText("3,952 processed · 100 rejected · running for 4m, 12s")).toBeVisible()
    },
}

export const SingleExecution: Story = {
    args: {notification: {...replay, asyncOperationType: "EXECUTION_KILL", succeededItems: 0, totalItems: 1}},
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        await expect(await canvas.findByText("Killing 1 execution")).toBeVisible()
    },
}

export const Backfill: Story = {
    args: {notification: {...replay, asyncOperationType: "BACKFILL_PAUSE", resourceType: "TRIGGER", succeededItems: 1, totalItems: 3}},
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        await expect(await canvas.findByText("Pausing 3 backfills")).toBeVisible()
    },
}

export const WithoutTotal: Story = {
    args: {notification: {...replay, asyncOperationType: null, title: "Deleting matching executions", succeededItems: null, failedItems: null, totalItems: null, ongoing: undefined}},
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        await expect(await canvas.findByText("Deleting matching executions")).toBeVisible()
        await expect(await canvas.findByText("running for 4m, 12s")).toBeVisible()
        await expect(canvas.queryByRole("progressbar")).toBeNull()
    },
}

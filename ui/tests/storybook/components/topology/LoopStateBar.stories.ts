import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, within} from "storybook/test"
import LoopStateBar from "../../../../packages/topology/src/nodes/LoopStateBar.vue"

const meta: Meta<typeof LoopStateBar> = {
    title: "Topology/LoopStateBar",
    component: LoopStateBar,
    render: (args) => ({
        components: {LoopStateBar},
        setup: () => ({args}),
        template: "<div style=\"width: 20rem; padding: var(--ks-spacing-4); background: var(--ks-bg-surface);\"><LoopStateBar v-bind=\"args\" /></div>",
    }),
}

export default meta

type Story = StoryObj<typeof LoopStateBar>

export const AllSuccess: Story = {
    args: {counts: {SUCCESS: 480}},
    play: async ({canvasElement}) => {
        const bar = within(canvasElement).getByRole("img")

        expect(bar.getAttribute("aria-label")).toBe("480 success")
        expect(bar.querySelectorAll("[data-test='loop-state-bar-segment']")).toHaveLength(1)
    },
}

export const OneFailureInTenThousand: Story = {
    args: {counts: {SUCCESS: 9999, FAILED: 1}},
    play: async ({canvasElement}) => {
        const failed = canvasElement.querySelector<HTMLElement>("[data-state='FAILED']")!
        const bar = within(canvasElement).getByRole("img")

        expect(failed.getBoundingClientRect().width).toBeGreaterThanOrEqual(3)
        expect(bar.getAttribute("aria-label")).toBe("9,999 success, 1 failed")
    },
}

export const RunningWithNotStarted: Story = {
    args: {counts: {SUCCESS: 4812, FAILED: 3}, running: 12, total: 5000},
    play: async ({canvasElement}) => {
        const bar = within(canvasElement).getByRole("img")

        expect(bar.getAttribute("aria-label")).toBe("4,812 success, 3 failed, 12 running, 173 not started")
    },
}

export const EveryState: Story = {
    args: {counts: {SUCCESS: 40, WARNING: 5, FAILED: 8, KILLED: 4, SKIPPED: 12, CANCELLED: 3, RUNNING: 10, PAUSED: 2}, total: 100},
}

export const UnknownState: Story = {
    args: {counts: {SUCCESS: 30, MYSTERY: 5}},
    play: async ({canvasElement}) => {
        const unknown = canvasElement.querySelector<HTMLElement>("[data-state='MYSTERY']")!

        expect(unknown.style.backgroundColor).toBe("var(--ks-status-neutral)")
    },
}

export const Dark: Story = {
    args: {counts: {SUCCESS: 4812, FAILED: 3, SKIPPED: 20}, running: 12, total: 5000},
    parameters: {themes: {themeOverride: "dark"}},
}

export const NoCounts: Story = {
    args: {},
    play: async ({canvasElement}) => {
        expect(canvasElement.querySelector("[data-test='loop-state-bar']")).toBeNull()
    },
}

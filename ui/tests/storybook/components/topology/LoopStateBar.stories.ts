import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, userEvent, waitFor, within} from "storybook/test"
import LoopStateBar from "../../../../packages/topology/src/nodes/LoopStateBar.vue"

const REAL_POINTER_EVENTS = ["mouseover", "mouseout", "mouseenter", "mouseleave"] as const

const meta: Meta<typeof LoopStateBar> = {
    title: "Topology/LoopStateBar",
    component: LoopStateBar,
    render: (args) => ({
        components: {LoopStateBar},
        setup: () => ({args}),
        template: "<div style=\"width: 20rem; padding: var(--ks-spacing-4); background: var(--ks-bg-surface);\"><LoopStateBar v-bind=\"args\" /></div>",
    }),
    // The test browser's own cursor fires trusted mouseovers whenever a story mounts under it, which the bar reads as the pointer leaving.
    beforeEach: () => {
        const ignoreRealPointer = (event: Event) => {
            if (event.isTrusted) event.stopImmediatePropagation()
        }
        for (const type of REAL_POINTER_EVENTS) window.addEventListener(type, ignoreRealPointer, true)
        return () => {
            for (const type of REAL_POINTER_EVENTS) window.removeEventListener(type, ignoreRealPointer, true)
        }
    },
}

export default meta

type Story = StoryObj<typeof LoopStateBar>

export const AllSuccess: Story = {
    args: {counts: {SUCCESS: 480}},
    play: async ({canvasElement}) => {
        const bar = within(canvasElement).getByRole("img")

        expect(bar.getAttribute("aria-label")).toBe("480 SUCCESS")
        expect(bar.querySelectorAll("[data-test='loop-state-bar-segment']")).toHaveLength(1)
    },
}

export const OneFailureInTenThousand: Story = {
    args: {counts: {SUCCESS: 9999, FAILED: 1}},
    play: async ({canvasElement}) => {
        const failed = canvasElement.querySelector<HTMLElement>("[data-state='FAILED']")!
        const bar = within(canvasElement).getByRole("img")

        expect(failed.getBoundingClientRect().width).toBeGreaterThanOrEqual(3)
        expect(bar.getAttribute("aria-label")).toBe("9,999 SUCCESS, 1 FAILED")
    },
}

export const RunningWithNotStarted: Story = {
    args: {counts: {SUCCESS: 4812, FAILED: 3}, running: 12, total: 5000},
    play: async ({canvasElement}) => {
        const bar = within(canvasElement).getByRole("img")

        expect(bar.getAttribute("aria-label")).toBe("4,812 SUCCESS, 3 FAILED, 12 RUNNING, 173 not started")
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

const legendShown = () => {
    const legend = document.body.querySelector<HTMLElement>("[data-test='loop-state-bar-legend']")
    return legend !== null && getComputedStyle(legend.closest<HTMLElement>("[role='tooltip']") ?? legend).display !== "none"
}

const SETTLE_MS = 400

const settle = () => new Promise((resolve) => setTimeout(resolve, SETTLE_MS))

export const LegendOnKeyboardFocus: Story = {
    args: {counts: {SUCCESS: 30, FAILED: 2}, total: 40},
    play: async ({canvasElement}) => {
        await userEvent.tab()

        expect(within(canvasElement).getByRole("img")).toHaveFocus()
        await waitFor(() => expect(legendShown()).toBe(true))
        await userEvent.keyboard("{Escape}")
        await waitFor(() => expect(legendShown()).toBe(false))
    },
}

export const LegendClosesAfterAClick: Story = {
    args: {counts: {SUCCESS: 30, FAILED: 2}},
    play: async ({canvasElement}) => {
        const bar = within(canvasElement).getByRole("img")

        await userEvent.click(bar)
        await userEvent.unhover(bar)

        await waitFor(() => expect(legendShown()).toBe(false))
    },
}

const ignoreRealPointer = (event: MouseEvent) => {
    if (event.isTrusted) event.stopImmediatePropagation()
}

export const LegendStaysOpenWhileHoveringIt: Story = {
    args: {counts: {SUCCESS: 30, FAILED: 2}},
    play: async ({canvasElement}) => {
        const user = userEvent.setup()
        const bar = within(canvasElement).getByRole("img")
        // A real cursor resting over the page sends a trusted mouseover whenever the DOM under it changes, which LoopStateBar reads as the pointer leaving.
        window.addEventListener("mouseover", ignoreRealPointer, true)

        try {
            await user.hover(bar)
            await waitFor(() => expect(legendShown()).toBe(true))
            await user.hover(document.body.querySelector<HTMLElement>("[data-test='loop-state-bar-legend']")!)
            await settle()
            // legendShown stays true through the fade-out, so a legend that had started closing would still pass it.
            expect(bar).toHaveAttribute("aria-describedby")

            await user.hover(canvasElement)
            await waitFor(() => expect(legendShown()).toBe(false))
        } finally {
            window.removeEventListener("mouseover", ignoreRealPointer, true)
        }
    },
}

export const EscapeClosesAHoverLegend: Story = {
    args: {counts: {SUCCESS: 30, FAILED: 2}},
    play: async ({canvasElement}) => {
        await userEvent.hover(within(canvasElement).getByRole("img"))
        await waitFor(() => expect(legendShown()).toBe(true))

        await userEvent.keyboard("{Escape}")

        await waitFor(() => expect(legendShown()).toBe(false))
    },
}

export const HitAreaExtendsBeyondTheBar: Story = {
    args: {counts: {SUCCESS: 30, FAILED: 2}},
    play: async ({canvasElement}) => {
        const {left, bottom, width} = within(canvasElement).getByRole("img").getBoundingClientRect()

        const below = document.elementFromPoint(left + width / 2, bottom + 1)

        expect(below?.closest("[data-test='loop-state-bar']")).not.toBeNull()
    },
}

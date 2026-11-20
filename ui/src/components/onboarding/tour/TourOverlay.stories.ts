import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {vueRouter} from "storybook-vue3-router"
import {expect, userEvent, waitFor, within} from "storybook/test"
import TourOverlay from "./TourOverlay.vue"
import {DEFAULT_TOUR_VARIANT} from "./tourVariant"
import {useProductTourStore} from "../../../stores/productTour"

const meta: Meta<typeof TourOverlay> = {
    title: "components/onboarding/TourOverlay",
    component: TourOverlay,
    parameters: {
        layout: "fullscreen",
        viewport: {
            options: {
                laptop: {name: "Laptop", styles: {width: "1440px", height: "900px"}},
            },
        },
    },
    globals: {
        viewport: {value: "laptop", isRotated: false},
    },
    decorators: [
        vueRouter([
            {path: "/", name: "home", component: {template: "<div />"}},
            {path: "/executions", name: "executions/list", component: {template: "<div />"}},
        ]),
    ],
}
export default meta
type Story = StoryObj<typeof TourOverlay>

const BOX = "position: fixed; background: var(--ks-bg-surface); border: 1px solid var(--ks-border-default);"

// Where the card sits by default, so a card that does not move covers these.
const BOTTOM_RIGHT = `${BOX} right: 3rem; bottom: 3rem; width: 30rem; height: 16rem;`

const tourOn = (sceneId: string, page: string): Story["render"] => () => ({
    components: {TourOverlay},
    setup() {
        const store = useProductTourStore()
        store.startGuided(DEFAULT_TOUR_VARIANT)
        store.setStep(sceneId)
        store.setTourState({introSeen: true})
    },
    template: `<div>${page}<TourOverlay /></div>`,
})

const find = (canvasElement: HTMLElement, selector: string) => {
    const element = canvasElement.querySelector<HTMLElement>(selector)
    if (!element) {
        throw new Error(`Nothing matches ${selector}`)
    }
    return element
}

const overlap = (canvasElement: HTMLElement, selector: string) => {
    const card = find(canvasElement, "[data-test=\"tour-card\"]").getBoundingClientRect()
    const other = find(canvasElement, selector).getBoundingClientRect()
    return card.left < other.right && other.left < card.right && card.top < other.bottom && other.top < card.bottom
}

export const MovesOffTheHighlightedElement: Story = {
    render: tourOn("failed_execution", `<div class="log-row-error" style="${BOTTOM_RIGHT}">ERROR</div>`),
    async play({canvasElement}) {
        await within(canvasElement).findByText("A task failed")

        await waitFor(() => expect(overlap(canvasElement, ".log-row-error")).toBe(false), {timeout: 3_000})
    },
}

export const MovesOffAnAvoidedElement: Story = {
    render: tourOn("chain", `<span class="labels-container" style="${BOTTOM_RIGHT}">started_by:you</span>`),
    async play({canvasElement}) {
        await within(canvasElement).findByText("How each execution was started")

        await waitFor(() => expect(overlap(canvasElement, ".labels-container")).toBe(false), {timeout: 3_000})
    },
}

export const CollapsesToAPill: Story = {
    render: tourOn("failed_execution", `<div class="log-row-error" style="${BOX} inset: 0;">ERROR</div>`),
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        await canvas.findByText("A task failed")

        await userEvent.click(find(canvasElement, "[data-test=\"tour-collapse\"]"))

        const pill = await canvas.findByRole("button", {name: /Step 6 of 14/})
        await expect(canvas.queryByText("A task failed")).toBeNull()
        await expect(useProductTourStore().state.tour.minimized).toBe(true)
        await waitFor(() => expect(pill).toHaveFocus())

        await userEvent.click(pill)

        await expect(await canvas.findByText("A task failed")).toBeVisible()
        await waitFor(() => expect(find(canvasElement, "[data-test=\"tour-collapse\"]")).toHaveFocus())
    },
}

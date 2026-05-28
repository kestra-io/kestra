import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect} from "storybook/test"
import {KsPie} from "../../../src"

const STATUS_DATA = [
    {name: "Success", value: 1204},
    {name: "Failed", value: 87},
    {name: "Running", value: 34},
    {name: "Killed", value: 12},
    {name: "Paused", value: 5},
]

const meta: Meta<typeof KsPie> = {
    title: "Components/Charts/KsPie",
    component: KsPie,
    tags: ["autodocs"],
    argTypes: {
        loading: {control: "boolean"},
        donut: {control: "boolean"},
    },
    parameters: {
        docs: {
            description: {
                component:
                    "KsPie renders a pie (or donut) chart powered by ECharts, registering only the `PieChart` module " +
                    "for optimal tree-shaking. Pass `data` as an array of `{name, value}` items. " +
                    "Set `data` to `null` while fetching to show the built-in loading indicator. " +
                    "Use `donut` to render as a ring chart.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsPie>

// ─── Standard ─────────────────────────────────────────────────────────────────

/** Pie chart */
export const Default: Story = {
    render: (args) => ({
        components: {KsPie},
        setup() { return {args} },
        template: "<div style=\"padding:24px;height:300px\"><ks-pie v-bind=\"args\" /></div>",
    }),
    args: {
        data: STATUS_DATA,
        loading: false,
    },
    async play({canvasElement}) {
        await expect(canvasElement.querySelector(".ks-chart--pie")).toBeTruthy()
    },
}

/** Donut chart — ring variant */
export const Donut: Story = {
    render: (args) => ({
        components: {KsPie},
        setup() { return {args} },
        template: "<div style=\"padding:24px;height:300px\"><ks-pie v-bind=\"args\" /></div>",
    }),
    args: {
        data: STATUS_DATA,
        donut: true,
        loading: false,
        options: {
            legend: {
                show: false,
            },
        },
    },
    async play({canvasElement}) {
        await expect(canvasElement.querySelector(".ks-chart--pie")).toBeTruthy()
    },
}

/** Loading state — shown while data is being fetched */
export const Loading: Story = {
    render: () => ({
        components: {KsPie},
        template: "<div style=\"padding:24px;height:300px\"><ks-pie :data=\"null\" /></div>",
    }),
    async play({canvasElement}) {
        await expect(canvasElement.querySelector(".ks-chart--pie")).toBeTruthy()
    },
}

/** Custom colors via options override */
export const WithOptionsOverride: Story = {
    render: () => ({
        components: {KsPie},
        template: `
            <div style="padding:24px;height:320px">
                <ks-pie
                    :data="[
                        {name: 'Success', value: 1204},
                        {name: 'Failed', value: 87},
                        {name: 'Running', value: 34},
                    ]"
                    :options="{color: ['#22c55e', '#ef4444', '#3b82f6']}"
                    donut
                    :loading="false"
                />
            </div>
        `,
    }),
}

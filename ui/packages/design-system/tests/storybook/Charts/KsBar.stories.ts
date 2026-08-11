import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import {expect} from "storybook/test"
import KsBar from "../../../src/components/Charts/KsBar.vue"
import {ChartFeature, TooltipType} from "../../../src"

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
const ALL_FEATURES = ["LEGEND", "AXIS", "AXIS_SPLITLINE", "TOOLTIP"] as const

const meta: Meta<typeof KsBar> = {
    title: "Components/Charts/KsBar",
    component: KsBar,
    tags: ["autodocs"],
    argTypes: {
        loading: {control: "boolean"},
        stack: {control: "boolean"},
        disableFeatures: {control: "multi-select", options: ["LEGEND", "AXIS", "AXIS_SPLITLINE", "TOOLTIP"]},
        tooltipType: {control: "select", options: ["native", "external"]},
    },
    parameters: {
        docs: {
            description: {
                component:
                    "KsBar renders a bar chart powered by ECharts, registering only the `BarChart` module " +
                    "for optimal tree-shaking. Pass `data` as an array of `{name, data[]}` series objects. " +
                    "Set `data` to `null` while fetching to show the built-in loading indicator. " +
                    "Use `stack` to stack all series. " +
                    "Use `disableFeatures` to strip visual chrome (LEGEND, AXIS, AXIS_SPLITLINE, TOOLTIP).",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsBar>

// ─── Standard ─────────────────────────────────────────────────────────────────

/** Single series bar chart */
export const Default: Story = {
    render: (args) => ({
        components: {KsBar},
        setup() { return {args, MONTHS} },
        template: "<div style=\"padding:24px;height:300px\"><ks-bar v-bind=\"args\" :categories=\"MONTHS\" /></div>",
    }),
    args: {
        data: [{name: "Executions", data: [120, 200, 150, 80, 70, 110, 130, 170, 90, 160, 220, 180]}],
        loading: false,
    },
    async play({canvasElement}) {
        await expect(canvasElement.querySelector(".ks-chart--bar")).toBeTruthy()
    },
}

/** Stacked bar chart */
export const StackedExternalTooltip: Story = {
    render: () => ({
        components: {KsBar},
        setup() { return {MONTHS} },
        template: `
            <div style="padding:24px;height:320px">
                <ks-bar
                    stack
                    :categories="MONTHS"
                    tooltip-type="external"
                    :data="[
                        {name: 'Success', data: [120, 200, 150, 80, 70, 110, 130, 170, 90, 160, 220, 180]},
                        {name: 'Failed',  data: [10, 5, 18, 12, 8, 15, 7, 20, 9, 14, 25, 11]},
                        {name: 'Killed',  data: [2, 1, 4, 3, 1, 2, 3, 5, 2, 3, 6, 4]},
                    ]"
                    :loading="false"
                />
            </div>
        `,
    }),
}

/** Single series bar chart */
export const SplitArea: Story = {
    render: (args) => ({
        components: {KsBar},
        setup() { return {args, MONTHS} },
        template: "<div style=\"padding:24px;height:300px\"><ks-bar v-bind=\"args\" :categories=\"MONTHS\" /></div>",
    }),
    args: {
        data: [{name: "Executions", data: [120, 200, 150, 80, 70, 110, 130, 170, 90, 160, 220, 180]}],
        options: {
            xAxis: {
                name: "Month",
                splitLine: {
                    show: false,
                },
                splitArea: {
                    show: true,
                },
            },
            yAxis: {
                name: "Total",
                splitLine: {
                    show: false,
                },
                splitArea: {
                    show: true,
                },
            },
        },
        loading: false,
    },
    async play({canvasElement}) {
        await expect(canvasElement.querySelector(".ks-chart--bar")).toBeTruthy()
    },
}

/** Loading state — shown while data is being fetched */
export const Loading: Story = {
    render: () => ({
        components: {KsBar},
        template: "<div style=\"padding:24px;height:300px\"><ks-bar :data=\"null\" /></div>",
    }),
    async play({canvasElement}) {
        await expect(canvasElement.querySelector(".ks-chart--bar")).toBeTruthy()
    },
}

/** Simulates fetching then populating the chart */
export const AsyncData: Story = {
    render: () => ({
        components: {KsBar},
        setup() {
            const data = ref<null | {name: string; data: number[]}[]>(null)
            const categories = ref<string[]>([])

            function load() {
                data.value = null
                categories.value = []
                setTimeout(() => {
                    categories.value = MONTHS
                    data.value = [
                        {name: "2024", data: [120, 200, 150, 80, 70, 110, 130, 170, 90, 160, 220, 180]},
                        {name: "2023", data: [90, 140, 120, 60, 50, 90, 110, 140, 75, 130, 180, 150]},
                    ]
                }, 1500)
            }

            load()
            return {data, categories, load}
        },
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px">
                <button
                    style="width:120px;padding:6px 12px;cursor:pointer;border:1px solid #ccc;border-radius:4px"
                    @click="load"
                >Reload data</button>
                <div style="height:320px"><ks-bar :data="data" :categories="categories" /></div>
            </div>
        `,
    }),
}

/** Custom colors via options override */
export const WithOptionsOverride: Story = {
    args: {
        disableFeatures: [ChartFeature.LEGEND],
        loading: true,
        tooltipType: TooltipType.EXTERNAL,
    },

    render: () => ({
        components: {KsBar},
        setup() { return {MONTHS} },
        template: `
            <div style="padding:24px;height:300px">
                <ks-bar
                    stack
                    :categories="MONTHS"
                    :data="[
                        {name: 'Success', data: [120, 200, 150, 80, 70, 110, 130, 170, 90, 160, 220, 180]},
                        {name: 'Failed',  data: [10, 5, 18, 12, 8, 15, 7, 20, 9, 14, 25, 11]},
                    ]"
                    :options="{color: ['#22c55e', '#ef4444']}"
                    :loading="false"
                />
            </div>
        `,
    }),
}

// ─── Compact sparkline (all features disabled) ────────────────────────────────

/** Compact sparkline — single series */
export const MiniBar: Story = {
    render: (args) => ({
        components: {KsBar},
        setup() { return {args, MONTHS} },
        template: "<div style=\"padding:24px;width:240px;height:80px\"><ks-bar v-bind=\"args\" :categories=\"MONTHS\" /></div>",
    }),
    args: {
        disableFeatures: [ChartFeature.LEGEND, ChartFeature.AXIS, ChartFeature.AXIS_SPLITLINE],
        tooltipType: TooltipType.EXTERNAL,
        data: [{name: "Executions", data: [120, 200, 150, 80, 70, 110, 130, 170, 90, 160, 220, 180]}],
        loading: false,
    },
    async play({canvasElement}) {
        await expect(canvasElement.querySelector(".ks-chart-wrapper")).toBeTruthy()
    },
}

/** Compact stacked sparkline */
export const MiniStacked: Story = {
    render: () => ({
        components: {KsBar},
        setup() { return {MONTHS, ALL_FEATURES} },
        template: `
            <div style="padding:24px;width:240px;height:80px">
                <ks-bar
                    :disable-features="ALL_FEATURES"
                    stack
                    tooltip-type="external"
                    :categories="MONTHS"
                    :data="[
                        {name: 'Success', data: [120, 200, 150, 80, 70, 110, 130, 170, 90, 160, 220, 180]},
                        {name: 'Failed',  data: [10, 5, 18, 12, 8, 15, 7, 20, 9, 14, 25, 11]},
                    ]"
                    :loading="false"
                />
            </div>
        `,
    }),
}

/** Mini loading state */
export const MiniLoading: Story = {
    render: () => ({
        components: {KsBar},
        setup() { return {ALL_FEATURES} },
        template: "<div style=\"padding:24px;width:240px;height:80px\"><ks-bar :disable-features=\"ALL_FEATURES\" :data=\"null\" /></div>",
    }),
}

import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import {expect} from "storybook/test"
import KsLine from "../../../src/components/Charts/KsLine.vue"
import KsBar from "../../../src/components/Charts/KsBar.vue"
import {ChartFeature, TooltipType} from "../../../src/components/Charts/ksChartUtils"

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
const ALL_FEATURES: ChartFeature[] = [ChartFeature.LEGEND, ChartFeature.AXIS, ChartFeature.AXIS_SPLITLINE, ChartFeature.TOOLTIP]

const meta: Meta<typeof KsLine> = {
    title: "Components/Charts/KsLine",
    component: KsLine,
    tags: ["autodocs"],
    argTypes: {
        loading: {control: "boolean"},
        disableFeatures: {control: "multi-select", options: ["LEGEND", "AXIS", "AXIS_SPLITLINE", "TOOLTIP"]},
        tooltipType: {control: "select", options: ["native", "external"]},
    },
    parameters: {
        docs: {
            description: {
                component:
                    "KsLine renders a line chart powered by ECharts, registering only the `LineChart` module " +
                    "for optimal tree-shaking. Pass `data` as an array of `{name, data[]}` series objects. " +
                    "Set `data` to `null` while fetching to show the built-in loading indicator. " +
                    "Use `disableFeatures` to strip visual chrome (LEGEND, AXIS, AXIS_SPLITLINE, TOOLTIP). " +
                    "Use `tooltipType=\"external\"` to replace the ECharts tooltip with a KsTooltip overlay.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsLine>

// ─── Standard ─────────────────────────────────────────────────────────────────

/** Single series line chart */
export const Default: Story = {
    render: (args) => ({
        components: {KsLine},
        setup() { return {args, MONTHS} },
        template: "<div style=\"padding:24px;height:300px\"><ks-line v-bind=\"args\" :categories=\"MONTHS\" /></div>",
    }),
    args: {
        data: [{name: "Executions", data: [120, 200, 150, 80, 70, 110, 130, 170, 90, 160, 220, 180]}],
        loading: false,
    },
    async play({canvasElement}) {
        await expect(canvasElement.querySelector(".ks-chart--line")).toBeTruthy()
    },
}

/** Multiple series */
export const MultipleSeries: Story = {
    render: () => ({
        components: {KsLine},
        setup() { return {MONTHS} },
        template: `
            <div style="padding:24px;height:320px">
                <ks-line
                    :categories="MONTHS"
                    :disable-features="['AXIS_SPLITLINE']"
                    :data="[
                        {name: 'Success', data: [120, 200, 150, 80, 70, 110, 130, 170, 90, 160, 220, 180]},
                        {name: 'Failed',  data: [10, 5, 18, 12, 8, 15, 7, 20, 9, 14, 25, 11]},
                        {name: 'Running', data: [5, 10, 8, 15, 12, 6, 9, 14, 11, 7, 18, 13]},
                    ]"
                    :loading="false"
                />
            </div>
        `,
    }),
}

/** Loading state — shown while data is being fetched */
export const Loading: Story = {
    render: () => ({
        components: {KsLine},
        template: "<div style=\"padding:24px;height:300px\"><ks-line :data=\"null\" /></div>",
    }),
    async play({canvasElement}) {
        await expect(canvasElement.querySelector(".ks-chart--line")).toBeTruthy()
    },
}

/** Simulates fetching then populating the chart */
export const AsyncData: Story = {
    render: () => ({
        components: {KsLine},
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
                <div style="height:320px"><ks-line :data="data" :categories="categories" /></div>
            </div>
        `,
    }),
}

/** Custom colors via options override */
export const WithOptionsOverride: Story = {
    render: () => ({
        components: {KsLine},
        setup() { return {MONTHS} },
        template: `
            <div style="padding:24px;height:300px">
                <ks-line
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
export const MiniLine: Story = {
    render: (args) => ({
        components: {KsLine},
        setup() { return {args, MONTHS} },
        template: "<div style=\"padding:24px;width:240px;height:80px\"><ks-line v-bind=\"args\" :categories=\"MONTHS\" /></div>",
    }),
    args: {
        disableFeatures: [...ALL_FEATURES],
        tooltipType: TooltipType.EXTERNAL,
        data: [{name: "Executions", data: [120, 200, 150, 80, 70, 110, 130, 170, 90, 160, 220, 180]}],
        loading: false,
    },
    async play({canvasElement}) {
        await expect(canvasElement.querySelector(".ks-chart-wrapper")).toBeTruthy()
    },
}

/** Compact sparkline — multiple series */
export const MiniMultipleSeries: Story = {
    render: () => ({
        components: {KsLine},
        setup() { return {MONTHS, ALL_FEATURES} },
        template: `
            <div style="padding:24px;width:240px;height:80px">
                <ks-line
                    :disable-features="ALL_FEATURES"
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

/** Taller sparkline */
export const MiniTall: Story = {
    render: () => ({
        components: {KsLine},
        setup() { return {MONTHS, ALL_FEATURES} },
        template: `
            <div style="padding:24px;width:320px;height:120px">
                <ks-line
                    :disable-features="ALL_FEATURES"
                    tooltip-type="external"
                    :categories="MONTHS"
                    :data="[{name: 'Executions', data: [120, 200, 150, 80, 70, 110, 130, 170, 90, 160, 220, 180]}]"
                    :loading="false"
                />
            </div>
        `,
    }),
}

/** Mini loading state */
export const MiniLoading: Story = {
    render: () => ({
        components: {KsLine},
        setup() { return {ALL_FEATURES} },
        template: "<div style=\"padding:24px;width:240px;height:80px\"><ks-line :disable-features=\"ALL_FEATURES\" :data=\"null\" /></div>",
    }),
}

/** Mini async data */
export const MiniAsyncData: Story = {
    render: () => ({
        components: {KsLine},
        setup() {
            const data = ref<null | {name: string; data: number[]}[]>(null)
            const categories = ref<string[]>([])

            function load() {
                data.value = null
                categories.value = []
                setTimeout(() => {
                    categories.value = MONTHS
                    data.value = [{name: "Executions", data: [120, 200, 150, 80, 70, 110, 130, 170, 90, 160, 220, 180]}]
                }, 1500)
            }

            load()
            return {data, categories, load, ALL_FEATURES}
        },
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px;width:240px">
                <button
                    style="width:120px;padding:6px 12px;cursor:pointer;border:1px solid #ccc;border-radius:4px"
                    @click="load"
                >Reload</button>
                <div style="height:80px">
                    <ks-line :disable-features="ALL_FEATURES" tooltip-type="external" :data="data" :categories="categories" />
                </div>
            </div>
        `,
    }),
}

/** Mini line and bar charts embedded in dashboard cards */
export const MiniInCard: Story = {
    render: () => ({
        components: {KsLine, KsBar},
        setup() { return {MONTHS, ALL_FEATURES} },
        template: `
            <div style="padding:24px;display:flex;gap:16px">
                <div style="border:1px solid #e1e3e5;border-radius:8px;padding:16px;width:200px">
                    <div style="font-size:12px;color:#9797a6;margin-bottom:4px">Executions</div>
                    <div style="font-size:24px;font-weight:600;margin-bottom:8px">1,342</div>
                    <div style="height:80px">
                        <ks-line
                            :disable-features="ALL_FEATURES"
                            tooltip-type="external"
                            :categories="MONTHS"
                            :data="[{name: 'Executions', data: [80, 120, 95, 60, 55, 90, 105, 140, 75, 130, 180, 150]}]"
                            :loading="false"
                        />
                    </div>
                </div>
                <div style="border:1px solid #e1e3e5;border-radius:8px;padding:16px;width:200px">
                    <div style="font-size:12px;color:#9797a6;margin-bottom:4px">Failed</div>
                    <div style="font-size:24px;font-weight:600;margin-bottom:8px">87</div>
                    <div style="height:80px">
                        <ks-bar
                            :disable-features="ALL_FEATURES"
                            tooltip-type="external"
                            :categories="MONTHS"
                            :data="[{name: 'Failed', data: [10, 5, 18, 12, 8, 15, 7, 20, 9, 14, 25, 11]}]"
                            :loading="false"
                        />
                    </div>
                </div>
            </div>
        `,
    }),
}

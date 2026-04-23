import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {use} from "echarts/core"
import {BarChart, LineChart} from "echarts/charts"
import KsEchart from "../../../src/components/Charts/KsEchart.vue"
import {TooltipType} from "../../../src/components/Charts/ksChartUtils"

use([BarChart, LineChart])

const CATEGORIES = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul"]

const meta: Meta<typeof KsEchart> = {
    title: "Components/Charts/KsEchart",
    component: KsEchart,
    tags: ["autodocs"],
    argTypes: {
        loading: {control: "boolean"},
        tooltipType: {control: "select", options: ["native", "external"]},
        renderer: {control: "select", options: ["canvas", "svg"]},
        disableFeatures: {
            control: "multi-select",
            options: ["LEGEND", "AXIS", "AXIS_SPLITLINE", "TOOLTIP"],
        },
    },
    parameters: {
        docs: {
            description: {
                component:
                    "KsEchart is the low-level ECharts wrapper. It handles theming, dark-mode detection, " +
                    "an optional external tooltip (KsTooltip), and feature toggles. " +
                    "Higher-level chart components (KsBar, KsLine, KsPie) delegate to it.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsEchart>

// ─── Basic line chart ─────────────────────────────────────────────────────────

export const Default: Story = {
    render: (args) => ({
        components: {KsEchart},
        setup() {
            return {
                args,
                options: {
                    xAxis: {type: "category", data: CATEGORIES, boundaryGap: false},
                    yAxis: {type: "value"},
                    tooltip: {trigger: "axis"},
                    legend: {},
                    series: [
                        {name: "Executions", type: "line", smooth: true, data: [820, 932, 901, 934, 1290, 1330, 1320]},
                        {name: "Failures", type: "line", smooth: true, data: [120, 82, 91, 34, 90, 130, 110]},
                    ],
                },
            }
        },
        template: "<div style=\"padding:24px;height:320px\"><ks-echart v-bind=\"{...args, options}\" /></div>",
    }),
    args: {loading: false, tooltipType: TooltipType.NATIVE},
}

// ─── Bar chart ────────────────────────────────────────────────────────────────

export const SimpleBarChart: Story = {
    render: () => ({
        components: {KsEchart},
        setup() {
            return {
                options: {
                    xAxis: {type: "category", data: CATEGORIES},
                    yAxis: {type: "value"},
                    tooltip: {trigger: "axis", axisPointer: {type: "shadow"}},
                    legend: {},
                    series: [
                        {name: "Succeeded", type: "bar", stack: "total", data: [320, 420, 380, 410, 520, 480, 390]},
                        {name: "Failed", type: "bar", stack: "total", data: [80, 60, 90, 70, 40, 55, 65]},
                        {name: "Killed", type: "bar", stack: "total", data: [10, 20, 15, 25, 18, 22, 12]},
                    ],
                },
            }
        },
        template: "<div style=\"padding:24px;height:320px\"><ks-echart :options=\"options\" /></div>",
    }),
}

// ─── Mixed bar + area with multiple Y axes ────────────────────────────────────

export const MixedBarAndAreaMultipleYAxes: Story = {
    render: () => ({
        components: {KsEchart},
        setup() {
            return {
                options: {
                    xAxis: {type: "category", data: CATEGORIES, boundaryGap: true},
                    yAxis: [
                        {type: "value", name: "Executions", position: "left"},
                        {type: "value", name: "Duration (s)", position: "right"},
                    ],
                    tooltip: {trigger: "axis"},
                    legend: {},
                    series: [
                        {
                            name: "Executions",
                            type: "bar",
                            yAxisIndex: 0,
                            data: [320, 420, 380, 410, 520, 480, 390],
                        },
                        {
                            name: "Avg Duration",
                            type: "line",
                            yAxisIndex: 1,
                            smooth: true,
                            areaStyle: {opacity: 0.2},
                            data: [12.4, 9.8, 14.2, 11.1, 8.7, 13.5, 10.3],
                        },
                    ],
                },
            }
        },
        template: "<div style=\"padding:24px;height:320px\"><ks-echart :options=\"options\" tooltip-type=\"external\" /></div>",
    }),
    parameters: {
        docs: {
            description: {
                story:
                    "Bar series (left y-axis for count) and an area-line series (right y-axis for duration). " +
                    "Pass the full ECharts option directly to KsEchart when you need a mixed chart type.",
            },
        },
    },
}

// ─── Mixed bar + area — three Y axes ─────────────────────────────────────────

export const TripleYAxis: Story = {
    render: () => ({
        components: {KsEchart},
        setup() {
            return {
                options: {
                    xAxis: {type: "category", data: CATEGORIES, boundaryGap: true},
                    yAxis: [
                        {type: "value", name: "Runs", position: "left", offset: 0},
                        {type: "value", name: "Errors", position: "right", offset: 0},
                        {type: "value", name: "Latency (ms)", position: "right", offset: 70},
                    ],
                    tooltip: {trigger: "axis"},
                    legend: {},
                    grid: {right: "15%"},
                    series: [
                        {
                            name: "Runs",
                            type: "bar",
                            yAxisIndex: 0,
                            data: [420, 380, 510, 430, 600, 550, 490],
                        },
                        {
                            name: "Errors",
                            type: "bar",
                            yAxisIndex: 1,
                            data: [12, 8, 20, 10, 5, 15, 9],
                        },
                        {
                            name: "Latency",
                            type: "line",
                            yAxisIndex: 2,
                            smooth: true,
                            areaStyle: {opacity: 0.15},
                            data: [240, 210, 290, 230, 180, 260, 200],
                        },
                    ],
                },
            }
        },
        template: "<div style=\"padding:24px;height:360px\"><ks-echart :options=\"options\" /></div>",
    }),
    parameters: {
        docs: {
            description: {
                story: "Three independent y-axes: runs (bar, left), errors (bar, right-inner), latency ms (area, right-outer).",
            },
        },
    },
}

// ─── External tooltip ─────────────────────────────────────────────────────────

export const ExternalTooltip: Story = {
    render: () => ({
        components: {KsEchart},
        setup() {
            const seriesData = [
                {name: "Succeeded", data: [820, 932, 901, 934, 1290, 1330, 1320]},
                {name: "Failed", data: [120, 82, 91, 34, 90, 130, 110]},
            ]
            return {
                options: {
                    xAxis: {type: "category", data: CATEGORIES, boundaryGap: false},
                    yAxis: {type: "value"},
                    legend: {},
                    tooltip: {trigger: "axis"},
                    series: seriesData.map((s) => ({...s, type: "line", smooth: true})),
                },
            }
        },
        template: `
            <div style="padding:24px;height:280px">
                <ks-echart
                    :options="options"
                    tooltip-type="external"
                />
            </div>
        `,
    }),
    parameters: {
        docs: {
            description: {
                story:
                    "With tooltipType=\"external\" the native ECharts tooltip is suppressed and KsTooltip is used instead. " +
                    "Move your cursor over the chart area to see the external tooltip.",
            },
        },
    },
}

// ─── Loading state ────────────────────────────────────────────────────────────

export const Loading: Story = {
    render: () => ({
        components: {KsEchart},
        setup() {
            return {
                options: {
                    xAxis: {type: "category", data: CATEGORIES},
                    yAxis: {type: "value"},
                    series: [],
                },
            }
        },
        template: "<div style=\"padding:24px;height:280px\"><ks-echart :options=\"options\" :loading=\"true\" /></div>",
    }),
}

// ─── Disabled features ────────────────────────────────────────────────────────

export const DisabledAxis: Story = {
    render: () => ({
        components: {KsEchart},
        setup() {
            return {
                options: {
                    xAxis: {type: "category", data: CATEGORIES, boundaryGap: false},
                    yAxis: {type: "value"},
                    series: [
                        {name: "Executions", type: "line", smooth: true, areaStyle: {opacity: 0.15}, data: [820, 932, 901, 934, 1290, 1330, 1320]},
                    ],
                },
            }
        },
        template: `
            <div style="padding:24px;height:120px">
                <ks-echart
                    :options="options"
                    :disable-features="['LEGEND', 'AXIS', 'AXIS_SPLITLINE', 'TOOLTIP']"
                />
            </div>
        `,
    }),
    parameters: {
        docs: {
            description: {
                story: "All non-data features disabled — ideal for sparkline / mini-chart usage.",
            },
        },
    },
}

// ─── SVG renderer ─────────────────────────────────────────────────────────────

export const SvgRenderer: Story = {
    render: () => ({
        components: {KsEchart},
        setup() {
            return {
                options: {
                    xAxis: {type: "category", data: CATEGORIES, boundaryGap: false},
                    yAxis: {type: "value"},
                    tooltip: {trigger: "axis"},
                    series: [
                        {name: "Executions", type: "line", data: [820, 932, 901, 934, 1290, 1330, 1320]},
                    ],
                },
            }
        },
        template: "<div style=\"padding:24px;height:280px\"><ks-echart :options=\"options\" renderer=\"svg\" /></div>",
    }),
}

<template>
    <Header :title="dashboard.title" :breadcrumb />

    <div class="p-3">
        <KestraFilter
            prefix="custom_dashboard"
            :include="['relative_date', 'absolute_date']"
            :refresh="{shown: true, callback: () => {}}"
        />

        <el-row class="custom">
            <el-col
                v-for="(chart, index) in dashboard.charts"
                :key="index"
                :xs="24"
                :sm="12"
            >
                <div class="p-4">
                    <p class="m-0 fs-6 fw-bold">
                        {{ chart.chartOptions.displayName }}
                    </p>
                    <p class="m-0 fw-light small">
                        {{ chart.chartOptions.description }}
                    </p>

                    <component
                        :is="TYPES[chart.type]"
                        :source="chart.content"
                        :chart
                    />
                </div>
            </el-col>
        </el-row>
    </div>
</template>

<script lang="ts" setup>
    import {onMounted, ref} from "vue";

    import Header from "./components/Header.vue";
    import KestraFilter from "../filter/KestraFilter.vue";

    import TimeSeries from "./components/charts/custom/TimeSeries.vue";
    import Markdown from "../layout/Markdown.vue";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    const breadcrumb = [
        {
            label: t("custom_dashboards"),
            link: {name: "dashboards/list"},
        },
    ];

    const TYPES = {
        "io.kestra.plugin.core.dashboard.chart.TimeSeries": TimeSeries,
        "io.kestra.plugin.core.dashboard.chart.Markdown": Markdown,
    };

    const dashboard = ref({});
    onMounted(() => {
        // TODO: Fetch proper data from server and assign it to the variable below
        dashboard.value = {
            title: "Executions per country",
            description: "Count executions per country label and execution state",
            timeWindow: {
                default: "P30D",
                max: "P365D",
            },
            charts: [
                {
                    id: "timeseries_executions",
                    type: "io.kestra.plugin.core.dashboard.chart.TimeSeries",
                    chartOptions: {
                        displayName: "Executions per country over time",
                        description:
                            "Count executions per country label and execution",
                        tooltip: "ALL", // ALL, NONE, SINGLE
                        legend: {
                            enabled: true, // Extendable with position: AUTO, LEFT, RIGHT, TOP, BOTTOM
                        },
                        column: "date",
                        colorByColumn: "country",
                    },
                    data: {
                        type: "io.kestra.plugin.core.dashboard.data.Executions",
                        columns: {
                            date: {
                                field: "START_DATE",
                                displayName: "Execution Date",
                            },
                            country: {
                                field: "LABELS",
                                labelKey: "country",
                            },
                            state: {
                                field: "STATE",
                            },
                            total: {
                                displayName: "Total Executions",
                                agg: "COUNT",
                                graphStyle: "BARS", // LINES, BARS, POINTS
                            },
                            duration: {
                                displayName: "Duration",
                                field: "DURATION",
                                agg: "SUM",
                                graphStyle: "LINES", // LINES, BARS, POINTS
                            },
                        },
                        where: [
                            {
                                field: "NAMESPACE",
                                type: "IN",
                                values: ["dev_graph", "prod_graph"],
                            },
                        ],
                    },
                },
                {
                    id: "timeseries_executions_ns",
                    type: "io.kestra.plugin.core.dashboard.chart.TimeSeries",
                    chartOptions: {
                        displayName: "State of executions over time",
                        description: "Count executions per execution state",
                        tooltip: "ALL", // ALL, NONE, SINGLE
                        legend: {
                            enabled: true, // Extendable with position: AUTO, LEFT, RIGHT, TOP, BOTTOM
                        },
                        column: "date",
                        colorByColumn: "state",
                    },
                    data: {
                        type: "io.kestra.plugin.core.dashboard.data.Executions",
                        columns: {
                            namespace: {
                                field: "NAMESPACE",
                            },
                            date: {
                                field: "START_DATE",
                                displayName: "Execution Date",
                            },
                            total: {
                                displayName: "Total Executions",
                                agg: "COUNT",
                                graphStyle: "BARS", // LINES, BARS, POINTS
                            },
                            country: {
                                field: "LABELS",
                                labelKey: "country",
                            },
                            state: {
                                field: "STATE",
                            },
                            duration: {
                                displayName: "Duration",
                                field: "DURATION",
                                agg: "SUM",
                                graphStyle: "LINES", // LINES, BARS, POINTS
                            },
                        },
                        where: [
                            {
                                field: "NAMESPACE",
                                type: "IN",
                                values: ["dev_graph", "prod_graph"],
                            },
                        ],
                    },
                },
                {
                    id: "markdown_section",
                    type: "io.kestra.plugin.core.dashboard.chart.Markdown",
                    chartOptions: {
                        displayName: "Executions per country over time",
                        description:
                            "Count executions per country label and execution state",
                    },
                    content: "## This is a markdown panel",
                },
                {
                    id: "table_logs",
                    type: "io.kestra.plugin.core.dashboard.chart.Table",
                    chartOptions: {
                        displayName: "Log count by level for filtered namespace",
                    },
                    data: {
                        type: "io.kestra.plugin.core.dashboard.data.Logs",
                        columns: {
                            level: {
                                field: "LEVEL",
                            },
                            count: {
                                agg: "COUNT",
                            },
                        },
                        where: [
                            {
                                field: "NAMESPACE",
                                type: "IN",
                                values: ["dev_graph", "prod_graph"],
                            },
                        ],
                    },
                },
                {
                    id: "table_executions",
                    type: "io.kestra.plugin.core.dashboard.chart.Table",
                    chartOptions: {
                        displayName: "Executions per country, state and date",
                    },
                    data: {
                        type: "io.kestra.plugin.core.dashboard.data.Executions",
                        columns: {
                            date: {
                                field: "START_DATE",
                                displayName: "Execution Date",
                            },
                            country: {
                                field: "LABELS",
                                labelKey: "country",
                            },
                            state: {
                                field: "STATE",
                            },
                            duration: {
                                displayName: "Executions duration",
                                field: "DURATION",
                                agg: "SUM",
                            },
                            total: {
                                displayName: "Total Executions",
                                agg: "COUNT",
                            },
                        },
                        where: [
                            {
                                field: "NAMESPACE",
                                type: "IN",
                                values: ["dev_graph", "prod_graph"],
                            },
                        ],
                        orderBy: {
                            date: "ASC",
                            duration: "DESC",
                        },
                    },
                },
                {
                    id: "table_metrics",
                    type: "io.kestra.plugin.core.dashboard.chart.Table",
                    chartOptions: {
                        displayName: "Sum of sales per namespace",
                    },
                    data: {
                        type: "io.kestra.plugin.core.dashboard.data.Metrics",
                        columns: {
                            namespace: {
                                field: "NAMESPACE",
                            },
                            value: {
                                field: "VALUE",
                                agg: "SUM",
                            },
                        },
                        where: [
                            {
                                field: "NAME",
                                type: "EQUAL_TO",
                                value: "sales_count",
                            },
                            {
                                field: "NAMESPACE",
                                type: "IN",
                                values: ["dev_graph", "prod_graph"],
                            },
                        ],
                        orderBy: {
                            value: "DESC",
                        },
                    },
                },
            ],
        };
    });
</script>

<style lang="scss" scoped>
@import "@kestra-io/ui-libs/src/scss/variables";

$spacing: 20px;

.custom.el-row {
    width: 100%;

    & .el-col {
        padding-bottom: $spacing;

        &:nth-of-type(even) div {
            margin-left: 1rem;
        }

        & > div {
            height: 100%;
            background: var(--card-bg);
            border: 1px solid var(--bs-gray-300);
            border-radius: $border-radius;

            html.dark & {
                border-color: var(--bs-gray-600);
            }
        }
    }
}
</style>

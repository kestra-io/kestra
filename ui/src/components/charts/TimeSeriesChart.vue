<template>
    <TimeSeries
        :chart="mappedChart(props.flowId, props.namespace)"
        showDefault
        short
    />
</template>

<script setup lang="ts">
    import TimeSeries from "../dashboard/sections/TimeSeries.vue";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";

    interface ChartDefinition {
        id: string;
        type: string;
        chartOptions: {
            displayName: string;
            description: string;
            legend: { enabled: boolean };
            column: string;
            colorByColumn: string;
            width: number;
        };
        data: {
            type: string;
            columns: {
                date: { field: string; displayName: string };
                state: { field: string };
                total: { displayName: string; agg: string };
                duration: { field: string; displayName: string; agg: string };
            };
            where: { field: string; type: string; value: string }[];
        };
        content?: string;
    }

    // Props coming from parent
    const props = defineProps<{
        flowId: string;
        namespace: string;
    }>();

    // Chart base definition
    const CHART_DEFINITION: ChartDefinition = {
        id: "total_executions_timeseries",
        type: "io.kestra.plugin.core.dashboard.chart.TimeSeries",
        chartOptions: {
            displayName: "Total Executions",
            description: "Executions duration and count per date",
            legend: {enabled: false},
            column: "date",
            colorByColumn: "state",
            width: 12,
        },
        data: {
            type: "io.kestra.plugin.core.dashboard.data.Executions",
            columns: {
                date: {field: "START_DATE", displayName: "Date"},
                state: {field: "STATE"},
                total: {displayName: "Executions", agg: "COUNT"},
                duration: {field: "DURATION", displayName: "Duration", agg: "SUM"},
            },
            where: [
                {field: "NAMESPACE", type: "EQUAL_TO", value: "${namespace}"},
                {field: "FLOW_ID", type: "EQUAL_TO", value: "${flow_id}"},
            ],
        },
    };
    CHART_DEFINITION.content = YAML_UTILS.stringify(CHART_DEFINITION);

    // Dynamic chart generator
    function mappedChart(id: string, namespace: string) {
        const chart = JSON.parse(JSON.stringify(CHART_DEFINITION));
        chart.content = chart.content
            .replace("${namespace}", namespace)
            .replace("${flow_id}", id);
        return chart;
    }
</script>


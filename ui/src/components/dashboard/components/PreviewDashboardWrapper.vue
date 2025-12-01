<template>
    <div class="w-100 p-4">
        <Sections
            :key="dashboardStore.sourceCode"
            :dashboard="{id: 'default', charts: []}"
            :charts="charts.map(chart => chart.data).filter(chart => chart !== null)"
            showDefault
        />
    </div>
</template>

<script lang="ts" setup>
    import {ref, watch} from "vue";
    import Sections from "../sections/Sections.vue";
    import {Chart} from "../types.ts";
    import {useDashboardStore} from "../../../stores/dashboard";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import throttle from "lodash/throttle";

    interface Result {
        error: string[] | null;
        data: Chart | null;
        raw: any;
    }

    const charts = ref<Result[]>([])

    const dashboardStore = useDashboardStore();

    const validateAndLoadAllChartsThrottled = throttle(validateAndLoadAllCharts, 500);

    async function validateAndLoadAllCharts() {
        const allCharts = YAML_UTILS.getAllCharts(dashboardStore.sourceCode) ?? [];
        charts.value = await Promise.all(allCharts.map(async (chart: any) => {
            return loadChart(chart);
        }));
    }

    watch(
        () => dashboardStore.sourceCode,
        () => {
            validateAndLoadAllChartsThrottled();
        }
        , {immediate: true}
    );



    async function loadChart(chart: any) {
        const yamlChart = YAML_UTILS.stringify(chart);
        const result: Result = {
            error: null,
            data: null,
            raw: {}
        };
        const errors = await dashboardStore.validateChart(yamlChart);
        if (errors.constraints) {
            result.error = errors.constraints;
        } else {
            result.data = {...chart, content: yamlChart, raw: chart};
        }
        return result;
    }
</script>

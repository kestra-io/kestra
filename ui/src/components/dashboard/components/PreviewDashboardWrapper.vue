<template>
    <div class="w-100 p-4">
        <Sections
            :dashboard="{id: 'default', charts: []}"
            :charts="charts.map(chart => chart.data).filter(chart => chart !== null)"
            showDefault
        />
    </div>
</template>

<script lang="ts" setup>
    import {onMounted, ref} from "vue";
    import Sections from "../sections/Sections.vue";
    import {Chart} from "../composables/useDashboards";
    import {useDashboardStore} from "../../../stores/dashboard";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";

    interface Result {
        error: string[] | null;
        data: Chart | null;
        raw: any;
    }

    const charts = ref<Result[]>([])

    onMounted(async () => {
        validateAndLoadAllCharts();
    });

    const dashboardStore = useDashboardStore();

    function validateAndLoadAllCharts() {
        charts.value = [];
        const allCharts = YAML_UTILS.getAllCharts(dashboardStore.sourceCode) ?? [];
        allCharts.forEach(async (chart: any) => {
            const loadedChart = await loadChart(chart);
            charts.value.push(loadedChart);
        });
    }

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

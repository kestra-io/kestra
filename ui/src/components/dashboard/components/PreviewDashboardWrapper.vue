<template>
    <div class="w-100 p-4">
        <Sections
            :dashboard="{id: 'default', charts: []}"
            :charts="chartData"
            showDefault
        />
    </div>
</template>

<script lang="ts" setup>
    import {computed, onBeforeUnmount, ref, watch} from "vue";
    import Sections from "../sections/Sections.vue";
    import {Chart} from "../types.ts";
    import {useDashboardStore} from "../../../stores/dashboard";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import debounce from "lodash/debounce";

    interface Result {
        error: string[] | null;
        data: Chart | null;
        raw: any;
    }

    const charts = ref<Result[]>([]);

    const dashboardStore = useDashboardStore();

    const chartData = computed(() => charts.value.map((chart) => chart.data).filter((chart) => chart !== null));

    const validateAndLoadAllChartsDebounced = debounce(
        (allCharts: any[]) => validateAndLoadAllCharts(allCharts),
        500
    );

    async function validateAndLoadAllCharts(allCharts: any[]) {
        charts.value = await Promise.all(allCharts.map(async (chart: any) => {
            return loadChart(chart);
        }));
    }

    const parsedCharts = computed(() => YAML_UTILS.getAllCharts(dashboardStore.sourceCode) ?? []);
    const chartsSignature = computed(() => parsedCharts.value.map((chart: any) => YAML_UTILS.stringify(chart)).join("\n---\n"));

    watch(
        chartsSignature,
        () => {
            validateAndLoadAllChartsDebounced(parsedCharts.value);
        },
        {immediate: true}
    );

    onBeforeUnmount(() => {
        validateAndLoadAllChartsDebounced.cancel();
    });
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

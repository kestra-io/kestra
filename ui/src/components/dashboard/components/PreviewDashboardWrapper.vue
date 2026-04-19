<template>
    <div class="w-100 p-4">
        <Sections
            :key="previewSectionsKey"
            :dashboard="{id: 'default', charts: []}"
            :charts="charts.map(chart => chart.data).filter(chart => chart !== null)"
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

    const charts = ref<Result[]>([])

    const dashboardStore = useDashboardStore();
    const previewSectionsKey = computed(() => {
        const id = dashboardStore.parsedSource?.id;
        return typeof id === "string" && id.length > 0 ? id : "dashboard-preview";
    });

    // Wait until editing pauses so the preview does not flicker on every keystroke
    // (no-code and YAML both write `sourceCode` continuously while typing).
    const debouncedValidateAndLoadAllCharts = debounce(validateAndLoadAllCharts, 600);

    async function validateAndLoadAllCharts() {
        const allCharts = YAML_UTILS.getAllCharts(dashboardStore.sourceCode) ?? [];
        charts.value = await Promise.all(allCharts.map(async (chart: any) => {
            return loadChart(chart);
        }));
    }

    let initialPreviewLoadDone = false;

    watch(
        () => dashboardStore.sourceCode,
        () => {
            if (!initialPreviewLoadDone) {
                initialPreviewLoadDone = true;
                void validateAndLoadAllCharts();
                return;
            }
            debouncedValidateAndLoadAllCharts();
        },
        {immediate: true}
    );

    onBeforeUnmount(() => {
        debouncedValidateAndLoadAllCharts.cancel();
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

<template>
    <div class="w-100 p-4">
        <Sections
            v-memo="[chartMemoKey]"
            :dashboard="{id: 'default', charts: []}"
            :charts="chartData"
            showDefault
        />
    </div>
</template>

<script lang="ts" setup>
    import {computed, onActivated, onBeforeUnmount, onDeactivated, ref, watch} from "vue";
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
    const lastLoadedChartsSignature = ref("");
    const previewTabActive = ref(true);

    const dashboardStore = useDashboardStore();

    const previewSource = ref(dashboardStore.sourceCode);

    const syncPreviewSourceFromStore = debounce(() => {
        previewSource.value = dashboardStore.sourceCode;
    }, 900);

    const chartData = computed(() => charts.value.map((chart) => chart.data).filter((chart) => chart !== null));

    const chartMemoKey = computed(() =>
        charts.value.map((r) => `${r.data?.content ?? ""}::${JSON.stringify(r.error)}`).join("|")
    );

    /** Stable signature so key order / stringify noise does not retrigger loads. */
    function stableStringify(obj: unknown): string {
        if (obj === null || typeof obj !== "object") {
            return JSON.stringify(obj);
        }
        if (Array.isArray(obj)) {
            return `[${obj.map((item) => stableStringify(item)).join(",")}]`;
        }
        const record = obj as Record<string, unknown>;
        const keys = Object.keys(record).sort();
        return `{${keys.map((k) => `${JSON.stringify(k)}:${stableStringify(record[k])}`).join(",")}}`;
    }

    const chartValidationCache = new Map<string, {constraints?: unknown}>();

    function resultsSignature(results: Result[]): string {
        return JSON.stringify(
            results.map((r) => ({
                error: r.error,
                content: r.data?.content ?? null
            }))
        );
    }

    async function validateChartCached(yamlChart: string) {
        const cached = chartValidationCache.get(yamlChart);
        if (cached !== undefined) {
            return cached;
        }
        const errors = await dashboardStore.validateChart(yamlChart, {silent: true});
        if (chartValidationCache.size > 80) {
            chartValidationCache.clear();
        }
        chartValidationCache.set(yamlChart, errors);
        return errors;
    }

    async function validateAndLoadAllCharts(allCharts: any[]) {
        const results = await Promise.all(
            allCharts.map(async (chart: any) => loadChart(chart))
        );
        const nextSig = resultsSignature(results);
        if (nextSig === lastLoadedChartsSignature.value) {
            return;
        }
        lastLoadedChartsSignature.value = nextSig;
        charts.value = results;
    }

    const parsedCharts = computed(() => YAML_UTILS.getAllCharts(previewSource.value) ?? []);
    const chartsSignature = computed(() =>
        (parsedCharts.value as unknown[]).map((chart) => stableStringify(chart)).join("\n---\n")
    );

    watch(
        () => dashboardStore.sourceCode,
        () => {
            syncPreviewSourceFromStore();
        }
    );

    watch(
        chartsSignature,
        () => {
            if (!previewTabActive.value) {
                return;
            }
            void validateAndLoadAllCharts(parsedCharts.value);
        },
        {immediate: true}
    );

    onDeactivated(() => {
        previewTabActive.value = false;
        syncPreviewSourceFromStore.cancel();
    });

    onActivated(() => {
        previewTabActive.value = true;
        syncPreviewSourceFromStore.cancel();
        previewSource.value = dashboardStore.sourceCode;
        void validateAndLoadAllCharts(parsedCharts.value);
    });

    onBeforeUnmount(() => {
        syncPreviewSourceFromStore.cancel();
    });

    async function loadChart(chart: any) {
        const yamlChart = YAML_UTILS.stringify(chart);
        const result: Result = {
            error: null,
            data: null,
            raw: {}
        };
        const errors = await validateChartCached(yamlChart);
        if (errors.constraints) {
            result.error = errors.constraints;
        } else {
            result.data = {...chart, content: yamlChart, raw: chart};
        }
        return result;
    }
</script>

<template>
    <section v-if="data" id="kpi" :class="`kpi--${color}`">
        <span class="title">{{ getChartTitle(props.chart!) }}</span>
        <p class="value">
            {{ getPropertyValue(data, "value") }}{{ percentageShown ? "%" : "" }}
        </p>
        <p v-if="rawCount" class="raw-count">
            {{ rawCount }}
        </p>
    </section>

    <KsEmpty v-else :description="EMPTY_TEXT" />
</template>

<script setup lang="ts">
    import {PropType, computed, ref, watch} from "vue";

    import {Chart} from "../composables/useDashboards";
    import {getChartTitle, getPropertyValue, useChartGenerator} from "../composables/useDashboards";

    import {useRoute} from "vue-router";
    import {decodeSearchParams, State} from "@kestra-io/design-system";
    import {useExecutionsStore} from "../../../stores/executions";
    import {FilterObject} from "../../../utils/filters";

    const VALID_EXECUTION_STATES = new Set<string>(
        State.arrayAllStates().map((s: {name: string}) => s.name),
    );

    const props = defineProps({
        dashboardId: {type: String, required: false, default: undefined},
        chart: {type: Object as PropType<Chart>, required: true},
        filters: {type: Array as PropType<FilterObject[]>, default: () => []},
        showDefault: {type: Boolean, default: false},
    });

    const route = useRoute();
    const executionsStore = useExecutionsStore();
    const {percentageShown, EMPTY_TEXT, data, generate} = useChartGenerator(props.dashboardId, {...props});

    const color = computed(() => {
        const value = (props.chart?.chartOptions as Record<string, unknown> | undefined)?.color;
        return typeof value === "string" ? value.toLowerCase() : "default";
    });

    const numeratorStates = computed<string[] | undefined>(() => {
        const numerator = (props.chart?.data as Record<string, any> | undefined)?.numerator as
            | Array<{ field?: string; values?: string[] }>
            | undefined;
        if (!Array.isArray(numerator)) return undefined;
        const stateFilter = numerator.find((f) => f?.field === "STATE");
        if (!Array.isArray(stateFilter?.values)) return undefined;
        const sanitized = stateFilter!.values.filter((v) => VALID_EXECUTION_STATES.has(v));
        return sanitized.length > 0 ? sanitized : undefined;
    });

    const rawCount = ref<string | undefined>(undefined);
    let refreshGeneration = 0;

    function timeWindowFilters(): Record<string, string> {
        const params: Record<string, string> = {};
        const decoded = decodeSearchParams(route.query) ?? [];
        const timeRange = decoded.find((f: any) => f?.field === "timeRange");
        const startDate = decoded.find((f: any) => f?.field === "startDate");
        const endDate = decoded.find((f: any) => f?.field === "endDate");
        if (timeRange?.value) params["filters[timeRange][EQUALS]"] = String(timeRange.value);
        if (startDate?.value) params["filters[startDate][GREATER_THAN_OR_EQUAL_TO]"] = String(startDate.value);
        if (endDate?.value) params["filters[endDate][LESS_THAN_OR_EQUAL_TO]"] = String(endDate.value);
        return params;
    }

    async function refreshRawCount() {
        if (!numeratorStates.value || numeratorStates.value.length === 0) {
            rawCount.value = undefined;
            return;
        }
        const generation = ++refreshGeneration;
        const flowFilter = props.filters.find((f) => f.field === "flowId");
        const namespaceFilter = props.filters.find((f) => f.field === "namespace");
        const baseParams: Record<string, any> = {
            ...timeWindowFilters(),
            size: 1,
            page: 1,
            commit: false,
        };
        if (namespaceFilter?.value) {
            baseParams["filters[namespace][EQUALS]"] = namespaceFilter.value;
        }
        if (flowFilter?.value) {
            baseParams["filters[flowId][EQUALS]"] = flowFilter.value;
        }

        try {
            const [matching, all] = await Promise.all([
                executionsStore.findExecutions({
                    ...baseParams,
                    "filters[state][IN]": numeratorStates.value.join(","),
                }),
                executionsStore.findExecutions(baseParams),
            ]);
            if (generation !== refreshGeneration) return;
            const num = matching?.total ?? 0;
            const denom = all?.total ?? 0;
            rawCount.value = denom > 0 ? `${num} / ${denom}` : undefined;
        } catch {
            if (generation !== refreshGeneration) return;
            rawCount.value = undefined;
        }
    }

    function refresh() {
        refreshRawCount();
        return generate();
    }

    defineExpose({refresh});

    watch(
        [() => route.params.filters, () => route.query],
        () => refresh(),
        {deep: true, immediate: true},
    );
</script>

<style scoped lang="scss">
    section#kpi {
        height: 100%;
        display: flex;
        flex-direction: column;
        justify-content: center;
        align-items: center;
        text-align: center;
        gap: 0.25rem;

        .title {
            font-size: var(--ks-font-size-sm);
            color: var(--ks-content-secondary);
            font-weight: 500;
        }

        .value {
            margin: 0;
            font-size: 2.5rem;
            font-weight: 700;
            line-height: 1.1;
            color: var(--ks-content-primary);
        }

        .raw-count {
            margin: 0;
            font-size: var(--ks-font-size-xs);
            color: var(--ks-content-tertiary);
            font-family: monospace;
        }

        &.kpi--success .value {
            color: var(--ks-content-success);
        }

        &.kpi--danger .value,
        &.kpi--failure .value {
            color: var(--ks-content-error);
        }

        &.kpi--warning .value {
            color: var(--ks-content-warning);
        }

        &.kpi--info .value {
            color: var(--ks-content-link);
        }
    }
</style>

<template>
    <KSFilter
        :configuration="flowMetricFilter"
        :prefix="'flow-metrics'"
        :tableOptions="{
            chart: {shown: false},
            columns: {shown: false},
            refresh: {shown: true, callback: load}
        }"
        :defaultScope="false"
        :defaultTimeRange="false"
    />

    <div v-bind="$attrs">
        <KsCard>
            <KsBar
                v-if="flowStore.aggregatedMetrics"
                class="chart"
                :data="seriesData"
                :categories="categories"
                :loading="isLoading"
            />
            <span v-else>
                <KsAlert type="info" :closable="false">
                    {{ $t("metric choice") }}
                </KsAlert>
            </span>
        </KsCard>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue";
    import {useRoute, useRouter} from "vue-router";
    import moment from "moment";
    import {useI18n} from "vue-i18n";
    import {useFlowStore} from "../../stores/flow";
    import {getFormat} from "../dashboard/composables/charts";
    import {cssVar} from "@kestra-io/ui-design-system"
    import {KsBar} from "@kestra-io/ui-design-system";
    import type {KsChartSeriesItem} from "@kestra-io/ui-design-system";
    import {KsFilter as KSFilter} from "@kestra-io/ui-design-system";
    import {useFlowMetricFilter} from "../filter/configurations";

    defineOptions({
        name: "FlowMetrics",
        inheritAttrs: false,
    });

    const route = useRoute();
    const router = useRouter();
    const {t} = useI18n();

    const flowMetricFilter = useFlowMetricFilter();
    const flowStore = useFlowStore();

    const isLoading = ref(false);

    interface MetricAggregation {
        date: string;
        value?: number;
    }

    const display = computed(() => {
        return route.query.metric && route.query.aggregation;
    });

    const categories = computed(() => {
        const aggregations = (flowStore.aggregatedMetrics?.aggregations ?? []) as MetricAggregation[];
        const groupBy = flowStore.aggregatedMetrics?.groupBy;
        return aggregations.map((e) => moment(e.date).format(getFormat(groupBy)));
    });

    const seriesData = computed<KsChartSeriesItem[]>(() => {
        if (!display.value) return [];

        const aggregations = (flowStore.aggregatedMetrics?.aggregations ?? []) as MetricAggregation[];
        const aggregationQuery = route.query.aggregation;
        const aggregationValue = Array.isArray(aggregationQuery)
            ? aggregationQuery[0]
            : aggregationQuery;
        const aggregationLabel = aggregationValue?.toLowerCase() ?? "";

        return [
            {
                name: `${t(aggregationLabel)} ${t("of")} ${route.query.metric}`,
                data: aggregations.map((e) => e.value ?? 0),
                itemStyle: {color: cssVar("--kel-color-success")},
            },
        ];
    });

    function loadMetrics(): void {
        const params = route.params as { namespace: string; id: string };

        flowStore.loadTasksWithMetrics({
            namespace: params.namespace,
            id: params.id,
        });

        const taskId = route.query.task as string | undefined;

        if (taskId) {
            flowStore.loadTaskMetrics({
                namespace: params.namespace,
                id: params.id,
                taskId: taskId,
            }).then(handleMetricsLoaded);
        } else {
            flowStore.loadFlowMetrics({
                namespace: params.namespace,
                id: params.id,
            }).then(handleMetricsLoaded);
        }
    }

    function handleMetricsLoaded(): void {
        if ((flowStore.metrics?.length ?? -1) > 0) {
            if (
                route.query.metric &&
                !flowStore.metrics?.includes(route.query.metric as string)
            ) {
                const query = {...route.query};
                delete query.metric;

                router
                    .push({query: query})
                    .then(() => loadAggregatedMetrics());
            } else {
                loadAggregatedMetrics();
            }
        }
    }

    function loadAggregatedMetrics(): void {
        isLoading.value = true;

        if (display.value) {
            const params = route.params as { namespace: string; id: string };
            const metric = route.query.metric as string;
            const taskId = route.query.task as string | undefined;

            if (taskId) {
                flowStore.loadTaskAggregatedMetrics({
                    namespace: params.namespace,
                    id: params.id,
                    taskId: taskId,
                    metric: metric,
                });
            } else {
                flowStore.loadFlowAggregatedMetrics({
                    namespace: params.namespace,
                    id: params.id,
                    metric: metric,
                });
            }
        } else {
            flowStore.aggregatedMetrics = undefined;
        }
        isLoading.value = false;
    }

    function load(): void {
        if (!route.query.metric) {
            loadMetrics();
        } else {
            loadAggregatedMetrics();
        }
    }

    // Watch for route query changes
    watch(
        () => route.query,
        (query) => {
            if (!query.metric) {
                loadMetrics();
            } else {
                loadAggregatedMetrics();
            }
        },
    );

    // Initial load (equivalent to created hook)
    loadMetrics();
</script>

<style scoped lang="scss">
    .chart {
        height: 231px;
    }
</style>


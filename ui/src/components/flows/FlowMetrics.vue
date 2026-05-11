<template>
    <KSFilter
        :configuration="flowMetricFilter"
        :prefix="'flow-metrics'"
        :tableOptions="{
            chart: {shown: false},
            columns: {shown: false},
            refresh: {shown: true, callback: load}
        }"
        :buttons="{savedFilters: {shown: false}, tableOptions: {shown: false}}"
        :defaultScope="false"
        :defaultTimeRange="false"
    >
        <template #extra>
            <div class="metric-controls">
                <KsSegmented
                    :modelValue="currentAggregation"
                    :options="aggregationOptions"
                    @change="setAggregation"
                />
                <KsSegmented
                    :modelValue="currentChartType"
                    :options="chartTypeOptions"
                    @change="setChartType"
                />
            </div>
        </template>
    </KSFilter>

    <div v-bind="$attrs">
        <KsRow v-if="displayedMetrics.length > 0" :gutter="16">
            <KsCol
                :md="12"
                :lg="8"
                v-for="metric in displayedMetrics"
                :key="metric"
            >
                <KsCard class="metric-chart-card">
                    <div class="metric-title">
                        {{ metric }}
                    </div>
                    <KsBar
                        v-if="currentChartType === 'bar'"
                        class="chart"
                        :data="getSeriesData(metric)"
                        :categories="getCategories(metric)"
                        :loading="isLoading"
                    />
                    <KsLine
                        v-else
                        class="chart"
                        :data="getSeriesData(metric)"
                        :categories="getCategories(metric)"
                        :loading="isLoading"
                    />
                </KsCard>
            </KsCol>
        </KsRow>
        <KsCard v-else-if="!isLoading">
            <KsAlert type="info" :closable="false">
                {{ $t("metric choice") }}
            </KsAlert>
        </KsCard>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import moment from "moment"
    import {useI18n} from "vue-i18n"
    import {useFlowStore} from "../../stores/flow"
    import {getFormat} from "../dashboard/composables/charts"
    import {cssVar, KsBar, KsLine, KsSegmented} from "@kestra-io/design-system"
    import type {KsChartSeriesItem} from "@kestra-io/design-system"
    import {KsFilter as KSFilter} from "@kestra-io/design-system"
    import {useFlowMetricFilter} from "../filter/configurations"

    defineOptions({
        name: "FlowMetrics",
        inheritAttrs: false,
    })

    const route = useRoute()
    const router = useRouter()
    const {t} = useI18n()

    const flowMetricFilter = useFlowMetricFilter()
    const flowStore = useFlowStore()

    const isLoading = ref(false)
    const metricsData = ref<Record<string, any>>({})

    interface MetricAggregation {
        date: string;
        value?: number;
    }

    const currentAggregation = computed(() => {
        return (route.query.aggregation as string) ?? "sum"
    })

    const currentChartType = computed(() => {
        return (route.query.chartType as string) ?? "bar"
    })

    const aggregationOptions = computed(() => [
        {label: t("sum"), value: "sum"},
        {label: t("avg"), value: "avg"},
        {label: t("min"), value: "min"},
        {label: t("max"), value: "max"},
    ])

    const chartTypeOptions = computed(() => [
        {label: t("bar"), value: "bar"},
        {label: t("line"), value: "line"},
    ])

    function setAggregation(value: string | number | boolean): void {
        router.push({query: {...route.query, aggregation: String(value)}})
    }

    function setChartType(value: string | number | boolean): void {
        router.push({query: {...route.query, chartType: String(value)}})
    }

    const selectedMetric = computed(() => {
        return route.query["filters[metric][EQUALS]"] as string | undefined
    })

    const selectedTextSearch = computed(() => {
        return route.query["filters[q][EQUALS]"] as string | undefined
    })

    const selectedTask = computed(() => {
        return route.query["filters[task][EQUALS]"] as string | undefined
    })

    const displayedMetrics = computed(() => {
        const metrics = (flowStore.metrics ?? []) as string[]
        if (selectedMetric.value) {
            return metrics.filter((m) => m === selectedMetric.value)
        }

        if (selectedTextSearch.value) {
            const search = selectedTextSearch.value
            return metrics.filter((m) => m.indexOf(search) !== -1)
        }

        return metrics
    })

    function getTimeRangeParams(): {startDate?: string; endDate?: string} {
        const timeRange = route.query["filters[timeRange][EQUALS]"] as string | undefined
        if (!timeRange) return {}
        const endDate = moment().toISOString()
        const startDate = moment().subtract(moment.duration(timeRange)).toISOString()
        return {startDate, endDate}
    }

    function getCategories(metric: string): string[] {
        const data = metricsData.value[metric]
        if (!data) return []
        const aggregations = (data.aggregations ?? []) as MetricAggregation[]
        return aggregations.map((e) => moment(e.date).format(getFormat(data.groupBy)))
    }

    function getSeriesData(metric: string): KsChartSeriesItem[] {
        const data = metricsData.value[metric]
        if (!data) return []
        const aggregations = (data.aggregations ?? []) as MetricAggregation[]
        const aggregationLabel = currentAggregation.value.toLowerCase()
        return [
            {
                name: `${t(aggregationLabel)} ${t("of")} ${metric}`,
                data: aggregations.map((e) => e.value ?? 0),
                itemStyle: {color: cssVar("--ks-content-success")},
            },
        ]
    }

    async function loadAllAggregatedMetrics(): Promise<void> {
        const metrics = displayedMetrics.value
        if (!metrics.length) {
            metricsData.value = {}
            return
        }

        isLoading.value = true

        const params = route.params as {namespace: string; id: string}
        const taskId = selectedTask.value
        const aggregation = currentAggregation.value
        const timeRangeParams = getTimeRangeParams()

        const newData: Record<string, any> = {}

        await Promise.all(
            metrics.map(async (metric) => {
                const options = {
                    namespace: params.namespace,
                    id: params.id,
                    metric,
                    aggregation,
                    ...timeRangeParams,
                }
                if (taskId) {
                    newData[metric] = await flowStore.loadTaskAggregatedMetrics({...options, taskId})
                } else {
                    newData[metric] = await flowStore.loadFlowAggregatedMetrics(options)
                }
            }),
        )

        metricsData.value = newData
        isLoading.value = false
    }

    async function loadMetrics(): Promise<void> {
        const params = route.params as {namespace: string; id: string}

        flowStore.loadTasksWithMetrics({
            namespace: params.namespace,
            id: params.id,
        })

        const taskId = selectedTask.value
        let metrics: string[]

        if (taskId) {
            metrics = await flowStore.loadTaskMetrics({
                namespace: params.namespace,
                id: params.id,
                taskId,
            })
        } else {
            metrics = await flowStore.loadFlowMetrics({
                namespace: params.namespace,
                id: params.id,
            })
        }

        if (
            selectedMetric.value &&
            metrics?.length > 0 &&
            !metrics.includes(selectedMetric.value)
        ) {
            const query = {...route.query}
            delete query["filters[metric][EQUALS]"]
            await router.push({query})
        }

        await loadAllAggregatedMetrics()
    }

    function load(): void {
        loadMetrics()
    }

    watch(
        () => route.query,
        (newQuery, oldQuery) => {
            const taskChanged = newQuery["filters[task][EQUALS]"] !== oldQuery["filters[task][EQUALS]"]
            const metricChanged = newQuery["filters[metric][EQUALS]"] !== oldQuery["filters[metric][EQUALS]"]
            const searchChanged = newQuery["filters[q][EQUALS]"] !== oldQuery["filters[q][EQUALS]"]
            const aggregationChanged = newQuery.aggregation !== oldQuery.aggregation
            const timeRangeChanged = newQuery["filters[timeRange][EQUALS]"] !== oldQuery["filters[timeRange][EQUALS]"]

            if (taskChanged || metricChanged || searchChanged) {
                loadMetrics()
            } else if (aggregationChanged || timeRangeChanged) {
                loadAllAggregatedMetrics()
            }
        },
    )

    loadMetrics()
</script>

<style scoped lang="scss">
    .metric-controls {
        display: flex;
        align-items: center;
        gap: 0.5rem;
    }

    .metric-chart-card {
        margin-bottom: 1rem;
    }

    .metric-title {
        font-size: var(--ks-font-size-sm);
        font-weight: 600;
        margin-bottom: 0.5rem;
        color: var(--ks-content-secondary);
    }

    .chart {
        height: 231px;
    }
</style>

<template>
    <div class="pie">
        <KsSkeleton v-if="loading && !generated" animated :rows="3" class="empty" />
        <div v-else-if="generated?.results?.length" class="chart">
            <KsPie
                ref="ksPieRef"
                :maxPixelRatio="DASHBOARD_CHART_MAX_PIXEL_RATIO"
                :data="pieData"
                :loading="false"
                :donut="chartOptions?.graphStyle !== 'PIE'"
                :radius="['52%', '80%']"
                :options="pieOptions"
                :disableFeatures="[ChartFeature.LEGEND]"
                :tooltipType="TooltipType.EXTERNAL"
                @echarts-click="onSegmentClick"
            />
            <div class="pie-center-label">
                <div class="pie-center-label__total">{{ totalValue }}</div>
                <div v-if="showSuccessRatio" class="pie-center-label__success">{{ successRatio }}% {{ $t("success") }}</div>
            </div>
        </div>
        <KsNoData v-else class="empty" />

        <ChartLegend
            v-if="legendItems.length"
            :items="legendItems"
            :maxVisible="6"
            center
            :chart="ksPieRef"
            :formatValue="isDuration ? durationUtils.humanDuration : undefined"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useRoute} from "vue-router"

    import {KsPie, KsSkeleton, ChartFeature, TooltipType, dateUtils, durationUtils, type KsChartSeriesItem} from "@kestra-io/design-system"

    import {Chart, useChartGenerator} from "../composables/useDashboards"
    import {DASHBOARD_CHART_MAX_PIXEL_RATIO, getConsistentHEXColor, type EchartsParams} from "../composables/charts"
    import type {Column} from "../types.ts"
    import {useChartDrillDown} from "../composables/chartDrillDown"
    import ChartLegend from "./ChartLegend.vue"
    import {QueryFilter} from "@kestra-io/kestra-sdk"

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        dashboardId?: string;
        chart: Chart;
        filters?: QueryFilter[];
        showDefault?: boolean;
    }>(), {
        dashboardId: undefined,
        filters: () => [],
        showDefault: false,
    })

    const route = useRoute()

    const {drillDown} = useChartDrillDown(props.chart)

    const {chartOptions} = props.chart
    const columns = props.chart.data?.columns ?? {}
    const isDuration = Object.values(columns).find((c: Column) => c.agg !== undefined)?.field === "DURATION"

    const aggregator = Object.entries(columns).reduce<{
        value?: {label: string; key: string};
        field?: {label: string; key: string};
    }>((result, [key, col]) => {
        result["agg" in col ? "value" : "field"] = {label: col.displayName ?? col.agg ?? "", key}
        return result
    }, {})

    const ksPieRef = ref<InstanceType<typeof KsPie> | null>(null)
    const {data: generated, loading, generate} = useChartGenerator(props.dashboardId, props)

    function parseValue(value: unknown): string {
        const date = dateUtils.parseIso(value)
        return date.isValid() ? date.format("YYYY-MM-DD") : String(value)
    }

    const pieData = computed<KsChartSeriesItem[]>(() => {
        const rawData = generated.value?.results
        if (!rawData) return []

        const results: Record<string, number> = Object.create(null)
        rawData.forEach((row) => {
            const field = parseValue(row[aggregator.field?.key ?? ""])
            results[field] = (results[field] || 0) + (row[aggregator.value?.key ?? ""] as number)
        })

        return Object.entries(results).map(([name, value]) => ({
            name,
            value,
            itemStyle: {color: getConsistentHEXColor("light", name)},
        }))
    })

    const total = computed(() => pieData.value.reduce((acc, item) => acc + Number(item.value), 0))

    const totalValue = computed(() =>
        isDuration ? durationUtils.humanDuration(total.value) : total.value.toLocaleString(),
    )

    const showSuccessRatio = computed(() => !isDuration && pieData.value.some((item) => item.name === "SUCCESS"))

    const successRatio = computed(() => {
        if (!total.value) return "0"
        const success = Number(pieData.value.find((item) => item.name === "SUCCESS")?.value ?? 0)
        return ((success / total.value) * 100).toFixed(1)
    })

    const legendItems = computed(() =>
        pieData.value.map((item) => ({
            label: String(item.name),
            color: (item.itemStyle as {color?: string} | undefined)?.color ?? "",
            count: Number(item.value),
        })),
    )

    const pieOptions = computed(() => ({
        tooltip: {
            formatter: (params: EchartsParams) =>
                isDuration
                    ? `${params.name}: ${durationUtils.humanDuration(Number(params.value))} (${params.percent}%)`
                    : `${params.name}: ${params.value} (${params.percent}%)`,
        },
    }))

    const dimensionColumn = computed(() => {
        const dimensionKey = aggregator.field?.key
        return dimensionKey ? columns[dimensionKey] : undefined
    })

    function onSegmentClick(rawParams: unknown) {
        const params = rawParams as EchartsParams
        if (!params?.name) return
        drillDown([{column: dimensionColumn.value, value: params.name}])
    }

    function refresh() {
        return generate()
    }

    defineExpose({refresh})

    watch(() => route.params.filters, () => refresh(), {deep: true})
</script>

<style scoped lang="scss">
    .pie {
        display: flex;
        flex-direction: column;
        height: 100%;
    }

    .empty {
        min-height: 200px;
    }

    .chart {
        position: relative;
        display: flex;
        align-items: center;
        justify-content: center;
        height: 231px;
        margin-top: -2rem;
        container-type: inline-size;
    }

    .pie-center-label {
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        display: flex;
        flex-direction: column;
        align-items: center;
        pointer-events: none;
        z-index: 1;
        max-width: min(52%, 7rem);
        text-align: center;
        line-height: 1.2;

        &__total {
            font-size: var(--ks-font-size-3xl);
            color: var(--ks-text-primary);
            font-weight: 700;
            white-space: nowrap;
        }

        &__success {
            font-size: var(--ks-font-size-2xs);
            color: var(--ks-text-success);
        }
    }
</style>
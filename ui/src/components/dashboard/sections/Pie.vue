<template>
    <div
        class="d-flex flex-row align-items-center justify-content-center chart chart-container"
    >
        <KsPie
            v-if="generated !== undefined"
            ref="ksPieRef"
            :data="pieData"
            :loading="false"
            :donut="chartOptions?.graphStyle !== 'PIE'"
            :options="pieOptions"
            :disableFeatures="[ChartFeature.LEGEND]"
            :tooltipType="TooltipType.EXTERNAL"
        />
        <div
            v-if="generated !== undefined"
            class="pie-center-label"
        >
            {{ totalValue }}
        </div>
        <KsEmpty v-else />
    </div>
</template>

<script setup lang="ts">
    import {computed, PropType, ref, watch} from "vue"

    import {Chart, useChartGenerator} from "../composables/useDashboards"
    import {extractState, getConsistentHEXColor} from "../composables/charts"
    import {FilterObject} from "../../../utils/filters"
    import {KsPie, durationUtils} from "@kestra-io/design-system"
    import type {KsChartSeriesItem} from "@kestra-io/design-system"
    import {TooltipType, ChartFeature} from "@kestra-io/design-system"
    import {useMiscStore} from "override/stores/misc"

    import moment from "moment"

    import {useRoute, useRouter} from "vue-router"

    const route = useRoute()
    const router = useRouter()

    defineOptions({inheritAttrs: false})
    const props = defineProps({
        dashboardId: {type: String, required: false, default: undefined},
        chart: {type: Object as PropType<Chart>, required: true},
        filters: {type: Array as PropType<FilterObject[]>, default: () => []},
        showDefault: {type: Boolean, default: false},
    })

    const ksPieRef = ref<InstanceType<typeof KsPie> | null>(null)

    const {chartOptions} = props.chart
    const columns = props.chart.data?.columns ?? {}
    const isDuration = Object.values(columns).find((c: Record<string, any>) => c.agg !== undefined)?.field === "DURATION"

    const aggregator = Object.entries(columns).reduce<{
        value?: {label: string; key: string};
        field?: {label: string; key: string};
    }>(
        (result, [key, column]) => {
            const col = column as Record<string, any>
            const type = "agg" in col ? "value" : "field"
            result[type] = {label: col.displayName ?? col.agg, key}
            return result
        },
        {},
    )

    function parseValue(value: unknown): string {
        const date = moment(value as moment.MomentInput, moment.ISO_8601, true)
        return date.isValid() ? date.format("YYYY-MM-DD") : String(value)
    }

    const {data: generated, generate} = useChartGenerator(props.dashboardId, props)

    const pieData = computed<KsChartSeriesItem[]>(() => {
        const rawData = generated.value?.results as Record<string, any>[] | undefined
        if (!rawData) return []

        const results: Record<string, number> = Object.create(null)
        rawData.forEach((value) => {
            const field = parseValue(value[aggregator.field?.key ?? ""])
            const aggregated = value[aggregator.value?.key ?? ""] as number
            results[field] = (results[field] || 0) + aggregated
        })

        return Object.entries(results).map(([name, value]) => ({
            name,
            value,
            itemStyle: {color: getConsistentHEXColor("light", name)},
        }))
    })

    const totalValue = computed(() => {
        const total = pieData.value.reduce((acc, item) => acc + Number(item.value), 0)
        return isDuration ? durationUtils.humanDuration(total) : String(total)
    })


    const pieOptions = computed(() => {
        const opts: Record<string, unknown> = {
            roseType: "radius",
            tooltip: {
                formatter: (params: any) =>
                    isDuration
                        ? `${params.name}: ${durationUtils.humanDuration(params.value)} (${params.percent}%)`
                        : `${params.name}: ${params.value} (${params.percent}%)`,
            },
        }

        return opts
    })

    watch(ksPieRef, (newRef) => {
        if (!newRef) return
        const instance = newRef.getEchartsInstance()
        if (!instance) return
        instance.on("click", (params: any) => {
            if (!params.name) return
            router.push({
                name: "executions/list",
                params: {tenant: route.params.tenant},
                query: {
                    state: extractState(params.name),
                    scope: "USER",
                    size: 100,
                    page: 1,
                    "filters[timeRange][EQUALS]": useMiscStore()?.configs?.chartDefaultDuration ?? "PT24H",
                },
            })
        })
    })

    function refresh() {
        return generate()
    }

    defineExpose({
        refresh,
    })

    watch(() => route.params.filters, () => {
        refresh()
    }, {deep: true})
</script>

<style scoped lang="scss">
    .chart {
        height: 231px;
    }

    .chart-container {
        position: relative;
    }

    .pie-center-label {
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        font-size: 22px;
        color: var(--ks-content-primary);
        pointer-events: none;
        z-index: 1;
        white-space: nowrap;
    }
</style>

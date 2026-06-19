<template>
    <section v-if="data" class="kpi">
        <span class="title">{{ getChartTitle(props.chart) }}</span>

        <p class="value">
            {{ getPropertyValue(data, "value") }}<span v-if="percentageShown" class="percent">%</span>
        </p>

        <KsProgress
            v-if="percentageShown && progressValue > 0"
            class="progress"
            :percentage="progressValue"
            :strokeWidth="6"
            :radius="60"
            :color="progressColor"
            :showText="false"
        />

        <span v-if="description" class="description">{{ description }}</span>
    </section>

    <KsNoData v-else />
</template>

<script setup lang="ts">
    import {computed, watch} from "vue"
    import {useRoute} from "vue-router"

    import {KsProgress} from "@kestra-io/design-system"

    import {Chart, getChartTitle, getPropertyValue, useChartGenerator} from "../composables/useDashboards"
    import {getConsistentHEXColor} from "../composables/charts"
    import {useTheme} from "../../../utils/utils"
    import {FilterObject} from "../../../utils/filters"

    const props = withDefaults(defineProps<{
        dashboardId?: string;
        chart: Chart;
        filters?: FilterObject[];
        showDefault?: boolean;
    }>(), {
        dashboardId: undefined,
        filters: () => [],
        showDefault: false,
    })

    const route = useRoute()
    const theme = useTheme()

    const {percentageShown, data, generate} = useChartGenerator(props.dashboardId, props)

    const description = computed(() => props.chart.chartOptions?.description)

    const progressValue = computed(() => {
        const value = Number(getPropertyValue(data.value, "value"))
        return Number.isFinite(value) ? Math.min(100, Math.max(0, value)) : 0
    })

    const progressColor = computed(() => {
        const state = (props.chart.data?.numerator as {values?: string[]}[] | undefined)?.[0]?.values?.[0]
        return state ? getConsistentHEXColor(theme.value, state) : undefined
    })

    function refresh() {
        return generate()
    }

    defineExpose({refresh})

    watch(() => route.params.filters, refresh, {deep: true})
</script>

<style scoped lang="scss">
    .kpi {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        height: 100%;
        text-align: left;

        .title {
            padding-bottom: var(--ks-spacing-2);
            font-size: var(--ks-font-size-xs);
            font-weight: var(--ks-font-weight-regular);
        }

        .value {
            margin: 0;
            font-size: var(--ks-font-size-2xl);
            font-weight: var(--ks-font-weight-semibold);
        }

        .percent {
            font-size: var(--ks-font-size-md);
            font-weight: var(--ks-font-weight-bold);
            color: var(--ks-text-muted);
        }

        .progress {
            width: 100%;
            margin-top: var(--ks-spacing-3);

            :deep(.kel-progress-bar__outer) {
                background-color: var(--ks-bg-base);
            }
        }

        .description {
            font-size: var(--ks-font-size-xs);
            font-weight: var(--ks-font-weight-regular);
            color: var(--ks-text-secondary);
        }
    }
</style>

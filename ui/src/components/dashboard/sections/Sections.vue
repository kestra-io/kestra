<template>
    <div class="dashboard-sections-container">
        <section id="charts" :class="{padding}">
            <div
                v-for="chart in props.charts"
                :key="`chart__${chart.id}`"
                class="dashboard-block"
                :class="{
                    [`dash-width-${chart.chartOptions?.width || 6}`]: true
                }"
            >
                <div :class="{'is-kpi': isKPIChart(chart.type)}">
                    <div class="chart-header">
                        <div id="charts_heading">
                            <p v-if="!isKPIChart(chart.type)">
                                <span>
                                    {{ labels(chart).title }}
                                </span>
                                <template v-if="labels(chart)?.description">
                                    <br>
                                    <small>
                                        {{ labels(chart).description }}
                                    </small>
                                </template>
                            </p>
                        </div>
                        <div id="charts_buttons">
                            <KsTooltip
                                v-if="isExportableChart(chart.type)"
                                :content="$t('dashboards.export')"
                            >
                                <KsDropdown
                                    placement="bottom-end"
                                    trigger="click"
                                >
                                    <KsButton
                                        :icon="Download"
                                        :aria-label="$t('dashboards.export')"
                                        link
                                        class="chart-action"
                                    />
                                    <template #dropdown>
                                        <KsDropdownMenu>
                                            <KsDropdownItem @click="exportChart(chart, 'CSV')">
                                                {{ $t('dashboards.exportTo.csv') }}
                                            </KsDropdownItem>
                                            <KsDropdownItem @click="exportChart(chart, 'ION')">
                                                {{ $t('dashboards.exportTo.ion') }}
                                            </KsDropdownItem>
                                        </KsDropdownMenu>
                                    </template>
                                </KsDropdown>
                            </KsTooltip>

                            <KsIcon
                                v-if="props.dashboard?.id !== 'default'"
                                :tooltip="$t('dashboards.edition.chart')"
                            >
                                <KsButton
                                    tag="router-link"
                                    :to="{
                                        name: 'dashboards/update',
                                        params: {dashboard: props.dashboard?.id},
                                        query: {highlight: chart.id}}"
                                    :aria-label="$t('dashboards.edition.chart')"
                                    :icon="Pencil"
                                    link
                                    class="chart-action"
                                />
                            </KsIcon>
                        </div>
                    </div>

                    <div :ref="(el) => observeChartBlock(el, chart.id)" class="chart-content">
                        <component
                            v-if="activatedCharts.has(chart.id)"
                            :ref="(el: Element | ComponentPublicInstance | null) => registerChartComponent(el, chart.id)"
                            :is="TYPES[chart.type as keyof typeof TYPES]"
                            :chart
                            :dashboardId="dashboard.id"
                            :filters
                            :showDefault="props.showDefault"
                            v-bind="chartProps(chart)"
                        />
                        <KsSkeleton
                            v-else
                            animated
                            :rows="3"
                            class="chart-placeholder"
                            :class="{'is-kpi': isKPIChart(chart.type)}"
                            :style="placeholderHeight(chart.id) ? {minHeight: `${placeholderHeight(chart.id)}px`} : undefined"
                        />
                    </div>
                </div>
            </div>
        </section>
    </div>
</template>

<script setup lang="ts">
    import {computed, provide, type ComponentPublicInstance} from "vue"

    import type {Dashboard, Chart} from "../composables/useDashboards"
    import {IGNORED_FILTERS_INJECTION_KEY, isKPIChart, isCanvasChart, isExportableChart, getChartTitle} from "../composables/useDashboards"
    import {useLazyChartBlocks} from "../composables/useLazyChartBlocks"
    import {TYPES} from "../dashboard-types"

    import {useRoute} from "vue-router"
    import {routeFamily} from "../../../utils/routeFamily"
    import type {BrushRange} from "../../../utils/logsBrushMappers"
    const route = useRoute()

    import {decodeSearchParams, KsDropdown, KsDropdownMenu, KsDropdownItem, KsSkeleton, KsTooltip} from "@kestra-io/design-system"

    import {useDashboardStore} from "../../../stores/dashboard"
    const dashboardStore = useDashboardStore()

    import {useI18n} from "vue-i18n"
    import {useToast} from "../../../utils/toast"
    const {t, te} = useI18n({useScope: "global"})
    const toast = useToast()

    import Download from "vue-material-design-icons/Download.vue"
    import Pencil from "vue-material-design-icons/Pencil.vue"
    import {QueryFilter} from "@kestra-io/kestra-sdk"

    const chartsComponents = new Map<string, {
        refresh(): void;
        exportParameters?(): {pageNumber?: number; pageSize?: number; filters?: QueryFilter[]};
    }>()

    function registerChartComponent(el: Element | ComponentPublicInstance | null, chartId: string) {
        if (el) chartsComponents.set(chartId, el as unknown as {refresh(): void})
        else chartsComponents.delete(chartId)
    }

    // Only mounted charts are in the map, so a recycled one is skipped here and reloads when it scrolls back in.
    function refreshCharts() {
        chartsComponents.forEach((component) => component.refresh())
    }

    defineExpose({
        refreshCharts,
    })

    const emit = defineEmits<{
        "chart-select": [BrushRange | null]
    }>()

    const props = defineProps<{
        dashboard: Dashboard;
        charts?: Chart[];
        showDefault?: boolean;
        padding?: boolean;
        baseFilters?: QueryFilter[];
        selectableChartId?: string;
        brushStart?: string;
        brushEnd?: string;
    }>()

    const chartTypesById = computed(() => new Map((props.charts ?? []).map((chart) => [chart.id, chart.type])))

    const {activatedCharts, observeChartBlock, placeholderHeight} = useLazyChartBlocks(
        (chartId) => isCanvasChart(chartTypesById.value.get(chartId) ?? ""),
    )

    function chartProps(chart: Chart): Record<string, unknown> {
        if (chart.id !== props.selectableChartId) return {}
        return {
            selectable: true,
            brushStart: props.brushStart,
            brushEnd: props.brushEnd,
            onSelect: (range: BrushRange | null) => emit("chart-select", range),
        }
    }

    const labels = (chart: Chart) => ({
        title: getChartTitle(chart),
        description: chart?.chartOptions?.description,
    })

    const notifiedIgnoredFilters = new Map<string, string>()

    provide(IGNORED_FILTERS_INJECTION_KEY, (chartId, fields) => {
        const notified = fields.join(",")

        if (notifiedIgnoredFilters.get(chartId) === notified) {
            return
        }

        notifiedIgnoredFilters.set(chartId, notified)

        if (fields.length > 0) {
            const chart = props.charts?.find((candidate) => candidate.id === chartId)
            const filterLabels = fields.map((field) => te(`filter.${field}.label`) ? t(`filter.${field}.label`) : field)

            toast.warning(t("dashboards.ignoredFilters", {filters: filterLabels.join(", ")}, fields.length), chart ? getChartTitle(chart) : undefined)
        }
    })

    // Make the overview of flows/dashboard/namespace specific
    const filters = computed<QueryFilter[]>(() => {
        const baseFilters: QueryFilter[] = [...(props.baseFilters ?? [])]

        if (routeFamily(route.name) === "flows/update") {
            baseFilters.push({
                field: "namespace", operation: "EQUALS", value: route.params.namespace as string,
            })
            baseFilters.push({field: "flowId", operation: "EQUALS", value: route.params.id as string})
        }

        if (routeFamily(route.name) === "namespaces/update") {
            baseFilters.push({field: "namespace", operation: "PREFIX", value: route.params.id as string})
        }

        return baseFilters
    })

    async function exportChart(chart: Chart, format: "CSV" | "ION") {
        const {filters: chartFilters = [], ...pagination} = chartsComponents.get(chart.id)?.exportParameters?.() ?? {}

        const exported = await dashboardStore.export(props.dashboard, chart, {
            ...pagination,
            filters: filters.value.concat(decodeSearchParams(route.query) as QueryFilter[] ?? [], chartFilters),
        }, format)

        if (!exported) toast.warning(t("dashboards.exportEmpty"))
    }
</script>

<style scoped>

.dashboard-sections-container{
    container-type: inline-size;
}

section#charts {
    display: grid;
    gap: 1rem;
    grid-template-columns: repeat(3, 1fr);
    @container (min-width: 375px) {
        grid-template-columns: repeat(6, 1fr);
    }
    @container (min-width: 768px) {
        grid-template-columns: repeat(12, 1fr);
    }
    &.padding {
        padding: 0 2rem 1rem;
    }

    .dashboard-block {
        & > div {
            display: flex;
            flex-direction: column;
            height: 100%;
            padding: 1.25rem;
            background: var(--ks-bg-surface);
            border: 1px solid var(--ks-border-default);
            border-radius: var(--ks-radius-base);
            box-shadow: 0px 2px 4px 0px var(--ks-shadow-element);

            &.is-kpi {
                position: relative;

                #charts_buttons {
                    position: absolute;
                    top: 1.25rem;
                    right: 1.25rem;
                }
            }

        }

        .chart-header {
            display: flex;
            justify-content: space-between;
        }

        .chart-content {
            flex-grow: 1;
        }

        .chart-action {
            margin-inline-start: var(--ks-spacing-2);
        }

        #charts_buttons {
            opacity: 0;
            transition: opacity 0.2s ease;
        }

        &:hover #charts_buttons {
            opacity: 1;
        }

        .chart-placeholder {
            min-height: 200px; /* roughly the height of a rendered chart, so activation does not shift the layout */

            &.is-kpi {
                min-height: 0;
            }
        }

        #charts_heading {
            span {
                color: var(--ks-text-primary);
                font-size: var(--ks-font-size-md);
                font-weight: var(--ks-font-weight-bold);
            }
            small {
                color: var(--ks-text-secondary);
                font-size: var(--ks-font-size-xs);
                font-weight: 300;
            }
        }
    }

    .dash-width-1 {
        grid-column: span 1;
    }

    .dash-width-2 {
        grid-column: span 2;
    }

    .dash-width-3 {
        grid-column: span 3;
    }

    .dash-width-4,
    .dash-width-5,
    .dash-width-6,
    .dash-width-7,
    .dash-width-8,
    .dash-width-9,
    .dash-width-10,
    .dash-width-11,
    .dash-width-12 {
        grid-column: span 3;
    }

    @container (min-width: 375px) {
        .dash-width-4,
        .dash-width-5,
        .dash-width-6,
        .dash-width-7,
        .dash-width-8,
        .dash-width-9,
        .dash-width-10,
        .dash-width-11,
        .dash-width-12 {
            grid-column: span 6;
        }
    }

    @container (min-width: 768px) {
        .dash-width-4 {
            grid-column: span 4;
        }

        .dash-width-5 {
            grid-column: span 5;
        }

        .dash-width-6 {
            grid-column: span 6;
        }

        .dash-width-7 {
            grid-column: span 7;
        }

        .dash-width-8 {
            grid-column: span 8;
        }

        .dash-width-9 {
            grid-column: span 9;
        }

        .dash-width-10 {
            grid-column: span 10;
        }

        .dash-width-11 {
            grid-column: span 11;
        }

        .dash-width-12 {
            grid-column: span 12;
        }
    }
}
</style>

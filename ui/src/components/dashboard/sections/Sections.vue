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
                <div class="d-flex flex-column" :class="{'is-kpi': isKPIChart(chart.type)}">
                    <div class="d-flex justify-content-between">
                        <div id="charts_heading">
                            <p v-if="!isKPIChart(chart.type)">
                                <span class="fs-6 fw-bold">
                                    {{ labels(chart).title }}
                                </span>
                                <template v-if="labels(chart)?.description">
                                    <br>
                                    <small class="fw-light">
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
                                        class="ms-2"
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
                                    :icon="Pencil"
                                    link
                                    class="ms-2"
                                />
                            </KsIcon>
                        </div>
                    </div>

                    <div :ref="(el) => observeChartBlock(el, chart.id)" class="flex-grow-1">
                        <component
                            v-if="activatedCharts.has(chart.id)"
                            ref="chartsComponents"
                            :is="TYPES[chart.type as keyof typeof TYPES]"
                            :chart
                            :dashboardId="dashboard.id"
                            :filters
                            :showDefault="props.showDefault"
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
    import {ref, computed} from "vue"

    import type {Dashboard, Chart} from "../composables/useDashboards"
    import {isKPIChart, isCanvasChart, isExportableChart, getChartTitle} from "../composables/useDashboards"
    import {useLazyChartBlocks} from "../composables/useLazyChartBlocks"
    import {TYPES} from "../dashboard-types"

    import {useRoute} from "vue-router"
    import {routeFamily} from "../../../utils/routeFamily"
    const route = useRoute()

    import {decodeSearchParams, KsDropdown, KsDropdownMenu, KsDropdownItem, KsSkeleton, KsTooltip} from "@kestra-io/design-system"

    import {useDashboardStore} from "../../../stores/dashboard"
    const dashboardStore = useDashboardStore()

    import Download from "vue-material-design-icons/Download.vue"
    import Pencil from "vue-material-design-icons/Pencil.vue"
    import {QueryFilter} from "@kestra-io/kestra-sdk"

    const chartsComponents = ref<({refresh(): void} | null)[]>()

    function refreshCharts() {
        // Recycled charts leave holes in the ref array and reload on their own when they scroll back in.
        (chartsComponents.value ?? []).forEach((component) => component?.refresh())
    }

    defineExpose({
        refreshCharts,
    })

    const props = defineProps<{
        dashboard: Dashboard;
        charts?: Chart[];
        showDefault?: boolean;
        padding?: boolean;
    }>()

    const chartTypesById = computed(() => new Map((props.charts ?? []).map((chart) => [chart.id, chart.type])))

    const {activatedCharts, observeChartBlock, placeholderHeight} = useLazyChartBlocks(
        (chartId) => isCanvasChart(chartTypesById.value.get(chartId) ?? ""),
    )

    const labels = (chart: Chart) => ({
        title: getChartTitle(chart),
        description: chart?.chartOptions?.description,
    })

    // Make the overview of flows/dashboard/namespace specific
    const filters = computed<QueryFilter[]>(() => {
        const baseFilters: QueryFilter[] = []

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

    function exportChart(chart: Chart, format: "CSV" | "ION") {
        dashboardStore.export(props.dashboard, chart, {
            filters: filters.value.concat(decodeSearchParams(route.query) as QueryFilter[] ?? []),
        }, format)
    }
</script>

<style scoped lang="scss">

.dashboard-sections-container{
    container-type: inline-size;
}

$smallMobile: 375px;
$tablet: 768px;

section#charts {
    display: grid;
    gap: 1rem;
    grid-template-columns: repeat(3, 1fr);
    @container (min-width: #{$smallMobile}) {
        grid-template-columns: repeat(6, 1fr);
    }
    @container (min-width: #{$tablet}) {
        grid-template-columns: repeat(12, 1fr);
    }
    &.padding {
        padding: 0 2rem 1rem;
    }

    .dashboard-block {
        & > div {
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

        #charts_buttons {
            opacity: 0;
            transition: opacity 0.2s ease;
        }

        &:hover #charts_buttons {
            opacity: 1;
        }

        .chart-placeholder {
            min-height: 200px; // roughly the height of a rendered chart, so activation does not shift the layout

            &.is-kpi {
                min-height: 0;
            }
        }

        #charts_heading {
            span {
                color: var(--ks-text-primary);
                font-size: var(--ks-font-size-md);
            }
            small {
                color: var(--ks-text-secondary);
                font-size: var(--ks-font-size-xs);
            }
        }
    }

    @for $i from 1 through 3 {
        .dash-width-#{$i} {
            grid-column: span #{$i};
        }
    }

    @for $i from 4 through 12 {
        .dash-width-#{$i} {
            grid-column: span 3;
        }
    }

    @container (min-width: #{$smallMobile}) {
        @for $i from 4 through 12 {
            .dash-width-#{$i} {
                grid-column: span 6;
            }
        }
    }

    @container (min-width: #{$tablet}) {
        @for $i from 4 through 12 {
            .dash-width-#{$i} {
                grid-column: span #{$i};
            }
        }
    }
}
</style>

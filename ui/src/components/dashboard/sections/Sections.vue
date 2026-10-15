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
                <div class="d-flex flex-column">
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
                            <KestraIcon
                                v-if="isTableChart(chart.type)"
                                :tooltip="$t('dashboards.export')"
                            >
                                <el-button
                                    @click="exportChart(chart)"
                                    :icon="Download"
                                    link
                                    class="ms-2"
                                />
                            </KestraIcon>

                            <KestraIcon
                                v-if="props.dashboard?.id !== 'default'"
                                :tooltip="$t('dashboards.edition.chart')"
                            >
                                <el-button
                                    tag="router-link"
                                    :to="{
                                        name: 'dashboards/update',
                                        params: {dashboard: props.dashboard?.id},
                                        query: {highlight: chart.id}}"
                                    :icon="Pencil"
                                    link
                                    class="ms-2"
                                />
                            </KestraIcon>
                        </div>
                    </div>

                    <div :ref="(el) => observeChartBlock(el, chart.id)" class="flex-grow-1">
                        <component
                            v-if="activatedCharts.has(chart.id)"
                            :ref="(el: Element | ComponentPublicInstance | null) => registerChartComponent(el, chart.id)"
                            :is="TYPES[chart.type as keyof typeof TYPES]"
                            :chart
                            :filters
                            :showDefault="props.showDefault"
                        />
                        <el-skeleton
                            v-else
                            animated
                            :rows="isKPIChart(chart.type) ? 1 : 3"
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
    import {computed, type ComponentPublicInstance} from "vue";

    import type {Dashboard, Chart} from "../composables/useDashboards";
    import {isKPIChart, isTableChart, isCanvasChart, getChartTitle} from "../composables/useDashboards";
    import {useLazyChartBlocks} from "../composables/useLazyChartBlocks";
    import {TYPES} from "../dashboard-types";

    import {useRoute} from "vue-router";
    const route = useRoute();

    import {decodeSearchParams} from "../../filter/utils/helpers";

    import {useDashboardStore} from "../../../stores/dashboard";
    const dashboardStore = useDashboardStore();

    import KestraIcon from "../../Kicon.vue";

    import Download from "vue-material-design-icons/Download.vue";
    import Pencil from "vue-material-design-icons/Pencil.vue";

    const chartsComponents = new Map<string, {refresh(): void}>();

    function registerChartComponent(el: Element | ComponentPublicInstance | null, chartId: string) {
        if (el) chartsComponents.set(chartId, el as unknown as {refresh(): void});
        else chartsComponents.delete(chartId);
    }

    // Only mounted charts are in the map, so a recycled one is skipped here and reloads when it scrolls back in.
    function refreshCharts() {
        chartsComponents.forEach((component) => component.refresh());
    }

    defineExpose({
        refreshCharts
    });

    const props = defineProps<{
        dashboard: Dashboard;
        charts?: Chart[];
        showDefault?: boolean;
        padding?: boolean;
    }>();

    const chartTypesById = computed(() => new Map((props.charts ?? []).map((chart) => [chart.id, chart.type])));

    // Charts mount as their block nears the viewport; the canvas ones are unmounted again once scrolled far away.
    const {activatedCharts, observeChartBlock, placeholderHeight} = useLazyChartBlocks(
        (chartId) => isCanvasChart(chartTypesById.value.get(chartId) ?? ""),
    );

    const labels = (chart: Chart) => ({
        title: getChartTitle(chart),
        description: chart?.chartOptions?.description,
    });

    // Make the overview of flows/dashboard/namespace specific
    const filters = computed(() => {
        const baseFilters: { field: string; operation: string; value: string | string[] }[] = [];

        if (route.name === "flows/update") {
            baseFilters.push({field: "namespace", operation: "EQUALS", value: route.params.namespace as string});
            baseFilters.push({field: "flowId", operation: "EQUALS", value: route.params.id as string});
        }

        if (route.name === "namespaces/update") {
            baseFilters.push({field: "namespace", operation: "PREFIX", value: route.params.id as string});
        }

        return baseFilters;
    });

    function exportChart(chart: Chart) {
        dashboardStore.export(props.dashboard, chart, {
            filters: filters.value.concat(decodeSearchParams(route.query) ?? []),
        });
    }
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables";

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
            padding: 1.5rem;
            background: var(--ks-background-card);
            border: 1px solid var(--ks-border-primary);
            border-radius: $border-radius;
            box-shadow: 0px 2px 4px 0px var(--ks-card-shadow);
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

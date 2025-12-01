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
                                    @click="dashboardStore.export(dashboard, chart, {filters})"
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

                    <div class="flex-grow-1">
                        <component
                            ref="chartsComponents"
                            :is="TYPES[chart.type as keyof typeof TYPES]"
                            :chart
                            :filters
                            :showDefault="props.showDefault"
                        />
                    </div>
                </div>
            </div>
        </section>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue";

    import type {Dashboard, Chart} from "../composables/useDashboards";
    import {isKPIChart, isTableChart, getChartTitle} from "../composables/useDashboards";
    import {TYPES} from "../dashboard-types";

    import {useRoute} from "vue-router";
    const route = useRoute();

    import {useDashboardStore} from "../../../stores/dashboard";
    const dashboardStore = useDashboardStore();

    import KestraIcon from "../../Kicon.vue";

    import Download from "vue-material-design-icons/Download.vue";
    import Pencil from "vue-material-design-icons/Pencil.vue";

    const chartsComponents = ref<{refresh(): void}[]>();

    function refreshCharts() {
        chartsComponents.value!.forEach((component) => {
            component.refresh();
        });
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
            baseFilters.push({field: "namespace", operation: "EQUALS", value: route.params.id as string});
        }

        return baseFilters;
    });
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

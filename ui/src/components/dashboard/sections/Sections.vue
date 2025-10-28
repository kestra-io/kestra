<template>
    <section id="charts" :class="{padding}">
        <el-row :gutter="16">
            <el-col
                v-for="chart in props.charts"
                :key="`chart__${chart.id}`"
                :xs="24"
                :sm="(chart.chartOptions?.width || 6) * 4"
                :md="(chart.chartOptions?.width || 6) * 2"
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
                                :tooltip="t('dashboards.export')"
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
                                :tooltip="t('dashboards.edition.chart')"
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
            </el-col>
        </el-row>
    </section>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue";

    import type {Dashboard, Chart} from "../composables/useDashboards";
    import {TYPES, isKPIChart, isTableChart, getChartTitle} from "../composables/useDashboards";

    import {useRoute} from "vue-router";
    const route = useRoute();

    import {useDashboardStore} from "../../../stores/dashboard";
    const dashboardStore = useDashboardStore();

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

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

section#charts {
    &.padding {
        padding: 0 2rem 1rem;
    }

    & .el-row .el-col {
        margin-bottom: 1rem;

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
}
</style>

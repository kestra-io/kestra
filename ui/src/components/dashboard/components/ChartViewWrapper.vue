<template>
    <div
        class="chart-view"
    >
        <div v-if="dashboardStore.selectedChart" class="w-100">
            <Sections
                :dashboard="{id: 'default', charts: [dashboardStore.selectedChart]}"
                :charts="[dashboardStore.selectedChart]"
                showDefault
            />
        </div>
        <div v-else-if="dashboardStore.chartErrors.length" class="text-container">
            <span v-for="error in dashboardStore.chartErrors" :key="error">{{ error }}</span>
        </div>
        <div v-else>
            <el-empty :image="EmptyVisualDashboard" :imageSize="200">
                <template #description>
                    <h5>
                        {{ $t("dashboards.chart_preview") }}
                    </h5>
                </template>
            </el-empty>
        </div>
    </div>
</template>

<script lang="ts" setup>
    import Sections from "../sections/Sections.vue";
    import EmptyVisualDashboard from "../../../assets/empty_visuals/Visuals_empty_dashboard.svg";
    import {useDashboardStore} from "../../../stores/dashboard";

    const dashboardStore = useDashboardStore();
</script>

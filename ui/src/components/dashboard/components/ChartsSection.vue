<template>
    <section id="charts" :class="fullSize ? '' : 'charts-padding'">
        <el-row :gutter="16">
            <el-col
                v-for="(chart, index) in props.charts"
                :key="`${chart.id}__${index}`"
                :xs="24"
                :sm="(chart.chartOptions?.width || 6) * 4"
                :md="(chart.chartOptions?.width || 6) * 2"
            >
                <div class="d-flex flex-column">
                    <p v-if="chart.type !== 'io.kestra.plugin.core.dashboard.chart.KPI'">
                        <span class="fs-6 fw-bold">{{ labels(chart).title }}</span>
                        <template v-if="labels(chart)?.description">
                            <br>
                            <small class="fw-light">
                                {{ labels(chart).description }}
                            </small>
                        </template>
                    </p>
                    <el-button
                        v-if="canExport(chart.type)"
                        size="small"
                        class="mb-2"
                        @click="exportCsv(chart.id)"
                        icon="el-icon-download"
                    >
                        Export CSV
                    </el-button>

                    <div class="flex-grow-1">
                        <component
                            :is="TYPES[chart.type]"
                            :default="route.params.id === 'default'"
                            :source="chart.content"
                            :chart="chart"
                            :show-default="props.showDefault"
                            :default-filters="defaultFilters"
                        />
                    </div>
                </div>
            </el-col>
        </el-row>
    </section>
</template>

<script setup>
    import {useRoute, useRouter} from "vue-router";
    import axios from "axios";
    const route = useRoute();
    const router = useRouter();

    import TimeSeries from "./charts/custom/TimeSeries.vue";
    import Bar from "./charts/custom/Bar.vue";
    import Markdown from "./MarkdownPanel.vue";
    import Table from "./tables/custom/Table.vue";
    import Pie from "./charts/custom/Pie.vue";
    import KPI from "./charts/custom/KPI.vue";
    import {onMounted, ref} from "vue";

    const TYPES = {
        "io.kestra.plugin.core.dashboard.chart.TimeSeries": TimeSeries,
        "io.kestra.plugin.core.dashboard.chart.Bar": Bar,
        "io.kestra.plugin.core.dashboard.chart.Markdown": Markdown,
        "io.kestra.plugin.core.dashboard.chart.Table": Table,
        "io.kestra.plugin.core.dashboard.chart.Pie": Pie,
        "io.kestra.plugin.core.dashboard.chart.KPI": KPI,
    };

    const defaultFilters = ref([])

    const props = defineProps({
        charts: {type: Array, required: true, default: () => []},
        showDefault: {type: Boolean, default: false},
        fullSize: {type: Boolean, default: false},
    });

    const labels = (chart) => ({
        title: chart?.chartOptions?.displayName ?? chart?.id,
        description: chart?.chartOptions?.description,
    });

    // Only allow export for data charts and tables
    const canExport = (type) => [
        "io.kestra.plugin.core.dashboard.chart.TimeSeries",
        "io.kestra.plugin.core.dashboard.chart.Bar",
        "io.kestra.plugin.core.dashboard.chart.Table",
        "io.kestra.plugin.core.dashboard.chart.Pie"
    ].includes(type);

    async function exportCsv(chartId) {
        const dashboardId = route.params.id;
        // Compose filters as needed for your backend
        const filters = defaultFilters.value; // Add more if needed from route.query

        const response = await axios.post(
            `/api/v1/main/dashboards/${dashboardId}/charts/${chartId}/export`,
            {filters},
            {responseType: "blob"}
        );
        const url = window.URL.createObjectURL(new Blob([response.data], {type: "text/csv"}));
        const link = document.createElement("a");
        link.href = url;
        link.setAttribute("download", "export.csv");
        document.body.appendChild(link);
        link.click();
        link.remove();
    }

    onMounted(() => {
        const dateTimeKeys = ["startDate", "endDate", "timeRange"];
        if (!Object.keys(route.query).some(key => dateTimeKeys.some(dateTimeKey => key.includes(dateTimeKey)))) {
            router.push({
                query: {...route.query, "filters[timeRange][EQUALS]":"PT168H"}
            })
        }
        const filters = [];
        if (route.name === "flows/update") {
            filters.push({
                             field: "namespace",
                             operation: "EQUALS",
                             value: route.params.namespace
                         },
                         {
                             field: "flowId",
                             operation: "EQUALS",
                             value: route.params.id
                         })
        }
        if (route.name === "namespaces/update") {
            filters.push({
                field: "namespace",
                operation: "EQUALS",
                value: route.params.id
            })
        }
        defaultFilters.value = filters;
    })
</script>

<style lang="scss" scoped>
@import "@kestra-io/ui-libs/src/scss/variables";

section#charts {

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
    }
}

.charts-padding {
    padding: 0 2rem 1rem;
}
</style>

<template>
    <section id="charts" :class="{padding}">
        <el-row :gutter="16">
            <el-col
                v-for="chart in props.charts"
                :key="`chart__$${chart.id}`"
                :xs="24"
                :sm="(chart.chartOptions?.width || 6) * 4"
                :md="(chart.chartOptions?.width || 6) * 2"
            >
                <div class="d-flex flex-column">
                    <p v-if="showTitle(chart.type)">
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

                    <div class="flex-grow-1">
                        <component
                            :is="TYPES[chart.type]"
                            :default="route.params.id === 'default'"
                            :source="chart.content"
                            :show-default="props.showDefault"
                            :default-filters="filters"
                            :chart
                        />
                    </div>
                </div>
            </el-col>
        </el-row>
    </section>
</template>

<script setup>
    import {onMounted, ref} from "vue";

    import {useRoute, useRouter} from "vue-router";
    const route = useRoute();
    const router = useRouter();

    import Bar from "./Bar.vue";
    import KPI from "./KPI.vue";
    import Markdown from "./MarkdownPanel.vue";
    import Pie from "./Pie.vue";
    import Table from "./Table.vue";
    import TimeSeries from "./TimeSeries.vue";

    const TYPES = {
        "io.kestra.plugin.core.dashboard.chart.Bar": Bar,
        "io.kestra.plugin.core.dashboard.chart.KPI": KPI,
        "io.kestra.plugin.core.dashboard.chart.Markdown": Markdown,
        "io.kestra.plugin.core.dashboard.chart.Pie": Pie,
        "io.kestra.plugin.core.dashboard.chart.Table": Table,
        "io.kestra.plugin.core.dashboard.chart.TimeSeries": TimeSeries,
    };

    const props = defineProps({
        charts: {type: Array, required: true, default: () => []},
        showDefault: {type: Boolean, default: true},
        padding: {type: Boolean, default: false},
    });

    const showTitle = (type) => {
        return type !== "io.kestra.plugin.core.dashboard.chart.KPI";
    };

    const labels = (chart) => ({
        title: chart?.chartOptions?.displayName ?? chart?.id,
        description: chart?.chartOptions?.description,
    });

    const filters = ref([]);
    onMounted(() => {
        const DATE_TIME_KEYS = ["startDate", "endDate", "timeRange"];

        // Default to the last 7 days if no time range is set
        if (!Object.keys(route.query).some((key) => DATE_TIME_KEYS.some((dateTimeKey) => key.includes(dateTimeKey)))) {
            router.push({query: {...route.query, "filters[timeRange][EQUALS]": "PT168H"}});
        }

        if (route.name === "flows/update") {
            filters.value.push(
                {field: "namespace", operation: "EQUALS", value: route.params.namespace},
                {field: "flowId", operation: "EQUALS", value: route.params.id},
            );
        }

        if (route.name === "namespaces/update") {
            filters.value.push({field: "namespace", operation: "EQUALS", value: route.params.id});
        }
    });
</script>

<style lang="scss" scoped>
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
    }
}
</style>

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
                    <p v-if="chart.type !== 'io.kestra.plugin.core.dashboard.chart.KPI'" class="m-0">
                        <span class="fs-6 fw-bold">{{ labels(chart).title }}</span>
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
                            :chart="chart"
                            :show-default="props.showDefault"
                        />
                    </div>
                </div>
            </el-col>
        </el-row>
    </section>
</template>

<script setup>
    import {useRoute} from "vue-router";
    const route = useRoute();

    import TimeSeries from "./charts/custom/TimeSeries.vue";
    import Bar from "./charts/custom/Bar.vue";
    import Markdown from "./MarkdownPanel.vue";
    import Table from "./tables/custom/Table.vue";
    import Pie from "./charts/custom/Pie.vue";
    import KPI from "./charts/custom/KPI.vue";

    const TYPES = {
        "io.kestra.plugin.core.dashboard.chart.TimeSeries": TimeSeries,
        "io.kestra.plugin.core.dashboard.chart.Bar": Bar,
        "io.kestra.plugin.core.dashboard.chart.Markdown": Markdown,
        "io.kestra.plugin.core.dashboard.chart.Table": Table,
        "io.kestra.plugin.core.dashboard.chart.Pie": Pie,
        "io.kestra.plugin.core.dashboard.chart.KPI": KPI,
    };

    const props = defineProps({
        charts: {type: Array, required: true, default: () => []},
        showDefault: {type: Boolean, default: false},
        fullSize: {type: Boolean, default: false},
    });

    const labels = (chart) => ({
        title: chart?.chartOptions?.displayName ?? chart?.id,
        description: chart?.chartOptions?.description,
    });
</script>

<style lang="scss" scoped>
@import "@kestra-io/ui-libs/src/scss/variables";

section#charts {

    & .el-row .el-col {
        margin-bottom: 0.5rem;

        & > div {
            height: 100%;
            padding: 1.5rem;
            background: var(--ks-background-card);
            border: 1px solid var(--ks-border-primary);
            border-radius: $border-radius;
        }
    }
}

.charts-padding {
    padding: 0 2rem 1rem;
}
</style>

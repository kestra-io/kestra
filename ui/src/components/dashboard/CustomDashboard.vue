<template>
    <Header :title="dashboard.title" :breadcrumb />

    <div class="p-3">
        <KestraFilter
            prefix="custom_dashboard"
            :include="['relative_date', 'absolute_date']"
            :refresh="{shown: true, callback: () => {}}"
        />

        <el-row class="custom">
            <el-col
                v-for="(chart, index) in dashboard.charts"
                :key="index"
                :xs="24"
                :sm="12"
            >
                <div class="p-4">
                    <p class="m-0 fs-6 fw-bold">
                        {{ chart.chartOptions?.displayName ?? chart.id }}
                    </p>
                    <p v-if="chart.chartOptions?.description" class="m-0 fw-light small">
                        {{ chart.chartOptions.description }}
                    </p>

                    <component
                        :is="TYPES[chart.type]"
                        :source="chart.content"
                        :chart
                    />
                </div>
            </el-col>
        </el-row>
    </div>
</template>

<script lang="ts" setup>
    import {onMounted, onUnmounted, ref} from "vue";

    import Header from "./components/Header.vue";
    import KestraFilter from "../filter/KestraFilter.vue";

    import TimeSeries from "./components/charts/custom/TimeSeries.vue";
    import Markdown from "../layout/Markdown.vue";

    import {useI18n} from "vue-i18n";
    import {useStore} from "vuex";

    const {t} = useI18n({useScope: "global"});
    const store = useStore();

    const breadcrumb = [
        {
            label: t("custom_dashboards"),
            link: {name: "dashboards/list"},
        },
    ];

    const TYPES = {
        "io.kestra.plugin.core.dashboard.chart.TimeSeries": TimeSeries,
        "io.kestra.plugin.core.dashboard.chart.Markdown": Markdown,
    };

    const dashboard = ref({});

    const props = defineProps({
        id: {
            type: String,
            required: true
        }
    })
    onMounted(async () => {
        dashboard.value = await store.dispatch("dashboard/load", props.id);
    });
    onUnmounted(async () => {
        store.commit("dashboard/setDashboard", undefined);
    })
</script>

<style lang="scss" scoped>
@import "@kestra-io/ui-libs/src/scss/variables";

$spacing: 20px;

.custom.el-row {
    width: 100%;

    & .el-col {
        padding-bottom: $spacing;

        &:nth-of-type(even) div {
            margin-left: 1rem;
        }

        & > div {
            height: 100%;
            background: var(--card-bg);
            border: 1px solid var(--bs-gray-300);
            border-radius: $border-radius;

            html.dark & {
                border-color: var(--bs-gray-600);
            }
        }
    }
}
</style>

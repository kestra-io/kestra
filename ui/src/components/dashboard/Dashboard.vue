<template>
    <section id="header" v-if="!embed">
        <Header
            :title="dashboard.title ?? t('overview')"
            :breadcrumb="[{label: t('dashboard_label'), link: {}}]"
            :id="dashboard.id"
        />
    </section>

    <section id="filter">
        <KestraFilter
            :prefix="dashboard.id"
            :include="['relative_date', 'absolute_date', 'namespace', 'labels']"
            :buttons="{
                refresh: {shown: true, callback: () => load()},
                settings: {shown: false},
            }"
            :dashboards="{shown: route.name === 'home'}"
            @dashboard="(value) => load(value)"
        />
    </section>

    <section id="description" v-if="dashboard.description">
        <small>{{ dashboard.description }}</small>
    </section>

    <section id="charts">
        <el-row :gutter="8">
            <el-col
                v-for="(chart, index) in charts"
                :key="`${chart.id}__${index}`"
                :xs="24"
                :sm="(chart.chartOptions?.width || 6) * 2"
            >
                <div class="d-flex flex-column">
                    <p class="m-0">
                        <span class="fs-6 fw-bold">{{ labels.title }}</span>
                        <template v-if="labels?.description">
                            <br>
                            <small class="fw-light">
                                {{ labels.description }}
                            </small>
                        </template>
                    </p>

                    <div class="flex-grow-1 mt-2">
                        <component
                            :is="TYPES[chart.type]"
                            :default="route.params.id === 'default'"
                            :source="chart.content"
                            :chart
                        />
                    </div>
                </div>
            </el-col>
        </el-row>
    </section>
</template>

<script setup>
    import {onBeforeMount, ref} from "vue";

    import Header from "./components/Header.vue";
    import KestraFilter from "../filter/KestraFilter.vue";

    import {useRoute, useRouter} from "vue-router";
    const router = useRouter();
    const route = useRoute();

    import {useStore} from "vuex";
    const store = useStore();

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import TimeSeries from "./components/charts/custom/TimeSeries.vue";
    import Bar from "./components/charts/custom/Bar.vue";
    import Markdown from "../layout/Markdown.vue";
    import Table from "./components/tables/custom/Table.vue";
    import Pie from "./components/charts/custom/Pie.vue";
    import KPI from "./components/charts/custom/KPI.vue";

    const TYPES = {
        "io.kestra.plugin.core.dashboard.chart.TimeSeries": TimeSeries,
        "io.kestra.plugin.core.dashboard.chart.Bar": Bar,
        "io.kestra.plugin.core.dashboard.chart.Markdown": Markdown,
        "io.kestra.plugin.core.dashboard.chart.Table": Table,
        "io.kestra.plugin.core.dashboard.chart.Pie": Pie,
        "io.kestra.plugin;core.dashboard.chart.KPI": KPI,
    };

    const props = defineProps({
        embed: {type: Boolean, default: false},
        isFlow: {type: Boolean, default: false},
        isNamespace: {type: Boolean, default: false},
    });

    import yaml from "yaml";
    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";

    import DEFAULT_DASHBOARD from "../../assets/dashboard/default_dashboard_definition.yaml?raw";

    const dashboard = ref({});
    const charts = ref([]);

    const labels = (chart) => ({
        title: chart?.chartOptions?.displayName ?? chart?.id,
        description: chart?.chartOptions?.description,
    });

    const loadCharts = async (allCharts) => {
        charts.value = [];

        for (const chart of allCharts) {
            charts.value.push({...chart, content: yaml.stringify(chart)});
        }
    };

    const load = async (id = "default") => {
        if (route.name !== "home") return;

        router.replace({
            params: {...route.params, id},
            query: route.params.id !== id ? {} : {...route.query},
        });

        const initial = {id: "default", ...YAML_UTILS.parse(DEFAULT_DASHBOARD)};
        dashboard.value = id === "default" ? initial : await store.dispatch("dashboard/load", id);

        loadCharts(dashboard.value.charts);
    };

    onBeforeMount(() => {
        if (props.isFlow) {
        // TODO: Load dashboard for flow
        } else if (props.isNamespace) {
        // TODO: Load dashboard for namespace
        }
    });
</script>

<style lang="scss" scoped>
@import "@kestra-io/ui-libs/src/scss/variables";

section#filter {
    margin: 2rem 0.25rem 0;
    padding: 0 2rem;
}

section#description {
    margin: 0 0.25rem;
    padding: 0 2rem 1rem;
    color: var(--ks-content-secondary);
}

section#charts {
    padding: 0 2rem 1rem;

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
</style>

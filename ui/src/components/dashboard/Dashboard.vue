<template>
    <Header
        v-if="!embed"
        :title="custom.shown ? custom.dashboard.title : t('overview')"
        :breadcrumb="[
            {
                label: t(custom.shown ? 'custom_dashboard' : 'dashboard_label'),
                link: {},
            },
        ]"
        :id="custom.dashboard.id ?? undefined"
    />

    <div class="dashboard-filters">
        <!-- Force re-rendering when switching between custom and default -->
        <KestraFilter
            :key="custom.shown"
            :prefix="custom.shown ? 'custom_dashboard' : 'dashboard'"
            :include="['relative_date', 'absolute_date', 'namespace', 'labels']"
            :buttons="{
                refresh: {
                    shown: true,
                    callback: custom.shown ? refreshCustom : fetchAll,
                },
                settings: {shown: false},
            }"
            :dashboards="{shown: customDashboardsEnabled && route.name === 'home'}"
            @dashboard="(v) => handleCustomUpdate(v)"
            :is-default-dashboard="!custom.shown"
        />
    </div>

    <div v-if="custom.shown">
        <p v-if="custom.dashboard.description" class="description">
            <small>{{ custom.dashboard.description }}</small>
        </p>
        <el-row class="custom">
            <el-col
                v-for="(chart, index) in custom.dashboard.charts"
                :key="index + JSON.stringify(route.query)"
                :xs="24"
                :sm="12"
            >
                <div class="p-4 d-flex flex-column">
                    <p class="m-0 fs-6 fw-bold">
                        {{ chart.chartOptions?.displayName ?? chart.id }}
                    </p>
                    <p
                        v-if="chart.chartOptions?.description"
                        class="m-0 fw-light"
                    >
                        <small>{{ chart.chartOptions.description }}</small>
                    </p>

                    <div class="mt-4 flex-grow-1">
                        <component
                            :is="types[chart.type]"
                            :source="chart.content"
                            :chart
                            :identifier="custom.id"
                        />
                    </div>
                </div>
            </el-col>
        </el-row>
    </div>
    <div v-else>
        <el-row class="custom">
            <el-col
                v-for="chart in defaultCharts"
                :key="chart.content"
                :xs="24"
                :sm="12"
            >
                <div
                    class="p-4 d-flex flex-column"
                >
                    <p class="m-0 fs-6 fw-bold">
                        {{ chart.chartOptions?.displayName ?? chart.id }}
                    </p>
                    <p
                        v-if="chart.chartOptions?.description"
                        class="m-0 fw-light"
                    >
                        <small>{{ chart.chartOptions.description }}</small>
                    </p>


                    <div class="mt-4 flex-grow-1">
                        <component
                            :is="types[chart.type]"
                            :source="chart.content"
                            :chart
                            :identifier="custom.id"
                            is-preview
                        />
                    </div>
                </div>
            </el-col>
        </el-row>
    </div>
</template>

<script setup>
    import {computed, onBeforeMount, ref, watch} from "vue";
    import {useRoute, useRouter} from "vue-router";
    import {useStore} from "vuex";
    import {useI18n} from "vue-i18n";

    import {apiUrl} from "override/utils/route";

    import defautDashboardSource from "../../assets/dashboard/default_dashboard_definition.yaml?raw"

    import Header from "./components/Header.vue";

    import KestraFilter from "../filter/KestraFilter.vue";
    import Markdown from "../layout/Markdown.vue";
    import TimeSeries from "./components/charts/custom/TimeSeries.vue";
    import Bar from "./components/charts/custom/Bar.vue";
    import Pie from "./components/charts/custom/Pie.vue";
    import Table from "./components/tables/custom/Table.vue";

    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";
    import _cloneDeep from "lodash/cloneDeep.js";
    import yaml from "yaml";

    const router = useRouter();
    const route = useRoute();
    const store = useStore();
    const {t} = useI18n({useScope: "global"});
    const user = store.getters["auth/user"];

    const props = defineProps({
        embed: {
            type: Boolean,
            default: false,
        },
        flow: {
            type: Boolean,
            default: false,
        },
        flowId: {
            type: String,
            required: false,
            default: null,
        },
        namespace: {
            type: String,
            required: false,
            default: null,
        },
        restoreURL: {
            type: Boolean,
            default: true,
        },
        id: {
            type: String,
            required: false,
            default: null,
        },
        containerClass: {
            type: String,
            required: false,
            default: null,
        },
    });

    const customDashboardsEnabled = computed(
        () => store.state.misc?.configs?.isCustomDashboardsEnabled,
    );

    // Custom Dashboards
    const custom = ref({id: Math.random(), shown: false, dashboard: {}});
    const defaultCharts = ref([]);
    const handleCustomUpdate = async (v) => {
        let dashboard = {};

        if (route.name === "home") {
            router.replace({
                params: {...route.params, id: v?.id ?? "default"},
                query: route.params.id != v?.id ? {} : {...route.query},
            });
            if (v && v.id !== "default") {
                dashboard = await store.dispatch("dashboard/load", v.id);
            } else {
                validateAndLoadAllCharts();
            }

            custom.value = {
                id: Math.random(),
                shown: !v || v.id === "default" ? false : true,
                dashboard,
            };
        }
    };
    const refreshCustom = async () => {
        const ID = custom.value.dashboard.id;
        let dashboard = await store.dispatch("dashboard/load", ID);
        custom.value = {id: Math.random(), shown: true, dashboard};
    };
    const types = {
        "io.kestra.plugin.core.dashboard.chart.TimeSeries": TimeSeries,
        "io.kestra.plugin.core.dashboard.chart.Bar": Bar,
        "io.kestra.plugin.core.dashboard.chart.Markdown": Markdown,
        "io.kestra.plugin.core.dashboard.chart.Table": Table,
        "io.kestra.plugin.core.dashboard.chart.Pie": Pie,
    };

    const defaultNumbers = {flows: 0, triggers: 0};
    const numbers = ref({...defaultNumbers});
    const numbersLoading = ref(false);
    const fetchNumbers = () => {
        if (props.flowId) {
            return;
        }

        numbersLoading.value = true;
        store.$http
            .post(`${apiUrl(store)}/stats/summary`, mergeQuery())
            .then((response) => {
                if (!response.data) return;
                numbers.value = {...defaultNumbers, ...response.data};
            })
            .finally(() => {
                numbersLoading.value = false;
            });
    };

    const executions = ref({raw: {}, all: {}, yesterday: {}, today: {}});
    const transformer = (data) => {
        return data.reduce((accumulator, value) => {
            accumulator = accumulator || {executionCounts: {}, duration: {}};

            for (const key in value.executionCounts) {
                accumulator.executionCounts[key] =
                    (accumulator.executionCounts[key] || 0) +
                    value.executionCounts[key];
            }

            for (const key in value.duration) {
                accumulator.duration[key] =
                    (accumulator.duration[key] || 0) + value.duration[key];
            }

            return accumulator;
        }, null);
    };

    const mergeQuery = () => {
        let queryFilter = _cloneDeep(route.query);

        if (props.namespace) {
            queryFilter["namespace"] = props.namespace;
        }

        if (props.flowId) {
            queryFilter["flowId"] = props.flowId;
        }

        return queryFilter;
    }

    const executionsLoading = ref(false);
    const fetchExecutions = () => {
        executionsLoading.value = true;

        return store.dispatch("stat/daily", mergeQuery())
            .then((response) => {
                const sorted = response.sort(
                    (a, b) => new Date(b.date) - new Date(a.date),
                );

                executions.value = {
                    raw: sorted,
                    all: transformer(sorted),
                    yesterday: sorted.at(-2),
                    today: sorted.at(-1),
                };
            })
            .finally(() => {
                executionsLoading.value = false;
            });
    };

    const namespaceExecutions = ref({});

    const fetchNamespaceExecutions = () => {
        store.dispatch("stat/dailyGroupByNamespace", mergeQuery()).then((response) => {
            namespaceExecutions.value = response;
        });
    };

    const validateAndLoadAllCharts = async () => {
        defaultCharts.value = [];
        let charts = [];
        const allCharts = YAML_UTILS.getAllCharts(defautDashboardSource);
        for (const chart of allCharts) {
            const yamlChart = yaml.stringify(chart);
            charts.push({...chart, content: yamlChart});
        }
        defaultCharts.value = charts;
    }

    onBeforeMount(() => {
        handleCustomUpdate(route.params?.id ? {id: route.params?.id} : undefined);
    });

    watch(
        route,
        async () => {
            await handleCustomUpdate(route.params?.id ? {id: route.params?.id} : undefined);
            validateAndLoadAllCharts();
        },
        {immediate: true, deep: true},
    );
</script>

<style lang="scss" scoped>
@import "@kestra-io/ui-libs/src/scss/variables";

$spacing: 20px;

.dashboard-filters,
.dashboard {
    padding: 0 2rem;
    margin: 0;

    .description {
        border: none !important;
        color: var(ks-content-secondary);
    }
}

$media-md: 500px;
$media-lg: 1000px;

.dashboard{
    container-type: inline-size;
    padding-bottom: 1rem;
    margin: 1rem 0;
    width: 100%;
    display: flex;
    flex-direction: column;
    gap: 1rem;
    display: grid;
    grid-template-columns: repeat(12, 1fr);
}



.card {
    box-shadow: 0px 2px 4px 0px var(--ks-card-shadow);
    background: var(--ks-background-card);
    color: var(--ks-content-primary);
    border: 1px solid var(--ks-border-primary);
    border-radius: $border-radius;
    overflow: hidden;
    flex-shrink: 0;
    grid-column: span 12;
    @container (width > #{$media-md}) {
        grid-column: span 6;
    }
    @container (width > #{$media-lg}) {
        grid-column: span 3;
    }
}

@container (width > #{$media-md}) {
    .card-1\/2, .card-2\/3, .card-1\/3 {
        grid-column: span 12;
    }
}

.card-1\/2{
    @container (width > #{$media-lg}) {
        grid-column: span 6;
    }
}

.card-2\/3{
    @container (width > #{$media-lg}) {
        grid-column: span 8;
    }
}

.card-1\/3{
    @container (width > #{$media-lg}) {
        grid-column: span 4;
    }
}

.card-1{
    @container (width > #{$media-md}) {
        grid-column: span 12;
    }
}

.dashboard-filters {
    margin: 24px 0 0 0;
    padding-bottom: 0;

    & .el-row {
        padding: 0 5px;
    }

    & .el-col {
        padding-bottom: 0 !important;
    }
}

.description {
    padding: 0 2rem 1rem 2rem;
    margin: 0;
    color: var(--ks-content-secondary);
}

.custom {
    padding: 0 2rem 1rem 2rem;

    &.el-row {
        width: 100%;

        & .el-col {
            padding-bottom: $spacing;

            &:nth-of-type(even) > div {
                margin-left: 1rem;
            }

            & > div {
                height: 100%;
                background: var(--ks-background-card);
                border: 1px solid var(--ks-border-primary);
                border-radius: $border-radius;
            }
        }
    }
}

:deep(.legend) {
    &::-webkit-scrollbar {
        height: 5px;
        width: 5px;
    }

    &::-webkit-scrollbar-track {
        background: var(--ks-background-body);
    }

    &::-webkit-scrollbar-thumb {
        background: var(--ks-border-primary);
        border-radius: 5px;
    }

    &::-webkit-scrollbar-thumb:hover {
        background: var(--ks-button-background-primary-hover);
    }
}
</style>

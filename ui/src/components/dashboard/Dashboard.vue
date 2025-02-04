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
        <KestraFilter
            :prefix="custom.shown ? 'custom_dashboard' : 'dashboard'"
            :include="
                custom.shown
                    ? ['relative_date', 'absolute_date', 'namespace', 'labels']
                    : [
                        'namespace',
                        'state',
                        'scope',
                        'relative_date',
                        'absolute_date',
                    ]
            "
            :buttons="{
                refresh: {
                    shown: true,
                    callback: custom.shown ? refreshCustom : fetchAll,
                },
                settings: {shown: false},
            }"
            :dashboards="{shown: customDashboardsEnabled}"
            @dashboard="(v) => handleCustomUpdate(v)"
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
    <div v-else class="dashboard">
        <Card
            :icon="CheckBold"
            :label="t('dashboard.success_ratio')"
            :tooltip="t('dashboard.success_ratio_tooltip')"
            :value="stats.success"
            :redirect="{
                name: 'executions/list',
                query: {
                    state: State.SUCCESS,
                    scope: 'USER',
                    size: 100,
                    page: 1,
                },
            }"
        />

        <Card
            :icon="Alert"
            :label="t('dashboard.failure_ratio')"
            :tooltip="t('dashboard.failure_ratio_tooltip')"
            :value="stats.failed"
            :redirect="{
                name: 'executions/list',
                query: {
                    state: State.FAILED,
                    scope: 'USER',
                    size: 100,
                    page: 1,
                },
            }"
        />

        <Card
            :icon="FileTree"
            :label="t('flows')"
            :value="numbers.flows"
            :redirect="{
                name: 'flows/list',
                query: {scope: 'USER', size: 100, page: 1},
            }"
        />

        <Card
            :icon="LightningBolt"
            :label="t('triggers')"
            :value="numbers.triggers"
            :redirect="{
                name: 'admin/triggers',
                query: {size: 100, page: 1},
            }"
        />

        <ExecutionsBar
            :data="graphData"
            :total="stats.total"
            class="card card-2/3"
        />

        <ExecutionsDoughnut
            :data="graphData"
            :total="stats.total"
            class="card card-1/3"
        />

        <div v-if="props.flow" class="h-100 p-4 card card-1/2">
            <span class="d-flex justify-content-between">
                <span class="fs-6 fw-bold">
                    {{ t("dashboard.description") }}
                </span>
                <el-button
                    :icon="BookOpenOutline"
                    @click="descriptionDialog = true"
                >
                    {{ t("open") }}
                </el-button>

                <el-dialog
                    v-model="descriptionDialog"
                    :title="$t('description')"
                >
                    <Markdown
                        :source="description"
                        class="p-4 description"
                    />
                </el-dialog>
            </span>

            <Markdown :source="description" class="p-4 description" />
        </div>
        <ExecutionsInProgress
            v-else
            :flow="props.flowId"
            :namespace="props.namespace"
            class="card card-1/2"
        />

        <ExecutionsNextScheduled
            v-if="props.flow"
            :flow="props.flowId"
            :namespace="props.namespace"
            class="card card-1/2"
        />

        <ExecutionsDoughnut
            v-if="props.flow"
            :data="graphData"
            :total="stats.total"
            class="card card-1/2"
        />
        <ExecutionsNextScheduled
            v-else-if="isAllowedTriggers"
            :flow="props.flowId"
            :namespace="props.namespace"
            class="card card-1/2"
        />

        <ExecutionsEmptyNextScheduled v-else class="card card-1/2" />
        <ExecutionsNamespace
            v-if="!props.flow"
            class="card card-1"
            :data="namespaceExecutions"
            :total="stats.total"
        />
        <Logs v-if="!props.flow" :data="logs" class="card card-1" />
    </div>
</template>

<script setup>
    import {computed, onBeforeMount, ref, watch} from "vue";
    import {useRoute, useRouter} from "vue-router";
    import {useStore} from "vuex";
    import {useI18n} from "vue-i18n";

    import moment from "moment";

    import {apiUrl} from "override/utils/route";
    import {State} from "@kestra-io/ui-libs"

    import Header from "./components/Header.vue";
    import Card from "./components/Card.vue";

    import KestraFilter from "../filter/KestraFilter.vue";

    import ExecutionsBar from "./components/charts/executions/Bar.vue";
    import ExecutionsDoughnut from "./components/charts/executions/Doughnut.vue";
    import ExecutionsNamespace from "./components/charts/executions/Namespace.vue";
    import Logs from "./components/charts/logs/Bar.vue";

    import ExecutionsInProgress from "./components/tables/executions/InProgress.vue";
    import ExecutionsNextScheduled from "./components/tables/executions/NextScheduled.vue";
    import ExecutionsEmptyNextScheduled from "./components/tables/executions/EmptyNextScheduled.vue";

    import Markdown from "../layout/Markdown.vue";
    import TimeSeries from "./components/charts/custom/TimeSeries.vue";
    import Bar from "./components/charts/custom/Bar.vue";
    import Pie from "./components/charts/custom/Pie.vue";
    import Table from "./components/tables/custom/Table.vue";

    import CheckBold from "vue-material-design-icons/CheckBold.vue";
    import Alert from "vue-material-design-icons/Alert.vue";
    import LightningBolt from "vue-material-design-icons/LightningBolt.vue";
    import FileTree from "vue-material-design-icons/FileTree.vue";
    import BookOpenOutline from "vue-material-design-icons/BookOpenOutline.vue";
    import permission from "../../models/permission.js";
    import action from "../../models/action.js";
    import _cloneDeep from "lodash/cloneDeep.js";

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
    });

    const customDashboardsEnabled = computed(
        () => store.state.misc?.configs?.isCustomDashboardsEnabled,
    );

    // Custom Dashboards
    const custom = ref({id: Math.random(), shown: false, dashboard: {}});
    const handleCustomUpdate = async (v) => {
        let dashboard = {};

        if (route.name === "home") {
            router.replace({
                params: {...route.params, id: v?.id ?? "default"},
                query: {...route.query},
            });
            if (v && v.id !== "default") {
                dashboard = await store.dispatch("dashboard/load", v.id);
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

    const descriptionDialog = ref(false);
    const description = props.flow
        ? (store.state?.flow?.flow?.description ??
            t("dashboard.no_flow_description"))
        : undefined;

    const defaultNumbers = {flows: 0, triggers: 0};
    const numbers = ref({...defaultNumbers});
    const fetchNumbers = () => {
        if (props.flowId) {
            return;
        }

        store.$http
            .post(`${apiUrl(store)}/stats/summary`, mergeQuery())
            .then((response) => {
                if (!response.data) return;
                numbers.value = {...defaultNumbers, ...response.data};
            });
    };

    const executions = ref({raw: {}, all: {}, yesterday: {}, today: {}});
    const stats = computed(() => {
        const counts = executions?.value?.all?.executionCounts || {};
        const terminatedStates = State.getTerminatedStates();
        const statesToCount = Object.fromEntries(
            Object.entries(counts).filter(([key]) =>
                terminatedStates.includes(key),
            ),
        );

        const total = Object.values(counts).reduce(
            (sum, count) => sum + count,
            0,
        );

        const totalTerminated = Object.values(statesToCount).reduce(
            (sum, count) => sum + count,
            0,
        );

        const successStates = ["SUCCESS", "CANCELLED", "WARNING", "SKIPPED"];
        const failedStates = ["FAILED", "KILLED"];
        const sumStates = (states) =>
            states.reduce((sum, state) => sum + (statesToCount[state] || 0), 0);
        const successRatio =
            totalTerminated > 0 ? (sumStates(successStates) / totalTerminated) * 100 : 0;
        const failedRatio = totalTerminated > 0 ? (sumStates(failedStates) / totalTerminated) * 100 : 0;

        return {
            total: total,
            totalTerminated: totalTerminated,
            success: `${successRatio.toFixed(2)}%`,
            failed: `${failedRatio.toFixed(2)}%`,
        };
    });
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

    const fetchExecutions = () => {
        store.dispatch("stat/daily", mergeQuery()).then((response) => {
            const sorted = response.sort(
                (a, b) => new Date(b.date) - new Date(a.date),
            );

            executions.value = {
                raw: sorted,
                all: transformer(sorted),
                yesterday: sorted.at(-2),
                today: sorted.at(-1),
            };
        });
    };

    const graphData = computed(() => store.state.stat.daily || []);

    const namespaceExecutions = ref({});

    const fetchNamespaceExecutions = () => {
        store.dispatch("stat/dailyGroupByNamespace", mergeQuery()).then((response) => {
            namespaceExecutions.value = response;
        });
    };

    const logs = ref([]);
    const fetchLogs = () => {
        store.dispatch("stat/logDaily", mergeQuery()).then((response) => {
            logs.value = response;
        });
    };

    const fetchAll = async () => {
        route.query.startDate = route.query.timeRange
            ? moment()
                .subtract(
                    moment.duration(route.query.timeRange).as("milliseconds"),
                )
                .toISOString(true)
            : route.query.startDate ||
                moment()
                    .subtract(moment.duration("PT720H").as("milliseconds"))
                    .toISOString(true);
        route.query.endDate = route.query.timeRange
            ? moment().toISOString(true)
            : route.query.endDate || moment().toISOString(true);

        if (!custom.value.shown) {
            try {
                await Promise.any([
                    fetchNumbers(),
                    fetchExecutions(),
                    fetchNamespaceExecutions(),
                    fetchLogs(),
                ]);
            } catch (error) {
                console.error("All promises failed:", error);
            }
        }
    };

    const isAllowedTriggers = computed(() => {
        return (
            user &&
            user.isAllowed(permission.FLOW, action.READ, props.value?.namespace)
        );
    });

    onBeforeMount(() => {
        handleCustomUpdate(route.params?.id ? {id: route.params?.id} : undefined);
    });

    watch(
        route,
        async () => {
            await handleCustomUpdate(route.params?.id ? {id: route.params?.id} : undefined);
            fetchAll();
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
        color: #564a75;

        html.dark & {
            color: #e3dbff;
        }
    }
}

$media-md: 600px;
$media-lg: 1200px;

.dashboard{
    padding-bottom: 1rem;
    margin: 1rem 0;
    width: 100%;
    display: flex;
    flex-direction: column;
    gap: 1rem;
    @media (min-width: $media-md) {
        display: grid;
        grid-template-columns: repeat(12, 1fr);
    }
}



.card {
    box-shadow: 0px 2px 4px 0px var(--ks-card-shadow);
    background: var(--ks-background-card);
    color: var(--ks-content-primary);
    border: 1px solid var(--ks-border-primary);
    border-radius: $border-radius;
    overflow: hidden;
    flex-shrink: 0;
    @media (min-width: $media-md) {
        grid-column: span 6;
    }
    @media (min-width: $media-lg) {
        grid-column: span 3;
    }
}

@media (min-width: $media-md) {
    .card-1\/2, .card-2\/3, .card-1\/3 {
        grid-column: span 12;
    }
}

.card-1\/2{
    @media (min-width: $media-lg) {
        grid-column: span 6;
    }
}

.card-2\/3{
    @media (min-width: $media-lg) {
        grid-column: span 8;
    }
}

.card-1\/3{
    @media (min-width: $media-lg) {
        grid-column: span 4;
    }
}

.card-1{
    @media (min-width: $media-md) {
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
        background: var(--ks-background-card);
    }

    &::-webkit-scrollbar-thumb {
        background: var(--ks-button-background-primary);
        border-radius: 0px;
    }
}
</style>

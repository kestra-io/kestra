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
                    ? ['relative_date', 'absolute_date']
                    : [
                        'namespace',
                        'state',
                        'scope',
                        'relative_date',
                        'absolute_date',
                    ]
            "
            :refresh="{
                shown: true,
                callback: custom.shown ? refreshCustom : fetchAll,
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
                :key="index"
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
        <el-row v-if="!props.flow">
            <el-col :xs="24" :sm="12" :lg="6">
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
                    class="me-2"
                />
            </el-col>
            <el-col :xs="24" :sm="12" :lg="6">
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
                    class="mx-2"
                />
            </el-col>
            <el-col :xs="24" :sm="12" :lg="6">
                <Card
                    :icon="FileTree"
                    :label="t('flows')"
                    :value="numbers.flows"
                    :redirect="{
                        name: 'flows/list',
                        query: {scope: 'USER', size: 100, page: 1},
                    }"
                    class="mx-2"
                />
            </el-col>
            <el-col :xs="24" :sm="12" :lg="6">
                <Card
                    :icon="LightningBolt"
                    :label="t('triggers')"
                    :value="numbers.triggers"
                    :redirect="{
                        name: 'admin/triggers',
                        query: {size: 100, page: 1},
                    }"
                    class="ms-2"
                />
            </el-col>
        </el-row>

        <el-row>
            <el-col :xs="24" :lg="props.flow ? 24 : 16">
                <ExecutionsBar
                    :data="graphData"
                    :total="stats.total"
                    :class="{'me-2': !props.flow}"
                />
            </el-col>
            <el-col v-if="!props.flow" :xs="24" :lg="8">
                <ExecutionsDoughnut
                    :data="graphData"
                    :total="stats.total"
                    class="ms-2"
                />
            </el-col>
        </el-row>

        <el-row>
            <el-col :xs="24" :lg="props.flow ? 7 : 12">
                <div v-if="props.flow" class="h-100 p-4 me-2">
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
                    :flow="props.flowID"
                    :namespace="props.namespace"
                    class="me-2"
                />
            </el-col>
            <el-col v-if="props.flow" :xs="24" :lg="10">
                <ExecutionsNextScheduled
                    :flow="props.flowID"
                    :namespace="filters.namespace"
                    class="mx-2"
                />
            </el-col>
            <el-col :xs="24" :lg="props.flow ? 7 : 12">
                <ExecutionsDoughnut
                    v-if="props.flow"
                    :data="graphData"
                    :total="stats.total"
                    class="ms-2"
                />
                <ExecutionsNextScheduled
                    v-else-if="isAllowedTriggers"
                    :flow="props.flowID"
                    :namespace="filters.namespace"
                    class="ms-2"
                />
                <ExecutionsEmptyNextScheduled v-else />
            </el-col>
        </el-row>

        <el-row v-if="!props.flow">
            <el-col :xs="24">
                <ExecutionsNamespace
                    :data="filteredNamespaceExecutions"
                    :total="stats.total"
                />
            </el-col>
        </el-row>

        <el-row v-if="!props.flow">
            <el-col :xs="24">
                <Logs :data="logs" />
            </el-col>
        </el-row>
    </div>
</template>

<script setup>
    import {computed, onBeforeMount, ref, watch} from "vue";
    import {useRoute, useRouter} from "vue-router";
    import {useStore} from "vuex";
    import {useI18n} from "vue-i18n";

    import moment from "moment";

    import {apiUrl} from "override/utils/route";
    import State from "../../utils/state";

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
    // import {storageKeys} from "../../utils/constants";

    const router = useRouter();
    const route = useRoute();
    const store = useStore();
    const {t} = useI18n({useScope: "global"});
    const user = store.getters["auth/user"];

    // const defaultNamespace = localStorage.getItem(storageKeys.DEFAULT_NAMESPACE) || null;
    const props = defineProps({
        embed: {
            type: Boolean,
            default: false,
        },
        flow: {
            type: Boolean,
            default: false,
        },
        flowID: {
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
            router.replace({params: {...route.params, id: v?.id ?? "default"}});
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

    const filters = ref({
        namespace: null,
        state: [],
        startDate: null,
        endDate: null,
        timeRange: "PT720H",
        scope: ["USER"],
    });

    const defaultNumbers = {flows: 0, triggers: 0};
    const numbers = ref({...defaultNumbers});
    const fetchNumbers = () => {
        store.$http
            .post(`${apiUrl(store)}/stats/summary`, route.query)
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

        const total = Object.values(statesToCount).reduce(
            (sum, count) => sum + count,
            0,
        );
        const successStates = ["SUCCESS", "CANCELLED", "WARNING"];
        const failedStates = ["FAILED", "KILLED", "RETRIED"];
        const sumStates = (states) =>
            states.reduce((sum, state) => sum + (statesToCount[state] || 0), 0);

        const successRatio =
            total > 0 ? (sumStates(successStates) / total) * 100 : 0;
        const failedRatio = total > 0 ? (sumStates(failedStates) / total) * 100 : 0;

        return {
            total,
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
    const fetchExecutions = () => {
        store.dispatch("stat/daily", route.query).then((response) => {
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
    const filteredNamespaceExecutions = computed(() => {
        const namespace = filters.value.namespace;

        return !namespace
            ? namespaceExecutions.value
            : {[namespace]: namespaceExecutions.value[namespace]};
    });
    const fetchNamespaceExecutions = () => {
        store.dispatch("stat/dailyGroupByNamespace").then((response) => {
            namespaceExecutions.value = response;
        });
    };

    const logs = ref([]);
    const fetchLogs = () => {
        store.dispatch("stat/logDaily", route.query).then((response) => {
            logs.value = response;
        });
    };

    // const handleDatesUpdate = (dates) => {
    //     const {startDate, endDate, timeRange} = dates;

    //     if (startDate && endDate) {
    //         filters.value = {...filters.value, startDate, endDate, timeRange};
    //     } else if (timeRange) {
    //         filters.value = {
    //             ...filters.value,
    //             startDate: moment()
    //                 .subtract(moment.duration(timeRange).as("milliseconds"))
    //                 .toISOString(true),
    //             endDate: moment().toISOString(true),
    //             timeRange,
    //         };
    //     }

    //     return Promise.resolve(filters.value);
    // };

    // const updateParams = async (params) => {
    //     const completeParams = await handleDatesUpdate({
    //         ...filters.value,
    //         ...params,
    //     });

    //     filters.value = {
    //         namespace: props.namespace ?? completeParams.namespace,
    //         flowId: props.flowID ?? null,
    //         state: completeParams.state?.filter(Boolean).length
    //             ? [].concat(completeParams.state)
    //             : undefined,
    //         startDate: completeParams.startDate,
    //         endDate: completeParams.endDate,
    //         scope: completeParams.scope?.filter(Boolean).length
    //             ? [].concat(completeParams.scope)
    //             : undefined,
    //     };

    //     completeParams.flowId = props.flowID ?? null;

    //     delete completeParams.timeRange;
    //     for (const key in completeParams) {
    //         if (completeParams[key] == null) {
    //             delete completeParams[key];
    //         }
    //     }

    //     router.push({query: completeParams}).then(fetchAll());
    // };

    const fetchAll = async () => {
        // if (!route.query.startDate || !route.query.endDate) {
        //     route.query.startDate = moment()
        //         .subtract(moment.duration("PT720H").as("milliseconds"))
        //         .toISOString(true);
        //     route.query.endDate = moment().toISOString(true);
        // }

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
            user.isAllowed(permission.FLOW, action.READ, filters.value.namespace)
        );
    });

    onBeforeMount(() => {
        handleCustomUpdate(route.params?.id ? {id: route.params?.id} : undefined);

        if (props.flowID) {
            router.replace({query: {...route.query, flowId: props.flowID}});
        }

    // if (!route.query.namespace && props.restoreURL) {
    //     router.replace({query: {...route.query, namespace: defaultNamespace}});
    //     filters.value.namespace = route.query.namespace || defaultNamespace;
    // }
    // else {
    //     filters.value.namespace = null
    // }

    // updateParams(route.query);
    });

    watch(
        route,
        () => {
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
    padding: 0 32px;

    & .el-row {
        width: 100%;

        & .el-col {
            padding-bottom: $spacing;

            & div {
                background: var(--card-bg);
                border: 1px solid var(--bs-gray-300);
                border-radius: $border-radius;

                html.dark & {
                    border-color: var(--bs-gray-600);
                }
            }
        }
    }

    .description {
        border: none !important;
        color: #564a75;

        html.dark & {
            color: #e3dbff;
        }
    }
}

.dashboard {
    margin: 0;
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
    padding: 0px 32px;
    margin: 0;
    color: var(--bs-gray-700);
}

.custom {
    padding: 24px 32px;

    &.el-row {
        width: 100%;

        & .el-col {
            padding-bottom: $spacing;

            &:nth-of-type(even) > div {
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
}

:deep(.legend) {
    &::-webkit-scrollbar {
        height: 5px;
        width: 5px;
    }

    &::-webkit-scrollbar-track {
        background: var(--card-bg);
    }

    &::-webkit-scrollbar-thumb {
        background: var(--bs-primary);
        border-radius: 0px;
    }
}
</style>

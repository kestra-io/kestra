<template>
    <Header v-if="!embed" />

    <div class="filters">
        <el-row :gutter="10" class="mx-0">
            <el-col :xs="24" :lg="4">
                <namespace-select
                    v-model="filters.namespace"
                    data-type="flow"
                    :disabled="props.flow || !!props.namespace"
                    @update:model-value="updateParams"
                />
            </el-col>
            <el-col :xs="24" :lg="4">
                <el-select
                    v-model="filters.state"
                    clearable
                    filterable
                    collapse-tags
                    multiple
                    :placeholder="$t('state')"
                    @update:model-value="updateParams"
                >
                    <el-option
                        v-for="item in State.allStates()"
                        :key="item.key"
                        :label="item.key"
                        :value="item.key"
                    />
                </el-select>
            </el-col>
            <el-col :xs="24" :lg="8">
                <DateFilter
                    @update:is-relative="toggleAutoRefresh"
                    @update:filter-value="(dates) => updateParams(dates)"
                    absolute
                    wrap
                    class="d-flex flex-row"
                />
            </el-col>
            <el-col :xs="24" :sm="16" :lg="4">
                <scope-filter-buttons
                    v-model="filters.scope"
                    :label="$t('data')"
                    @update:model-value="updateParams"
                />
            </el-col>
            <el-col :xs="24" :sm="8" :lg="4">
                <refresh-button
                    class="float-right"
                    @refresh="refresh()"
                    :can-auto-refresh="canAutoRefresh"
                />
            </el-col>
        </el-row>
    </div>

    <div class="dashboard">
        <el-row v-if="!props.flow" :gutter="20" class="mx-0">
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
                />
            </el-col>
        </el-row>

        <el-row :gutter="20" class="mx-0">
            <el-col :xs="24" :lg="props.flow ? 24 : 16">
                <ExecutionsBar :data="graphData" :total="stats.total" />
            </el-col>
            <el-col v-if="!props.flow" :xs="24" :lg="8">
                <ExecutionsDoughnut :data="graphData" :total="stats.total" />
            </el-col>
        </el-row>

        <el-row :gutter="20" class="mx-0">
            <el-col :xs="24" :lg="props.flow ? 7 : 12">
                <div v-if="props.flow" class="h-100 p-4">
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
                />
            </el-col>
            <el-col v-if="props.flow" :xs="24" :lg="10">
                <ExecutionsNextScheduled
                    :flow="props.flowID"
                    :namespace="filters.namespace"
                />
            </el-col>
            <el-col :xs="24" :lg="props.flow ? 7 : 12">
                <ExecutionsDoughnut
                    v-if="props.flow"
                    :data="graphData"
                    :total="stats.total"
                />
                <ExecutionsNextScheduled
                    v-else-if="isAllowedTriggers"
                    :flow="props.flowID"
                    :namespace="filters.namespace"
                />
                <ExecutionsEmptyNextScheduled v-else />
            </el-col>
        </el-row>

        <el-row v-if="!props.flow" :gutter="20" class="mx-0">
            <el-col :xs="24">
                <ExecutionsNamespace
                    :data="filteredNamespaceExecutions"
                    :total="stats.total"
                />
            </el-col>
        </el-row>

        <el-row v-if="!props.flow" :gutter="20" class="mx-0">
            <el-col :xs="24">
                <Logs :data="logs" />
            </el-col>
        </el-row>
    </div>
</template>

<script setup>
    import {onBeforeMount, ref, computed} from "vue";
    import {useRouter, useRoute} from "vue-router";
    import {useStore} from "vuex";
    import {useI18n} from "vue-i18n";

    import moment from "moment";

    import {apiUrl} from "override/utils/route";
    import State from "../../utils/state";

    import Header from "./components/Header.vue";
    import Card from "./components/Card.vue";

    import NamespaceSelect from "../namespace/NamespaceSelect.vue";
    import DateFilter from "../executions/date-select/DateFilter.vue";
    import ScopeFilterButtons from "../layout/ScopeFilterButtons.vue";
    import RefreshButton from "../layout/RefreshButton.vue";

    import ExecutionsBar from "./components/charts/executions/Bar.vue";
    import ExecutionsDoughnut from "./components/charts/executions/Doughnut.vue";
    import ExecutionsNamespace from "./components/charts/executions/Namespace.vue";
    import Logs from "./components/charts/logs/Bar.vue";

    import ExecutionsInProgress from "./components/tables/executions/InProgress.vue";
    import ExecutionsNextScheduled from "./components/tables/executions/NextScheduled.vue";
    import ExecutionsEmptyNextScheduled from "./components/tables/executions/EmptyNextScheduled.vue";

    import Markdown from "../layout/Markdown.vue";

    import CheckBold from "vue-material-design-icons/CheckBold.vue";
    import Alert from "vue-material-design-icons/Alert.vue";
    import LightningBolt from "vue-material-design-icons/LightningBolt.vue";
    import FileTree from "vue-material-design-icons/FileTree.vue";
    import BookOpenOutline from "vue-material-design-icons/BookOpenOutline.vue";
    import permission from "../../models/permission.js";
    import action from "../../models/action.js";

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
    });

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

    const refresh = async () => {
        await updateParams({
            startDate: filters.value.startDate,
            endDate: moment().toISOString(true),
        });
        fetchAll();
    };
    const canAutoRefresh = ref(false);
    const toggleAutoRefresh = (event) => {
        canAutoRefresh.value = event;
    };

    const defaultNumbers = {flows: 0, triggers: 0};
    const numbers = ref({...defaultNumbers});
    const fetchNumbers = () => {
        store.$http
            .post(`${apiUrl(store)}/stats/summary`, filters.value)
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
        store.dispatch("stat/daily", filters.value).then((response) => {
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
        store.dispatch("stat/logDaily", filters.value).then((response) => {
            logs.value = response;
        });
    };

    const handleDatesUpdate = (dates) => {
        const {startDate, endDate, timeRange} = dates;

        if (startDate && endDate) {
            filters.value = {...filters.value, startDate, endDate, timeRange};
        } else if (timeRange) {
            filters.value = {
                ...filters.value,
                startDate: moment()
                    .subtract(moment.duration(timeRange).as("milliseconds"))
                    .toISOString(true),
                endDate: moment().toISOString(true),
                timeRange,
            };
        }

        return Promise.resolve(filters.value);
    };

    const updateParams = async (params) => {
        const completeParams = await handleDatesUpdate({
            ...filters.value,
            ...params,
        });

        filters.value = {
            namespace: props.namespace ?? completeParams.namespace,
            flowId: props.flowID ?? null,
            state: completeParams.state?.filter(Boolean).length
                ? [].concat(completeParams.state)
                : undefined,
            startDate: completeParams.startDate,
            endDate: completeParams.endDate,
            scope: completeParams.scope?.filter(Boolean).length
                ? [].concat(completeParams.scope)
                : undefined,
        };

        completeParams.flowId = props.flowID ?? null;

        delete completeParams.timeRange;
        for (const key in completeParams) {
            if (completeParams[key] == null) {
                delete completeParams[key];
            }
        }

        router.push({query: completeParams}).then(fetchAll());
    };

    const fetchAll = async () => {
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
    };

    const isAllowedTriggers = computed(() => {
        return (
            user &&
            user.isAllowed(permission.FLOW, action.READ, filters.value.namespace)
        );
    });

    onBeforeMount(() => {
        filters.value.namespace = route.query.namespace ?? null;
        updateParams();
    });
</script>

<style lang="scss" scoped>
@import "@kestra-io/ui-libs/src/scss/variables";

$spacing: 20px;

.filters,
.dashboard {
    padding: $spacing;

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

.filters {
    padding-bottom: 0;

    & .el-row {
        padding: 0 5px;
    }

    & .el-col {
        padding-bottom: 0 !important;
    }
}
</style>

<template>
    <top-nav-bar v-if="topbar" :title="routeInfo.title">
        <template #additional-right>
            <ul>
                <li>
                    <div class="el-input el-input-file custom-upload">
                        <form ref="importForm">
                            <div class="el-input__wrapper">
                                <label for="importFlows">
                                    <Upload />
                                    {{ $t('import') }}
                                </label>
                                <input
                                    id="importFlows"
                                    class="el-input__inner"
                                    type="file"
                                    accept=".zip, .yml, .yaml"
                                    @change="importFlows()"
                                    ref="file"
                                >
                            </div>
                        </form>
                    </div>
                </li>
                <li>
                    <router-link :to="{name: 'flows/search'}">
                        <el-button :icon="TextBoxSearch">
                            {{ $t('source search') }}
                        </el-button>
                    </router-link>
                </li>
                <li>
                    <router-link :to="{name: 'flows/create', query: {namespace: $route.query.namespace}}" v-if="canCreate">
                        <el-button :icon="Plus" type="primary">
                            {{ $t('create') }}
                        </el-button>
                    </router-link>
                </li>
            </ul>
        </template>
    </top-nav-bar>
    <section data-component="FILENAME_PLACEHOLDER" :class="{'container': topbar}" v-if="ready">
        <div>
            <data-table
                @page-changed="onPageChanged"
                ref="dataTable"
                :total="total"
            >
                <template #navbar>
                    <KestraFilter
                        prefix="flows"
                        :include="['namespace', 'scope', 'labels']"
                        :buttons="{
                            refresh: {shown: false},
                            settings: {shown: true, charts: {shown: true, value: showChart, callback: onShowChartChange}}
                        }"
                    />
                </template>

                <template #top>
                    <el-card v-if="showStatChart()" shadow="never" class="mb-4">
                        <ExecutionsBar :data="daily" :total="executionsCount" />
                    </el-card>
                </template>

                <template #table>
                    <select-table
                        v-if="flows.length"
                        ref="selectTable"
                        :data="flows"
                        :default-sort="{prop: 'id', order: 'ascending'}"
                        table-layout="auto"
                        fixed
                        @row-dblclick="onRowDoubleClick"
                        @sort-change="onSort"
                        :row-class-name="rowClasses"
                        @selection-change="handleSelectionChange"
                        :selectable="canCheck"
                        class="flows-table"
                    >
                        <template #select-actions>
                            <bulk-select
                                :select-all="queryBulkAction"
                                :selections="selection"
                                :total="total"
                                @update:select-all="toggleAllSelection"
                                @unselect="toggleAllUnselected"
                            >
                                <el-button v-if="canRead" :icon="Download" @click="exportFlows()">
                                    {{ $t('export') }}
                                </el-button>
                                <el-button v-if="canDelete" @click="deleteFlows" :icon="TrashCan">
                                    {{ $t('delete') }}
                                </el-button>
                                <el-button v-if="canUpdate && anyFlowDisabled()" @click="enableFlows" :icon="FileDocumentCheckOutline">
                                    {{ $t('enable') }}
                                </el-button>
                                <el-button v-if="canUpdate && anyFlowEnabled()" @click="disableFlows" :icon="FileDocumentRemoveOutline">
                                    {{ $t('disable') }}
                                </el-button>
                            </bulk-select>
                        </template>
                        <template #default>
                            <el-table-column
                                prop="id"
                                sortable="custom"
                                :sort-orders="['ascending', 'descending']"
                                :label="$t('id')"
                            >
                                <template #default="scope">
                                    <div class="flow-id">
                                        <router-link
                                            :to="{name: 'flows/update', params: {namespace: scope.row.namespace, id: scope.row.id}}"
                                            class="me-1"
                                        >
                                            {{ $filters.invisibleSpace(scope.row.id) }}
                                        </router-link>
                                        <markdown-tooltip
                                            :id="scope.row.namespace + '-' + scope.row.id"
                                            :description="scope.row.description"
                                            :title="scope.row.namespace + '.' + scope.row.id"
                                        />
                                    </div>
                                </template>
                            </el-table-column>

                            <el-table-column :label="$t('labels')">
                                <template #default="scope">
                                    <labels :labels="scope.row.labels" />
                                </template>
                            </el-table-column>

                            <el-table-column
                                prop="namespace"
                                sortable="custom"
                                :sort-orders="['ascending', 'descending']"
                                :label="$t('namespace')"
                                :formatter="(_, __, cellValue) => $filters.invisibleSpace(cellValue)"
                            />

                            <el-table-column
                                prop="state.startDate"
                                :label="$t('last execution date')"
                                v-if="user.hasAny(permission.EXECUTION)"
                            >
                                <template #default="scope">
                                    <date-ago v-if="lastExecutionByFlowReady" :inverted="true" :date="getLastExecution(scope.row).startDate" />
                                </template>
                            </el-table-column>

                            <el-table-column
                                prop="state.current"
                                :label="$t('last execution status')"
                                v-if="user.hasAny(permission.EXECUTION)"
                            >
                                <template #default="scope">
                                    <status v-if="lastExecutionByFlowReady && getLastExecution(scope.row).lastStatus" :status="getLastExecution(scope.row).lastStatus" size="small" />
                                </template>
                            </el-table-column>

                            <el-table-column
                                prop="state"
                                :label="$t('execution statistics')"
                                v-if="user.hasAny(permission.EXECUTION)"
                                class-name="row-graph"
                            >
                                <template #default="scope">
                                    <ExecutionsBarChart
                                        v-if="dailyGroupByFlowReady"
                                        class="stats-chart"
                                        :duration="false"
                                        :scales="false"
                                        :data="chartData(scope.row)"
                                        small
                                        external-tooltip
                                        @click="tableChartClick.bind(null, scope.row.namespace, scope.row.id)"
                                    />
                                </template>
                            </el-table-column>

                            <el-table-column :label="$t('triggers')" class-name="row-action">
                                <template #default="scope">
                                    <trigger-avatar :flow="scope.row" />
                                </template>
                            </el-table-column>

                            <el-table-column column-key="action" class-name="row-action">
                                <template #default="scope">
                                    <router-link
                                        :to="{name: 'flows/update', params : {namespace: scope.row.namespace, id: scope.row.id}}"
                                    >
                                        <kicon :tooltip="$t('details')" placement="left">
                                            <TextSearch />
                                        </kicon>
                                    </router-link>
                                </template>
                            </el-table-column>
                        </template>
                    </select-table>
                </template>
            </data-table>
        </div>
    </section>
</template>

<script setup>
    import moment from "moment";
    import BulkSelect from "../layout/BulkSelect.vue";
    import SelectTable from "../layout/SelectTable.vue";
    import ExecutionsBar from "../dashboard/components/charts/executions/Bar.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import TextBoxSearch from "vue-material-design-icons/TextBoxSearch.vue";
    import Download from "vue-material-design-icons/Download.vue";
    import TrashCan from "vue-material-design-icons/TrashCan.vue";
    import FileDocumentRemoveOutline from "vue-material-design-icons/FileDocumentRemoveOutline.vue";
    import FileDocumentCheckOutline from "vue-material-design-icons/FileDocumentCheckOutline.vue";
    import Upload from "vue-material-design-icons/Upload.vue";
    import ExecutionsBarChart from "../dashboard/components/charts/executions/BarChart.vue";
    import KestraFilter from "../filter/KestraFilter.vue"
    import {chartClick} from "../../utils/charts.js";
    import {useRoute, useRouter} from "vue-router";

    const route = useRoute();
    const router = useRouter();

    function tableChartClick(namespace, flowId, e, elements){
        if (elements.length > 0 && elements[0].index !== undefined && elements[0].datasetIndex !== undefined) {
            chartClick(
                moment,
                router,
                route,
                {
                    date: e.chart.data.labels[elements[0].index],
                    state: e.chart.data.datasets[elements[0].datasetIndex].label,
                    namespace,
                    flowId
                }
            )
        }
    }
</script>

<script>
    import {mapState} from "vuex";
    import _merge from "lodash/merge";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import TextSearch from "vue-material-design-icons/TextSearch.vue";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import RouteContext from "../../mixins/routeContext";
    import DataTableActions from "../../mixins/dataTableActions";
    import DateAgo from "../layout/DateAgo.vue";
    import SelectTableActions from "../../mixins/selectTableActions";
    import RestoreUrl from "../../mixins/restoreUrl";
    import DataTable from "../layout/DataTable.vue";
    import Status from "../Status.vue";
    import TriggerAvatar from "./TriggerAvatar.vue";
    import MarkdownTooltip from "../layout/MarkdownTooltip.vue"
    import Kicon from "../Kicon.vue"
    import Labels from "../layout/Labels.vue"
    import {storageKeys} from "../../utils/constants";

    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions, SelectTableActions],
        components: {
            TextSearch,
            DataTable,
            DateAgo,
            Status,
            TriggerAvatar,
            MarkdownTooltip,
            Kicon,
            Labels,
            TopNavBar,
        },
        props: {
            topbar: {
                type: Boolean,
                default: true
            },
            namespace: {
                type: String,
                required: false,
                default: undefined
            },
        },
        data() {
            return {
                isDefaultNamespaceAllow: true,
                permission: permission,
                action: action,
                dailyGroupByFlowReady: false,
                lastExecutionByFlowReady: false,
                dailyReady: false,
                file: undefined,
                showChart: ["true", null].includes(localStorage.getItem(storageKeys.SHOW_FLOWS_CHART)),
            };
        },
        computed: {
            ...mapState("flow", ["flows", "total"]),
            ...mapState("stat", ["dailyGroupByFlow", "daily", "lastExecutions"]),
            ...mapState("auth", ["user"]),
            routeInfo() {
                return {
                    title: this.$t("flows")
                };
            },
            endDate() {
                return new Date();
            },
            startDate() {
                return this.$moment(this.endDate)
                    .add(-30, "days")
                    .toDate();
            },
            canCheck() {
                return this.canRead || this.canDelete || this.canUpdate;
            },
            canCreate() {
                return this.user && this.user.hasAnyActionOnAnyNamespace(permission.FLOW, action.CREATE);
            },
            canRead() {
                return this.user && this.user.isAllowed(permission.FLOW, action.READ, this.$route.query.namespace);
            },
            canDelete() {
                return this.user && this.user.isAllowed(permission.FLOW, action.DELETE, this.$route.query.namespace);
            },
            canUpdate() {
                return this.user && this.user.isAllowed(permission.FLOW, action.UPDATE, this.$route.query.namespace);
            },
            executionsCount() {
                return [...this.daily].reduce((a, b) => {
                    return a + Object.values(b.executionCounts).reduce((a, b) => a + b, 0);
                }, 0);
            }
        },
        beforeRouteEnter(to, from, next) {
            const defaultNamespace = localStorage.getItem(storageKeys.DEFAULT_NAMESPACE);
            const query = {...to.query};
            if (defaultNamespace) {
                query.namespace = defaultNamespace;
            } if (!query.scope) {
                query.scope = ["USER"];
            }
            next(vm => {
                vm.$router?.replace({query});
            });
        },
        methods: {
            selectionMapper(element) {
                return {
                    id: element.id,
                    namespace: element.namespace,
                    enabled: !element.disabled
                }
            },
            showStatChart() {
                return this.daily && this.showChart;
            },
            onShowChartChange(value) {
                this.showChart = value;
                localStorage.setItem(storageKeys.SHOW_FLOWS_CHART, value);
                if(this.showStatChart())
                    this.loadStats();
            },
            exportFlows() {
                this.$toast().confirm(
                    this.$t("flow export", {"flowCount": this.queryBulkAction ? this.total : this.selection.length}),
                    () => {
                        if (this.queryBulkAction) {
                            return this.$store
                                .dispatch("flow/exportFlowByQuery", this.loadQuery({
                                    namespace: this.$route.query.namespace ? [this.$route.query.namespace] : undefined,
                                    q: this.$route.query.q ? [this.$route.query.q] : undefined,
                                }, false))
                                .then(_ => {
                                    this.$toast().success(this.$t("flows exported"));
                                })
                        } else {
                            return this.$store
                                .dispatch("flow/exportFlowByIds", {ids: this.selection})
                                .then(_ => {
                                    this.$toast().success(this.$t("flows exported"));
                                })
                        }
                    },
                    () => {
                    }
                )
            },
            disableFlows() {
                this.$toast().confirm(
                    this.$t("flow disable", {"flowCount": this.queryBulkAction ? this.total : this.selection.length}),
                    () => {
                        if (this.queryBulkAction) {
                            return this.$store
                                .dispatch("flow/disableFlowByQuery", this.loadQuery({
                                    namespace: this.$route.query.namespace ? [this.$route.query.namespace] : undefined,
                                    q: this.$route.query.q ? [this.$route.query.q] : undefined,
                                }, false))
                                .then(r => {
                                    this.$toast().success(this.$t("flows disabled", {count: r.data.count}));
                                    this.loadData(() => {
                                    })
                                })
                        } else {
                            return this.$store
                                .dispatch("flow/disableFlowByIds", {ids: this.selection})
                                .then(r => {
                                    this.$toast().success(this.$t("flows disabled", {count: r.data.count}));
                                    this.loadData(() => {
                                    })
                                })
                        }
                    },
                    () => {
                    }
                )
            },
            anyFlowDisabled() {
                return this.selection.some(flow => !flow.enabled);
            },
            anyFlowEnabled() {
                return this.selection.some(flow => flow.enabled);
            },
            enableFlows() {
                this.$toast().confirm(
                    this.$t("flow enable", {"flowCount": this.queryBulkAction ? this.total : this.selection.length}),
                    () => {
                        if (this.queryBulkAction) {
                            return this.$store
                                .dispatch("flow/enableFlowByQuery", this.loadQuery({
                                    namespace: this.$route.query.namespace ? [this.$route.query.namespace] : undefined,
                                    q: this.$route.query.q ? [this.$route.query.q] : undefined,
                                }, false))
                                .then(r => {
                                    this.$toast().success(this.$t("flows enabled", {count: r.data.count}));
                                    this.loadData(() => {
                                    })
                                })
                        } else {
                            return this.$store
                                .dispatch("flow/enableFlowByIds", {ids: this.selection})
                                .then(r => {
                                    this.$toast().success(this.$t("flows enabled", {count: r.data.count}));
                                    this.loadData(() => {
                                    })
                                })
                        }
                    },
                    () => {
                    }
                )
            },
            deleteFlows() {
                this.$toast().confirm(
                    this.$t("flow delete", {"flowCount": this.queryBulkAction ? this.total : this.selection.length}),
                    () => {
                        if (this.queryBulkAction) {
                            return this.$store
                                .dispatch("flow/deleteFlowByQuery", this.loadQuery({
                                    namespace: this.$route.query.namespace ? [this.$route.query.namespace] : undefined,
                                    q: this.$route.query.q ? [this.$route.query.q] : undefined,
                                }, false))
                                .then(r => {
                                    this.$toast().success(this.$t("flows deleted", {count: r.data.count}));
                                    this.loadData(() => {
                                    })
                                })
                        } else {
                            return this.$store
                                .dispatch("flow/deleteFlowByIds", {ids: this.selection})
                                .then(r => {
                                    this.$toast().success(this.$t("flows deleted", {count: r.data.count}));
                                    this.loadData(() => {
                                    })
                                })
                        }
                    },
                    () => {
                    }
                )
            },
            importFlows() {
                const formData = new FormData();
                formData.append("fileUpload", this.$refs.file.files[0]);
                this.$store
                    .dispatch("flow/importFlows", formData)
                    .then(res => {
                        if (res.data.length > 0) {
                            this.$toast().warning(this.$t("flows not imported") + ": " + res.data.join(", "));
                        } else {
                            this.$toast().success(this.$t("flows imported"));
                        }
                        this.$refs.importForm.reset();
                        this.loadData(() => {
                        })
                    })
            },
            chartData(row) {
                if (this.dailyGroupByFlow && this.dailyGroupByFlow[row.namespace] && this.dailyGroupByFlow[row.namespace][row.id]) {
                    return this.dailyGroupByFlow[row.namespace][row.id];
                } else {
                    return [];
                }
            },
            getLastExecution(row) {
                let noState = {state: null, startDate: null}
                if (this.lastExecutions && this.lastExecutions.length > 0) {
                    let filteredFlowExec = this.lastExecutions.filter((executedFlow) => executedFlow.flowId == row.id && executedFlow.namespace == row.namespace)
                    if (filteredFlowExec.length > 0) {
                        return {
                            lastStatus: filteredFlowExec[0].state?.current,
                            startDate: filteredFlowExec[0].state?.startDate
                        }
                    }
                    return noState
                }
                else {
                    return noState
                }
            },
            loadQuery(base) {
                let queryFilter = this.queryWithFilter();

                if(this.namespace){
                    queryFilter.namespace = this.namespace
                }

                return _merge(base, queryFilter)
            },
            loadStats() {
                this.dailyReady = false;

                if (this.user.hasAny(permission.EXECUTION) && this.showStatChart) {
                    this.$store
                        .dispatch("stat/daily", this.loadQuery({
                            startDate: this.$moment(this.startDate).add(-1, "day").startOf("day").toISOString(true),
                            endDate: this.$moment(this.endDate).endOf("day").toISOString(true)
                        }))
                        .then(() => {
                            this.dailyReady = true;
                        });
                }
            },
            loadData(callback) {
                this.loadStats();

                this.$store
                    .dispatch("flow/findFlows", this.loadQuery({
                        size: parseInt(this.$route.query.size || 25),
                        page: parseInt(this.$route.query.page || 1),
                        sort: this.$route.query.sort || "id:asc"
                    }))
                    .then(flows => {
                        this.dailyGroupByFlowReady = false;
                        this.lastExecutionByFlowReady = false;

                        if (flows.results && flows.results.length > 0) {
                            if (this.user && this.user.hasAny(permission.EXECUTION)) {
                                this.$store
                                    .dispatch("stat/dailyGroupByFlow", {
                                        flows: flows.results
                                            .map(flow => {
                                                return {namespace: flow.namespace, id: flow.id}
                                            }),
                                        startDate: this.$moment(this.startDate).add(-1, "day").startOf("day").toISOString(true),
                                        endDate: this.$moment(this.endDate).endOf("day").toISOString(true)
                                    })
                                    .then(() => {
                                        this.dailyGroupByFlowReady = true
                                    })

                                this.$store
                                    .dispatch("stat/lastExecutions", {
                                        flows: flows.results
                                            .map(flow => {
                                                return {namespace: flow.namespace, id: flow.id}
                                            }),
                                    })
                                    .then(() => {
                                        this.lastExecutionByFlowReady = true
                                    })
                            }
                        }
                    })
                    .finally(callback);
            },
            rowClasses(row) {
                return row && row.row && row.row.disabled ? "disabled" : "";
            }
        }
    };
</script>

<style lang="scss" scoped>
    :deep(nav .dropdown-menu) {
        display: flex;
        width: 20rem;
    }

    .flow-id {
        min-width: 200px;
    }

    .flows-table  .el-table__cell {
        vertical-align: middle;
    }
</style>

<template>
    <top-nav-bar v-if="topbar" :title="routeInfo.title">
        <template #additional-right>
            <ul>
                <li>
                    <el-button :icon="Upload" @click="file?.click()">
                        {{ $t("import") }}
                    </el-button>
                    <input
                        ref="file"
                        type="file"
                        accept=".zip, .yml, .yaml"
                        @change="importFlows()"
                        class="d-none"
                    >
                </li>
                <li>
                    <router-link :to="{name: 'flows/search'}">
                        <el-button :icon="TextBoxSearch">
                            {{ $t("source search") }}
                        </el-button>
                    </router-link>
                </li>
                <li>
                    <router-link
                        :to="{
                            name: 'flows/create',
                            query: {namespace: $route.query.namespace},
                        }"
                        v-if="canCreate"
                    >
                        <el-button :icon="Plus" type="primary">
                            {{ $t("create") }}
                        </el-button>
                    </router-link>
                </li>
            </ul>
        </template>
    </top-nav-bar>
    <section
        data-component="FILENAME_PLACEHOLDER"
        :class="{container: topbar}"
        v-if="ready"
    >
        <div>
            <data-table
                @page-changed="onPageChanged"
                ref="dataTable"
                :total="total"
                :hide-top-pagination="!!namespace"
            >
                <template #navbar>
                    <KestraFilter
                        prefix="flows"
                        :language="FlowFilterLanguage"
                        :buttons="{
                            refresh: {shown: false},
                            settings: {shown: false}
                        }"
                        :properties="{
                            shown: true,
                            columns: optionalColumns,
                            displayColumns,
                            storageKey: 'flows',
                        }"
                        @update-properties="updateDisplayColumns"
                    />
                </template>

                <template #table>
                    <select-table
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
                        :no-data-text="$t('no_results.flows')"
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
                                <el-button
                                    v-if="canRead"
                                    :icon="Download"
                                    @click="exportFlows()"
                                >
                                    {{ $t("export") }}
                                </el-button>
                                <el-button
                                    v-if="canDelete"
                                    @click="deleteFlows"
                                    :icon="TrashCan"
                                >
                                    {{ $t("delete") }}
                                </el-button>
                                <el-button
                                    v-if="canUpdate && anyFlowDisabled()"
                                    @click="enableFlows"
                                    :icon="FileDocumentCheckOutline"
                                >
                                    {{ $t("enable") }}
                                </el-button>
                                <el-button
                                    v-if="canUpdate && anyFlowEnabled()"
                                    @click="disableFlows"
                                    :icon="FileDocumentRemoveOutline"
                                >
                                    {{ $t("disable") }}
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
                                            :to="{
                                                name: 'flows/update',
                                                params: {
                                                    namespace:
                                                        scope.row.namespace,
                                                    id: scope.row.id,
                                                },
                                            }"
                                            class="me-1"
                                        >
                                            {{
                                                $filters.invisibleSpace(
                                                    scope.row.id,
                                                )
                                            }}
                                        </router-link>
                                        <markdown-tooltip
                                            :id="
                                                scope.row.namespace +
                                                    '-' +
                                                    scope.row.id
                                            "
                                            :description="scope.row.description"
                                            :title="
                                                scope.row.namespace +
                                                    '.' +
                                                    scope.row.id
                                            "
                                        />
                                    </div>
                                </template>
                            </el-table-column>

                            <el-table-column
                                v-if="displayColumn('labels')"
                                :label="$t('labels')"
                            >
                                <template #default="scope">
                                    <labels :labels="scope.row.labels" />
                                </template>
                            </el-table-column>

                            <el-table-column
                                prop="namespace"
                                v-if="displayColumn('namespace')"
                                sortable="custom"
                                :sort-orders="['ascending', 'descending']"
                                :label="$t('namespace')"
                                :formatter="
                                    (_, __, cellValue) =>
                                        $filters.invisibleSpace(cellValue)
                                "
                            />

                            <el-table-column
                                prop="state.startDate"
                                v-if="
                                    displayColumn('state.startDate') &&
                                        user.hasAny(permission.EXECUTION)
                                "
                                :label="$t('last execution date')"
                            >
                                <template #default="scope">
                                    <date-ago
                                        v-if="lastExecutionByFlowReady"
                                        :inverted="true"
                                        :date="
                                            getLastExecution(scope.row)
                                                ?.startDate
                                        "
                                    />
                                </template>
                            </el-table-column>

                            <el-table-column
                                prop="state.current"
                                v-if="
                                    displayColumn('state.current') &&
                                        user.hasAny(permission.EXECUTION)
                                "
                                :label="$t('last execution status')"
                            >
                                <template #default="scope">
                                    <div
                                        v-if="lastExecutionByFlowReady && getLastExecution(scope.row)?.status"
                                        class="d-flex justify-content-between align-items-center"
                                    >
                                        <Status :status="getLastExecution(scope.row)?.status" size="small" />
                                        <div class="height: 100px;">
                                            <Bar :chart="mappedChart(scope.row.id, scope.row.namespace)" show-default short />
                                        </div>
                                    </div>
                                </template>
                            </el-table-column>

                            <el-table-column
                                v-if="displayColumn('triggers')"
                                :label="$t('triggers')"
                                class-name="row-action"
                            >
                                <template #default="scope">
                                    <trigger-avatar :flow="scope.row" />
                                </template>
                            </el-table-column>

                            <el-table-column
                                column-key="action"
                                class-name="row-action"
                                :label="$t('actions')"
                            >
                                <template #default="scope">
                                    <router-link
                                        :to="{
                                            name: 'flows/update',
                                            params: {
                                                namespace: scope.row.namespace,
                                                id: scope.row.id,
                                            },
                                        }"
                                    >
                                        <kicon
                                            :tooltip="$t('details')"
                                            placement="left"
                                        >
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
    import {ref} from "vue";
    import BulkSelect from "../layout/BulkSelect.vue";
    import SelectTable from "../layout/SelectTable.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import TextBoxSearch from "vue-material-design-icons/TextBoxSearch.vue";
    import Download from "vue-material-design-icons/Download.vue";
    import TrashCan from "vue-material-design-icons/TrashCan.vue";
    import FileDocumentRemoveOutline from "vue-material-design-icons/FileDocumentRemoveOutline.vue";
    import FileDocumentCheckOutline from "vue-material-design-icons/FileDocumentCheckOutline.vue";
    import Upload from "vue-material-design-icons/Upload.vue";
    import KestraFilter from "../filter/KestraFilter.vue";
    import FlowFilterLanguage from "../../composables/monaco/languages/filters/impl/flowFilterLanguage.ts";

    import Bar from "../dashboard/sections/Bar.vue";

    const file = ref(null);
</script>

<script>
    import {mapState} from "vuex";
    import {mapStores} from "pinia";
    import {useExecutionsStore} from "../../stores/executions";
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
    import MarkdownTooltip from "../layout/MarkdownTooltip.vue";
    import Kicon from "../Kicon.vue";
    import Labels from "../layout/Labels.vue";
    import {storageKeys} from "../../utils/constants";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import YAML_CHART from "../dashboard/assets/executions_timeseries_chart.yaml?raw";

    const CHART_DEFINITION = {
        id: "executions_per_namespace_bars",
        type: "io.kestra.plugin.core.dashboard.chart.Bar",
        chartOptions: {
            displayName: "Executions (per namespace)",
            legend: {enabled: false},
            column: "total",
            width: 12,
        },
        data: {
            type: "io.kestra.plugin.core.dashboard.data.Executions",
            columns: {
                date: {field: "START_DATE", displayName: "Date"},
                state: {field: "STATE"},
                total: {displayName: "Executions", agg: "COUNT"},
            },
            where: [
                {
                    field: "NAMESPACE",
                    type: "EQUAL_TO",
                    value: "${namespace}",
                },
                {
                    field: "FLOW_ID",
                    type: "EQUAL_TO",
                    value: "${flow_id}",
                }
            ]
        },
    };

    CHART_DEFINITION.content = YAML_UTILS.stringify(CHART_DEFINITION);

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
                default: true,
            },
            namespace: {
                type: String,
                required: false,
                default: undefined,
            },
            id: {
                type: String,
                required: false,
                default: null,
            },
        },
        data() {
            return {
                optionalColumns: [
                    {
                        label: this.$t("labels"),
                        prop: "labels",
                        default: true,
                    },
                    {
                        label: this.$t("namespace"),
                        prop: "namespace",
                        default: true,
                    },
                    {
                        label: this.$t("last execution date"),
                        prop: "state.startDate",
                        default: true,
                    },
                    {
                        label: this.$t("last execution status"),
                        prop: "state.current",
                        default: true,
                    },
                    {
                        label: this.$t("execution statistics"),
                        prop: "state",
                        default: true,
                    },
                    {
                        label: this.$t("triggers"),
                        prop: "triggers",
                        default: true,
                    },
                ],
                displayColumns: [],
                isDefaultNamespaceAllow: true,
                permission: permission,
                action: action,
                file: undefined,
                loading: false,
                lastExecutionByFlowReady: false,
                latestExecutions: []
            };
        },
        computed: {
            ...mapState("flow", ["flows", "total"]),
            ...mapState("auth", ["user"]),
            ...mapStores(useExecutionsStore),
            routeInfo() {
                return {
                    title: this.$t("flows"),
                };
            },
            canCheck() {
                return this.canRead || this.canDelete || this.canUpdate;
            },
            canCreate() {
                return (
                    this.user &&
                    this.user.hasAnyActionOnAnyNamespace(
                        permission.FLOW,
                        action.CREATE,
                    )
                );
            },
            canRead() {
                return (
                    this.user &&
                    this.user.isAllowed(
                        permission.FLOW,
                        action.READ,
                        this.$route.query.namespace,
                    )
                );
            },
            canDelete() {
                return (
                    this.user &&
                    this.user.isAllowed(
                        permission.FLOW,
                        action.DELETE,
                        this.$route.query.namespace,
                    )
                );
            },
            canUpdate() {
                return (
                    this.user &&
                    this.user.isAllowed(
                        permission.FLOW,
                        action.UPDATE,
                        this.$route.query.namespace,
                    )
                );
            },
            charts() {
                return [
                    {...YAML_UTILS.parse(YAML_CHART), content: YAML_CHART}
                ];
            }
        },
        beforeRouteEnter(to, _, next) {
            const defaultNamespace = localStorage.getItem(
                storageKeys.DEFAULT_NAMESPACE,
            );
            const query = {...to.query};
            let queryHasChanged = false;
            const queryKeys = Object.keys(query);
            if (defaultNamespace && !queryKeys.some(key => key.startsWith("filters[namespace]"))) {
                query["filters[namespace][PREFIX]"] = defaultNamespace;
                queryHasChanged = true;
            }

            if (!queryKeys.some(key => key.startsWith("filters[scope]"))) {
                query["filters[scope][EQUALS]"] = "USER";
                queryHasChanged = true;
            }

            if (queryHasChanged) {
                next({
                    ...to,
                    query,
                    replace: true
                });
            } else {
                next();
            }
        },
        created() {
            this.displayColumns = this.loadDisplayColumns();
        },
        methods: {
            selectionMapper(element) {
                return {
                    id: element.id,
                    namespace: element.namespace,
                    enabled: !element.disabled,
                };
            },
            loadDisplayColumns() {
                const storedColumns = localStorage.getItem("columns_flows");
                if (storedColumns) {
                    return storedColumns.split(",");
                }
                return this.optionalColumns
                    .filter((col) => {
                        return col.default && (!col.condition || col.condition());
                    })
                    .map((col) => col.prop);
            },
            displayColumn(column) {
                return this.displayColumns.includes(column);
            },
            updateDisplayColumns(newColumns) {
                this.displayColumns = newColumns;
            },
            exportFlows() {
                this.$toast().confirm(
                    this.$t("flow export", {
                        flowCount: this.queryBulkAction
                            ? this.total
                            : this.selection.length,
                    }),
                    () => {
                        const flowCount = this.queryBulkAction
                            ? this.total
                            : this.selection.length;

                        if (this.queryBulkAction) {
                            return this.$store
                                .dispatch(
                                    "flow/exportFlowByQuery",
                                    this.loadQuery(),
                                )
                                .then(() => {
                                    this.$toast().success(
                                        this.$t("flows exported", {
                                            count: flowCount,
                                        }),
                                    );
                                });
                        } else {
                            return this.$store
                                .dispatch("flow/exportFlowByIds", {
                                    ids: this.selection,
                                })
                                .then(() => {
                                    this.$toast().success(
                                        this.$t("flows exported", {
                                            count: flowCount,
                                        }),
                                    );
                                });
                        }
                    },
                    () => {},
                );
            },
            disableFlows() {
                this.$toast().confirm(
                    this.$t("flow disable", {
                        flowCount: this.queryBulkAction
                            ? this.total
                            : this.selection.length,
                    }),
                    () => {
                        if (this.queryBulkAction) {
                            return this.$store
                                .dispatch(
                                    "flow/disableFlowByQuery",
                                    this.loadQuery(),
                                )
                                .then((r) => {
                                    this.$toast().success(
                                        this.$t("flows disabled", {
                                            count: r.data.count,
                                        }),
                                    );
                                    this.loadData(() => {});
                                });
                        } else {
                            return this.$store
                                .dispatch("flow/disableFlowByIds", {
                                    ids: this.selection,
                                })
                                .then((r) => {
                                    this.$toast().success(
                                        this.$t("flows disabled", {
                                            count: r.data.count,
                                        }),
                                    );
                                    this.loadData(() => {});
                                });
                        }
                    },
                    () => {},
                );
            },
            anyFlowDisabled() {
                return this.selection.some((flow) => !flow.enabled);
            },
            anyFlowEnabled() {
                return this.selection.some((flow) => flow.enabled);
            },
            enableFlows() {
                this.$toast().confirm(
                    this.$t("flow enable", {
                        flowCount: this.queryBulkAction
                            ? this.total
                            : this.selection.length,
                    }),
                    () => {
                        if (this.queryBulkAction) {
                            return this.$store
                                .dispatch(
                                    "flow/enableFlowByQuery",
                                    this.loadQuery(),
                                )
                                .then((r) => {
                                    this.$toast().success(
                                        this.$t("flows enabled", {
                                            count: r.data.count,
                                        }),
                                    );
                                    this.loadData(() => {});
                                });
                        } else {
                            return this.$store
                                .dispatch("flow/enableFlowByIds", {
                                    ids: this.selection,
                                })
                                .then((r) => {
                                    this.$toast().success(
                                        this.$t("flows enabled", {
                                            count: r.data.count,
                                        }),
                                    );
                                    this.loadData(() => {});
                                });
                        }
                    },
                    () => {},
                );
            },
            deleteFlows() {
                this.$toast().confirm(
                    this.$t("flow delete", {
                        flowCount: this.queryBulkAction
                            ? this.total
                            : this.selection.length,
                    }),
                    () => {
                        if (this.queryBulkAction) {
                            return this.$store
                                .dispatch(
                                    "flow/deleteFlowByQuery",
                                    this.loadQuery(),
                                )
                                .then((r) => {
                                    this.$toast().success(
                                        this.$t("flows deleted", {
                                            count: r.data.count,
                                        }),
                                    );
                                    this.loadData(() => {});
                                });
                        } else {
                            return this.$store
                                .dispatch("flow/deleteFlowByIds", {
                                    ids: this.selection,
                                })
                                .then((r) => {
                                    this.$toast().success(
                                        this.$t("flows deleted", {
                                            count: r.data.count,
                                        }),
                                    );
                                    this.loadData(() => {});
                                });
                        }
                    },
                    () => {},
                );
            },
            importFlows() {
                const formData = new FormData();
                formData.append("fileUpload", this.$refs.file.files[0]);
                this.$store.dispatch("flow/importFlows", formData)
                    .then((res) => {
                        if (res.data.length > 0) {
                            this.$toast().warning(
                                this.$t("flows not imported") +
                                    ": " +
                                    res.data.join(", "),
                            );
                        } else {
                            this.$toast().success(this.$t("flows imported"));
                        }
                        this.$refs.file.value = "";
                        this.loadData(() => {});
                    });
            },
            getLastExecution(row) {
                if (!this.latestExecutions || !row) return null;
                return this.latestExecutions.find(
                    e => e.flowId === row.id && e.namespace === row.namespace
                ) ?? null;
            },
            loadQuery(base) {
                let queryFilter = this.queryWithFilter(
                    undefined,
                    []
                );

                if (this.namespace) {
                    queryFilter["filters[namespace][PREFIX]"] = this.$route.params.id || this.namespace;
                }

                return _merge(base, queryFilter);
            },
            loadData(callback) {
                const q = this.$route.query;

                this.$store
                    .dispatch(
                        "flow/findFlows",
                        this.loadQuery({
                            size: parseInt(this.namespace ? this.internalPageSize : q.size ?? 25),
                            page: parseInt(this.namespace ? this.internalPageNumber : q.page ?? 1),
                            sort: q.sort ?? "id:asc",
                        }),
                    )
                    .then(data => {
                        if(this.user.hasAnyActionOnAnyNamespace(
                            permission.EXECUTION,
                            action.READ,
                        )) {
                            this.executionsStore.loadLatestExecutions(
                                {
                                    flowFilters: data.results.map(flow => {
                                        return {
                                            id: flow.id,
                                            namespace: flow.namespace
                                        };
                                    })
                                }
                            ).then(latestExecs => {
                                this.latestExecutions = latestExecs;
                                this.lastExecutionByFlowReady = true;
                            });
                        }
                    })
                    .finally(callback);
            },
            rowClasses(row) {
                return row && row.row && row.row.disabled ? "disabled" : "";
            },
            mappedChart(id, namespace) {
                let MAPPED_CHARTS = JSON.parse(JSON.stringify(CHART_DEFINITION));
                
                MAPPED_CHARTS.content = MAPPED_CHARTS.content.replace("${namespace}", namespace).replace("${flow_id}", id);

                return MAPPED_CHARTS;
            }
        }
    };
</script>

<style lang="scss" scoped>
.shadow {
    box-shadow: 0px 2px 4px 0px var(--ks-card-shadow) !important;
}

:deep(nav .dropdown-menu) {
    display: flex;
    width: 20rem;
}

.flow-id {
    min-width: 200px;
}

.flows-table .el-table__cell {
    vertical-align: middle;
}
</style>

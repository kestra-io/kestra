<template>
    <div v-if="ready">
        <div>
            <data-table
                @page-changed="onPageChanged"
                ref="dataTable"
                :total="total"
            >
                <template #navbar>
                    <el-form-item>
                        <search-field />
                    </el-form-item>
                    <el-form-item>
                        <namespace-select
                            data-type="flow"
                            :value="$route.query.namespace"
                            @update:model-value="onDataTableValue('namespace', $event)"
                        />
                    </el-form-item>
                </template>

                <template #top>
                    <state-global-chart
                        class="mb-4"
                        v-if="daily"
                        :ready="dailyReady"
                        :data="daily"
                    />
                </template>

                <template #table>
                    <el-table
                        :data="flows"
                        ref="table"
                        :default-sort="{prop: 'id', order: 'ascending'}"
                        stripe
                        table-layout="auto"
                        fixed
                        @row-dblclick="onRowDoubleClick"
                        @sort-change="onSort"
                        :row-class-name="rowClasses"
                        @selection-change="handleSelectionChange"
                    >
                        <el-table-column type="selection" v-if="(canRead)" />
                        <el-table-column prop="id" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('id')">
                            <template #default="scope">
                                <router-link
                                    :to="{name: 'flows/update', params: {namespace: scope.row.namespace, id: scope.row.id}}"
                                >
                                    {{ scope.row.id }}
                                </router-link>
                                &nbsp;<markdown-tooltip
                                    :id="scope.row.namespace + '-' + scope.row.id"
                                    :description="scope.row.description"
                                    :title="scope.row.namespace + '.' + scope.row.id"
                                />
                            </template>
                        </el-table-column>

                        <el-table-column :label="$t('labels')">
                            <template #default="scope">
                                <labels :labels="scope.row.labels" />
                            </template>
                        </el-table-column>

                        <el-table-column prop="namespace" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('namespace')" />

                        <el-table-column
                            prop="state"
                            :label="$t('execution statistics')"
                            v-if="user.hasAny(permission.EXECUTION)"
                            class-name="row-graph"
                        >
                            <template #default="scope">
                                <state-chart
                                    :duration="true"
                                    :namespace="scope.row.namespace"
                                    :flow-id="scope.row.id"
                                    v-if="dailyGroupByFlowReady"
                                    :data="chartData(scope.row)"
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
                                <router-link :to="{name: 'flows/update', params : {namespace: scope.row.namespace, id: scope.row.id}}">
                                    <kicon :tooltip="$t('details')" placement="left">
                                        <eye />
                                    </kicon>
                                </router-link>
                            </template>
                        </el-table-column>
                    </el-table>
                </template>
            </data-table>
        </div>

        <bottom-line>
            <ul>
                <ul v-if="flowsSelection.length !== 0 && canRead">
                    <bottom-line-counter v-model="queryBulkAction" :selections="flowsSelection" :total="total" @update:model-value="selectAll()">
                        <el-button v-if="canRead" :icon="Download" size="large" @click="exportFlows()">
                            {{ $t('export') }}
                        </el-button>
                        <el-button v-if="canDelete" @click="deleteFlows" size="large" :icon="TrashCan">
                            {{ $t('delete') }}
                        </el-button>
                        <el-button v-if="canDisable" @click="disableFlows" size="large" :icon="FileDocumentRemoveOutline">
                            {{ $t('disable') }}
                        </el-button>
                    </bottom-line-counter>
                </ul>
                <li class="spacer" />
                <li>
                    <div class="el-input el-input-file el-input--large custom-upload">
                        <div class="el-input__wrapper">
                            <label for="importFlows">
                                <Upload />
                                {{ $t('import') }}
                            </label>
                            <input
                                id="importFlows"
                                class="el-input__inner"
                                type="file"
                                @change="importFlows()"
                                ref="file"
                            >
                        </div>
                    </div>
                </li>
                <li>
                    <router-link :to="{name: 'flows/search'}">
                        <el-button :icon="TextBoxSearch" size="large">
                            {{ $t('source search') }}
                        </el-button>
                    </router-link>
                </li>
                <li v-if="user && user.hasAnyAction(permission.FLOW, action.CREATE)">
                    <router-link :to="{name: 'flows/create'}">
                        <el-button :icon="Plus" type="info" size="large">
                            {{ $t('create') }}
                        </el-button>
                    </router-link>
                </li>
            </ul>
        </bottom-line>
    </div>
</template>

<script setup>
    import Plus from "vue-material-design-icons/Plus.vue";
    import TextBoxSearch from "vue-material-design-icons/TextBoxSearch.vue";
    import Download from "vue-material-design-icons/Download.vue";
    import TrashCan from "vue-material-design-icons/TrashCan.vue";
    import FileDocumentRemoveOutline from "vue-material-design-icons/FileDocumentRemoveOutline.vue";
</script>

<script>
    import {mapState} from "vuex";
    import _merge from "lodash/merge";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import NamespaceSelect from "../namespace/NamespaceSelect.vue";
    import Eye from "vue-material-design-icons/Eye.vue";
    import BottomLine from "../layout/BottomLine.vue";
    import RouteContext from "../../mixins/routeContext";
    import DataTableActions from "../../mixins/dataTableActions";
    import RestoreUrl from "../../mixins/restoreUrl";
    import DataTable from "../layout/DataTable.vue";
    import SearchField from "../layout/SearchField.vue";
    import StateChart from "../stats/StateChart.vue";
    import StateGlobalChart from "../stats/StateGlobalChart.vue";
    import TriggerAvatar from "./TriggerAvatar.vue";
    import MarkdownTooltip from "../layout/MarkdownTooltip.vue"
    import Kicon from "../Kicon.vue"
    import Labels from "../layout/Labels.vue"
    import BottomLineCounter from "../layout/BottomLineCounter.vue";
    import Upload from "vue-material-design-icons/Upload.vue";
    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions],
        components: {
            NamespaceSelect,
            BottomLine,
            Eye,
            DataTable,
            SearchField,
            StateChart,
            StateGlobalChart,
            TriggerAvatar,
            MarkdownTooltip,
            Kicon,
            Labels,
            BottomLineCounter,
            Upload
        },
        data() {
            return {
                isDefaultNamespaceAllow: true,
                permission: permission,
                action: action,
                dailyGroupByFlowReady: false,
                dailyReady: false,
                flowsSelection: [],
                queryBulkAction: false,
                file: undefined,
            };
        },
        computed: {
            ...mapState("flow", ["flows", "total"]),
            ...mapState("stat", ["dailyGroupByFlow", "daily"]),
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
            canRead() {
                return this.user && this.user.isAllowed(permission.FLOW, action.READ);
            },
            canDelete() {
                return this.user && this.user.isAllowed(permission.FLOW, action.DELETE);
            },
            canDisable() {
                return this.user && this.user.isAllowed(permission.FLOW, action.UPDATE);
            },
        },
        methods: {
            handleSelectionChange(val) {
                if (val.length === 0) {
                    this.queryBulkAction = false
                }
                this.flowsSelection = val.map(x => {
                    return {
                        id: x.id,
                        namespace: x.namespace
                    }
                });
            },
            selectAll() {
                if (this.$refs.table.getSelectionRows().length !== this.$refs.table.data.length) {
                    this.$refs.table.toggleAllSelection();
                }
            },
            exportFlows() {
                this.$toast().confirm(
                    this.$t("flow export", {"flowCount": this.queryBulkAction ? this.total : this.flowsSelection.length}),
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
                                .dispatch("flow/exportFlowByIds", {ids: this.flowsSelection})
                                .then(_ => {
                                    this.$toast().success(this.$t("flows exported"));
                                })
                        }
                    },
                    () => {}
                )
            },
            disableFlows(){
                this.$toast().confirm(
                    this.$t("flow disable", {"flowCount": this.queryBulkAction ? this.total : this.flowsSelection.length}),
                    () => {
                        if (this.queryBulkAction) {
                            return this.$store
                                .dispatch("flow/disableFlowByQuery", this.loadQuery({
                                    namespace: this.$route.query.namespace ? [this.$route.query.namespace] : undefined,
                                    q: this.$route.query.q ? [this.$route.query.q] : undefined,
                                }, false))
                                .then(r => {
                                    this.$toast().success(this.$t("flows disabled", {count: r.data.count}));
                                    this.loadData(() => {})
                                })
                        } else {
                            return this.$store
                                .dispatch("flow/disableFlowByIds", {ids: this.flowsSelection})
                                .then(r => {
                                    this.$toast().success(this.$t("flows disabled", {count: r.data.count}));
                                    this.loadData(() => {})
                                })
                        }
                    },
                    () => {}
                )
            },
            deleteFlows(){
                this.$toast().confirm(
                    this.$t("flow delete", {"flowCount": this.queryBulkAction ? this.total : this.flowsSelection.length}),
                    () => {
                        if (this.queryBulkAction) {
                            return this.$store
                                .dispatch("flow/deleteFlowByQuery", this.loadQuery({
                                    namespace: this.$route.query.namespace ? [this.$route.query.namespace] : undefined,
                                    q: this.$route.query.q ? [this.$route.query.q] : undefined,
                                }, false))
                                .then(r => {
                                    this.$toast().success(this.$t("flows deleted", {count: r.data.count}));
                                    this.loadData(() => {})
                                })
                        } else {
                            return this.$store
                                .dispatch("flow/deleteFlowByIds", {ids: this.flowsSelection})
                                .then(r => {
                                    this.$toast().success(this.$t("flows deleted", {count: r.data.count}));
                                    this.loadData(() => {})
                                })
                        }
                    },
                    () => {}
                )
            },
            importFlows() {
                const formData = new FormData();
                formData.append("fileUpload", this.$refs.file.files[0]);
                this.$store
                    .dispatch("flow/importFlows", formData)
                    .then(_ => {
                        this.$toast().success(this.$t("flows imported"));
                        this.loadData(() => {})
                    })
            },
            chartData(row) {
                if (this.dailyGroupByFlow && this.dailyGroupByFlow[row.namespace] && this.dailyGroupByFlow[row.namespace][row.id]) {
                    return this.dailyGroupByFlow[row.namespace][row.id];
                } else {
                    return [];
                }
            },
            loadQuery(base) {
                let queryFilter = this.queryWithFilter();

                return _merge(base, queryFilter)
            },
            loadData(callback) {
                this.dailyReady = false;

                if (this.user.hasAny(permission.EXECUTION)) {
                    this.$store
                        .dispatch("stat/daily", this.loadQuery({
                            startDate: this.$moment(this.startDate).add(-1, "day").startOf("day").toISOString(true),
                            endDate: this.$moment(this.endDate).endOf("day").toISOString(true)
                        }))
                        .then(() => {
                            this.dailyReady = true;
                        });
                }

                this.$store
                    .dispatch("flow/findFlows", this.loadQuery({
                        size: parseInt(this.$route.query.size || 25),
                        page: parseInt(this.$route.query.page || 1),
                        sort: this.$route.query.sort || "id:asc"
                    }))
                    .then(flows => {
                        this.dailyGroupByFlowReady = false;
                        callback();

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
                            }
                        }
                    })
            },
            rowClasses(row) {
                return row && row.row && row.row.disabled ? "disabled" : "";
            }
        }
    };
</script>


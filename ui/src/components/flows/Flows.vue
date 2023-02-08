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
                    >
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
                <li>
                    <router-link :to="{name: 'flows/search'}">
                        <el-button :icon="TextBoxSearch">
                            {{ $t('source search') }}
                        </el-button>
                    </router-link>
                </li>

                <li v-if="user && user.hasAnyAction(permission.FLOW, action.CREATE)">
                    <router-link :to="{name: 'flows/create'}">
                        <el-button :icon="Plus" type="primary">
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
        },
        data() {
            return {
                isDefaultNamespaceAllow: true,
                permission: permission,
                action: action,
                dailyGroupByFlowReady: false,
                dailyReady: false,
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
            }
        },
        methods: {
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


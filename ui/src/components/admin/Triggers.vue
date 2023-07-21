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
                    <el-form-item>
                        <refresh-button class="float-right" @refresh="load(onDataLoaded)" />
                    </el-form-item>
                </template>
                <template #table>

                    <el-table
                        :data="triggers"
                        ref="table"
                        :default-sort="{prop: 'flowId', order: 'ascending'}"
                        stripe
                        table-layout="auto"
                        fixed
                        @sort-change="onSort"
                    >
                        <el-table-column prop="triggerId" sortable="custom" :sort-orders="['ascending', 'descending']"
                                         :label="$t('id')" />
                        <el-table-column sortable="custom" :sort-orders="['ascending', 'descending']"
                                         :label="$t('flow')">
                            <template #default="scope">
                                <router-link
                                    :to="{name: 'flows/update', params: {namespace: scope.row.namespace, id: scope.row.flowId}}"
                                >
                                    {{ $filters.invisibleSpace(scope.row.flowId) }}
                                </router-link>
                                <markdown-tooltip
                                    :id="scope.row.namespace + '-' + scope.row.flowId"
                                    :description="scope.row.description"
                                    :title="scope.row.namespace + '.' + scope.row.flowId"
                                />
                            </template>
                        </el-table-column>
                        <el-table-column sortable="custom" :sort-orders="['ascending', 'descending']"
                                         :label="$t('namespace')">
                            <template #default="scope">
                                {{ $filters.invisibleSpace(scope.row.namespace) }}
                            </template>
                        </el-table-column>

                        <el-table-column :label="$t('current execution')">
                            <template #default="scope">
                                <router-link
                                    v-if="scope.row.executionId"
                                    :to="{name: 'executions/update', params: {namespace: scope.row.namespace, flowId: scope.row.flowId, id: scope.row.executionId}}"
                                >
                                    {{ scope.row.executionId }}
                                </router-link>
                            </template>
                        </el-table-column>

                        <el-table-column prop="executionCurrentState" :label="$t('state')" />
                        <el-table-column sortable="custom" :sort-orders="['ascending', 'descending']"
                                         :label="$t('date')">
                            <template #default="scope">
                                {{ scope.row.date ? $filters.date(scope.row.date, "iso") : "" }}
                            </template>
                        </el-table-column>
                        <el-table-column sortable="custom" :sort-orders="['ascending', 'descending']"
                                         :label="$t('updated date')">
                            <template #default="scope">
                                {{ scope.row.updatedDate ? $filters.date(scope.row.updatedDate, "iso") : "" }}
                            </template>
                        </el-table-column>
                        <el-table-column sortable="custom" :sort-orders="['ascending', 'descending']"
                                         :label="$t('evaluation lock date')">
                            <template #default="scope">
                                {{ scope.row.evaluateRunningDate ? $filters.date(scope.row.evaluateRunningDate, "iso") : "" }}
                            </template>
                        </el-table-column>
                    </el-table>
                </template>
            </data-table>
        </div>
    </div>
</template>

<script>
    import NamespaceSelect from "../namespace/NamespaceSelect.vue";
    import RouteContext from "../../mixins/routeContext";
    import RestoreUrl from "../../mixins/restoreUrl";
    import SearchField from "../layout/SearchField.vue";
    import DataTable from "../layout/DataTable.vue";
    import DataTableActions from "../../mixins/dataTableActions";
    import MarkdownTooltip from "../layout/MarkdownTooltip.vue";
    import RefreshButton from "../layout/RefreshButton.vue";

    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions],
        components: {
            RefreshButton,
            MarkdownTooltip,
            DataTable,
            SearchField,
            NamespaceSelect
        },
        data() {
            return {
                triggers: undefined,
                total: undefined
            };
        },
        methods: {
            loadData(callback) {
                this.$store.dispatch("trigger/search", {
                    namespace: this.$route.query.namespace,
                    q: this.$route.query.q,
                    size: parseInt(this.$route.query.size || 25),
                    page: parseInt(this.$route.query.page || 1),
                    sort: this.$route.query.sort || "triggerId:asc"
                }).then(triggersData => {
                    this.triggers = triggersData.results;
                    this.total = triggersData.total;
                    callback();
                });
            }
        }
    };
</script>


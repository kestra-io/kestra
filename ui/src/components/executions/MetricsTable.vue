<template>
    <data-table
        @page-changed="onPageChanged"
        ref="dataTable"
        :total="metricsTotal"
        :embed="true"
    >
        <template #navbar>
            <slot name="navbar" />
        </template>
        <template #table>
            <el-table
                :data="metrics"
                :default-sort="{prop: 'name', order: 'ascending'}"
                table-layout="auto"
                fixed
                @sort-change="onSort"
            >
                <el-table-column v-if="showTask" prop="taskId" sortable :label="$t('task')">
                    <template #default="scope">
                        <p>{{ scope.row.taskId }}</p>
                    </template>
                </el-table-column>

                <el-table-column prop="name" sortable :label="$t('name')">
                    <template #default="scope">
                        <template v-if="scope.row.type === 'timer'">
                            <kicon><timer /></kicon>
                        </template>
                        <template v-else>
                            <kicon><counter /></kicon>
                        </template>
                        &nbsp;<code>{{ scope.row.name }}</code>
                    </template>
                </el-table-column>

                <el-table-column prop="value" sortable :label="$t('value')">
                    <template #default="scope">
                        <span v-if="scope.row.type === 'timer'">
                            {{ $filters.humanizeDuration(scope.row.value / 1000) }}
                        </span>
                        <span v-else>
                            {{ $filters.humanizeNumber(scope.row.value) }}
                        </span>
                    </template>
                </el-table-column>


                <el-table-column prop="tags" :label="$t('tags')">
                    <template #default="scope">
                        <el-tag
                            v-for="(value, key) in scope.row.tags"
                            :key="key"
                            class="me-1"
                            type="info"
                            size="small"
                            disable-transitions
                        >
                            {{ key }}: <strong>{{ value }}</strong>
                        </el-tag>
                    </template>
                </el-table-column>
            </el-table>
        </template>
    </data-table>
</template>

<script>
    import Kicon from "../Kicon.vue";
    import Timer from "vue-material-design-icons/Timer.vue";
    import Counter from "vue-material-design-icons/Numeric.vue";
    import DataTableActions from "../../mixins/dataTableActions";
    import DataTable from "../layout/DataTable.vue";

    export default {
        mixins: [DataTableActions],
        components: {
            Kicon,
            Timer,
            Counter,
            DataTable,
        },
        data() {
            return {
                loadInit: false,
                metrics: undefined,
                metricsTotal: 0
            };
        },
        props: {
            embed: {
                type: Boolean,
                default: true
            },
            taskRunId: {
                type: String,
                default: undefined
            },
            showTask: {
                type: Boolean,
                default: false
            },
            execution: {
                type: Object,
                required: true
            }
        },
        watch: {
            taskRunId() {
                this.loadData(this.onDataLoaded);
            }
        },
        methods: {
            loadData(callback) {
                let params = {};

                if (this.taskRunId) {
                    params.taskRunId = this.taskRunId;
                }

                if (this.internalPageNumber) {
                    params.page = this.internalPageNumber;
                }

                if (this.internalPageSize) {
                    params.size = this.internalPageSize;
                }

                if (this.internalSort) {
                    params.sort = this.internalSort;
                } else {
                    params.sort = "name:asc";
                }

                this.$store.dispatch("execution/loadMetrics", {
                    executionId: this.execution.id,
                    params: params,
                    store: false
                }).then(metrics => {
                    this.metrics = metrics.results;
                    this.metricsTotal = metrics.total;
                    callback();
                })
            },
        },
    };
</script>

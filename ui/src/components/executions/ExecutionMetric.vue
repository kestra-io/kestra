<template>
    <data-table
        v-if="execution"
        @page-changed="onPageChanged"
        ref="dataTable"
        :total="total"
    >
        <template #navbar>
            <el-form-item>
                <el-select
                    filterable
                    clearable
                    :persistent="false"
                    v-model="filter"
                    :placeholder="$t('display metric for specific task') + '...'"
                >
                    <el-option
                        v-for="item in selectOptions"
                        :key="item.value"
                        :label="item.label"
                        :value="item.value"
                    />
                </el-select>
            </el-form-item>
        </template>
        <template #table>
            <el-table
                :data="metricsData"
                ref="table"
                :default-sort="{prop: 'name', order: 'ascending'}"
                stripe
                table-layout="auto"
                fixed
            >
                <el-table-column prop="task" sortable :label="$t('task')">
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

                <el-table-column prop="tags" sortable :label="$t('tags')">
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
            </el-table>
        </template>
    </data-table>
</template>
<script>
    import {mapState} from "vuex";
    import Kicon from "../Kicon.vue";
    import Timer from "vue-material-design-icons/Timer.vue";
    import Counter from "vue-material-design-icons/Numeric.vue";
    import DataTable from "../layout/DataTable.vue";
    import DataTableActions from "../../mixins/dataTableActions";

    export default {
        mixins: [DataTableActions],
        components: {
            Kicon,
            Timer,
            Counter,
            DataTable
        },
        data() {
            return {
                filter: undefined,
                debugExpression: "",
                isJson: false,
                debugError: "",
                debugStackTrace: "",
                isModalOpen: false
            };
        },
        mounted() {
            this.loadData();
        },
        created() {
            if (this.$route.query.search) {
                this.filter = this.$route.query.search || ""
            }
        },
        methods: {
            loadData(callback) {
                const params = {
                    size: parseInt(this.$route.query.size || 25),
                    page: parseInt(this.$route.query.page || 1)
                }
                this.$store.dispatch("execution/loadMetrics", {
                    executionId: this.$route.params.id,
                    params: params
                }).then(metrics => callback())
            },
        },
        computed: {
            ...mapState("execution", ["execution","metrics"]),
            selectOptions() {
                const options = {};
                for (const taskRun of this.execution.taskRunList || []) {
                    options[taskRun.id] = {
                        label: taskRun.taskId + (taskRun.value ? ` - ${taskRun.value}`: ""),
                        value: taskRun.id
                    }
                }

                return Object.values(options);
            },
            metricsData(){
                if(this.filter){
                    return this.metrics.filter(metric => metric.taskRunId === this.filter)
                }
                return this.metrics
            },
            total(){
                return this.metrics.length
            }
        }
    };
</script>

<template>
    <metrics-table ref="table" :task-run-id="taskRunId" :show-task="true" :execution="execution">
        <template #navbar>
            <el-form-item>
                <el-select
                    filterable
                    clearable
                    :persistent="false"
                    :model-value="taskRunId"
                    @update:model-value="onFilter"
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
    </metrics-table>
</template>
<script>
    import {mapState} from "vuex";
    import MetricsTable from "../executions/MetricsTable.vue";

    export default {
        components: {
            MetricsTable
        },
        emits: ["follow"],
        mounted() {
            if (this.$refs.table) {
                this.$refs.table.loadData(this.$refs.table.onDataLoaded);
            }
        },
        data() {
            return {
                isModalOpen: false,
                taskRunId: undefined
            };
        },
        props: {
            preventRouteInfo : {
                type: Boolean,
                default: false
            }
        },
        methods: {
            onFilter(value) {
                this.taskRunId = value;
            }
        },
        computed: {
            ...mapState("execution", ["execution"]),
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
        },
    };
</script>

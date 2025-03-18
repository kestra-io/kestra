<template>
    <metrics-table
        ref="table"
        :task-run-id="$route.query.metric?.[0] ?? undefined"
        :show-task="true"
        :execution="execution"
    >
        <template #navbar>
            <KestraFilter
                :include="['metric']"
                :placeholder="`${$t('display metric for specific task')}...`"
                :values="{metric: selectOptions}"
            />
        </template>
    </metrics-table>
</template>
<script>
    import {mapState} from "vuex";

    import MetricsTable from "../executions/MetricsTable.vue";
    import KestraFilter from "../filter/KestraFilter.vue";

    export default {
        components: {
            MetricsTable,
            KestraFilter,
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
            };
        },
        computed: {
            ...mapState("execution", ["execution"]),
            selectOptions() {
                const options = {};
                for (const taskRun of this.execution.taskRunList || []) {
                    options[taskRun.id] = {
                        label:
                            taskRun.taskId +
                            (taskRun.value ? ` - ${taskRun.value}` : ""),
                        value: taskRun.id,
                    };
                }

                return Object.values(options);
            },
        },
    };
</script>

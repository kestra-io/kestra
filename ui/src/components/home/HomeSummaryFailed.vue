<template>
    <el-card
        :header="$t('homeDashboard.failedExecutions')"
    >
        <executions
            :page-size="10"
            :filters="filters"
            :restore-url="false"
            :statuses="['FAILED', 'KILLED', 'WARNING']"
            embed
            :topbar="false"
            :hidden="['id', 'state.endDate', 'triggers', 'selection', 'labels', 'inputs', 'taskRunList.taskId' , 'flowRevision']"
        />
    </el-card>
</template>
<script>
    import Executions from "../executions/Executions.vue";

    export default {
        components: {
            Executions
        },
        props: {
            filters: {
                type: Object,
                required: true
            },
        },
        computed: {
        }
    };
</script>

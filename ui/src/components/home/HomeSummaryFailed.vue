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
            :flow-id="flowId"
            :namespace="namespace"
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
            flowId: {
                type: String,
                required: true,
                default: undefined
            },
            namespace: {
                type: String,
                required: true,
                default: undefined
            }
        },
        computed: {
        }
    };
</script>

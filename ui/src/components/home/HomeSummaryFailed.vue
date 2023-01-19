<template>
    <el-card
        :header="$t('homeDashboard.failedExecutions')"
    >
        <executions
            :page-size="10"
            :filters="filters"
            :prevent-route-info="true"
            :restore-url="false"
            :statuses="['FAILED', 'KILLED', 'WARNING']"
            :embed="true"
            :hidden="['id', 'state.endDate', 'triggers']"
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

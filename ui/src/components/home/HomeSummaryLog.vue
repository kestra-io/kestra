<template>
    <el-card
        :header="$t('homeDashboard.errorLogs')"
    >
        <logs-wrapper
            :page-size="10"
            :filters="filters"
            :prevent-route-info="true"
            :restore-url="false"
            log-level="WARN"
            :embed="true"
        />
    </el-card>
</template>
<script>
    import LogsWrapper from "../logs/LogsWrapper.vue";

    export default {
        components: {
            LogsWrapper
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

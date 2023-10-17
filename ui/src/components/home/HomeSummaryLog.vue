<template>
    <el-card
        :header="$t('homeDashboard.errorLogs')"
    >
        <logs-wrapper
            :page-size="10"
            :filters="filters"
            :restore-url="false"
            log-level="WARN"
            embed
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

<template>
    <div>
        <log-filters />
        <log-list :level="level" :exclude-metas="['namespace', 'flowId', 'taskId', 'executionId']" :filter="filterTerm" />
    </div>
</template>
<script>
    import LogList from "../logs/LogList";
    import LogFilters from "../logs/LogFilters";
    import {mapState} from "vuex";
    export default {
        components: {LogList, LogFilters},
        computed: {
            ...mapState("execution", ["execution", "task", "logs"]),
            filterTerm() {
                return (this.$route.query.q || "").toLowerCase();
            },
            level() {
                return this.$route.query.level || "INFO";
            },
        },
    };
</script>

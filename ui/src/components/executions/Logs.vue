<template>
    <div>
        <log-filters @input="onChange" :filter="filterTerm" :level="level" />
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
            ...mapState("execution", ["execution", "taskRun", "logs"]),
            filterTerm() {
                return (this.$route.query.q || "").toLowerCase();
            },
            level() {
                return this.$route.query.level || "INFO";
            },
        },
        methods: {
            onChange(event) {
                this.$router.push({query: {...this.$route.query, q: event.filter, level: event.level, page: 1}});
            }
        }
    };
</script>

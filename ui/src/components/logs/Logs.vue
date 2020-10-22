<template>
    <div>
        <log-filters/>
        <log-list :level="level" :filter="filterTerm" />
    </div>
</template>
<script>
import LogList from "./LogList";
import LogFilters from "./LogFilters";
import { mapState } from "vuex";
export default {
    components: { LogList, LogFilters },
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

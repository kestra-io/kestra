<template>
    <div>
        <log-filters @onChange="loadData" />
        <hr />
        <template v-for="(log, i) in logs">
            <log-line
                level="TRACE"
                filter=""
                :log="log"
                :key="`${log.taskRunId}-${i}`"
                :metas="metas"
            />
        </template>
        <br />
        <pagination :total="total" @onPageChanged="onPageChanged" />
    </div>
</template>

<script>
import LogLine from "../logs/LogLine";
import LogFilters from "./LogFilters";
import Pagination from "../layout/Pagination";
import { mapState } from "vuex";
import RouteContext from "../../mixins/routeContext";
// import queryUtils from '../../utils/queryBuilder'
export default {
    mixins: [RouteContext],
    components: { LogLine, Pagination, LogFilters },
    data() {
        return {
            task: undefined,
            metas: [
                "attemptNumber",
                "executionId",
                "flowId",
                "level",
                "namespace",
                "taskId",
                "taskRunId",
                "thread",
                "timestamp",
            ],
        };
    },
    created() {
        this.loadData();
    },
    computed: {
        ...mapState("log", ["logs", "total", "level"]),
        routeInfo() {
            return {
                title: this.$t("logs"),
            };
        },
    },
    methods: {
        onPageChanged(pagination) {
            this.$router.push({
                query: { ...this.$route.query, ...pagination },
            });
            this.loadData();
        },
        loadData() {
            const query = [
                "message:" +
                    (this.$route.query.q ? `*${this.$route.query.q}*` : "*"),
                this.$route.query.level || "INFO", //TODO use minLevel serverside here
            ];
            this.$store.dispatch("log/findLogs", {
                q: query.join(" AND "),
                page: this.$route.query.page,
                size: this.$route.query.size,
            });
        },
    },
};
</script>
<style lang="scss" scoped>
@import "../../styles/_variable.scss";

.line:nth-child(odd) {
    background-color: $gray-300;
}
.line:nth-child(even) {
    background-color: lighten($gray-200, 5%);
}
</style>
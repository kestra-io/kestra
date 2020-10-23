<template>
    <b-card header-bg-variant="warning" header-tag="header">
        <template #header>
            <main-log-filter @onChange="loadData" />
        </template>
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
    </b-card>
</template>

<script>
import LogLine from "../logs/LogLine";
import Pagination from "../layout/Pagination";
import { mapState } from "vuex";
import RouteContext from "../../mixins/routeContext";
import MainLogFilter from "./MainLogFilter";
import qb from "../../utils/queryBuilder";

export default {
    mixins: [RouteContext],
    components: { LogLine, Pagination, MainLogFilter },
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
            const q = qb.logQueryBuilder(this.$route);
            console.log("logQueryBuilder", q, qb);

            this.$store.dispatch("log/findLogs", {
                q,
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
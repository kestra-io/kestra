<template>
    <div class="log-panel">
        <div class="log-content">
            <main-log-filter @onChange="loadData" />
            <div v-if="logs === undefined">
                <b-alert variant="light" show>{{$t('no result')}}</b-alert>
            </div>
            <div class="bg-dark text-white">
                <template v-for="(log, i) in logs">
                    <log-line
                        level="TRACE"
                        filter=""
                        :excludeMetas="isFlowEdit ? ['namespace', 'flowId'] : []"
                        :log="log"
                        :key="`${log.taskRunId}-${i}`"
                    />
                </template>
            </div>
        </div>
        <pagination :total="total" @onPageChanged="onPageChanged" />
    </div>
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
        isFlowEdit() {
            return this.$route.name === 'flowEdit'
        }
    },
    methods: {
        onPageChanged(pagination) {
            this.$router.push({
                query: { ...this.$route.query, ...pagination },
            });
            this.loadData();
        },
        loadData() {

            let q = qb.logQueryBuilder(this.$route);
            if (this.isFlowEdit) {
                q += ` AND namespace:${this.$route.params.namespace}`
                q += ` AND flowId:${this.$route.params.id}`
            }

            this.$store.dispatch("log/findLogs", {
                q,
                page: this.$route.query.page,
                size: this.$route.query.size,
                minLevel: `${this.$route.query.level || "INFO"}`
            });
        },
    },
};
</script>
<style lang="scss" scoped>
@import "../../styles/_variable.scss";
.log-panel {
    > div.log-content {
        margin-bottom: $spacer;
        .navbar {
            border: 1px solid $table-border-color;
        }

        .line:nth-child(odd) {
            background-color: $gray-800;
        }
        .line:nth-child(even) {
            background-color: lighten($gray-800, 5%);
        }
    }
}

</style>

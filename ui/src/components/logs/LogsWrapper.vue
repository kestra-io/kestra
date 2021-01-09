<template>
    <div class="log-panel">
        <div class="log-content">
            <main-log-filter v-if="!embed" @onChange="loadData" />
            <div v-if="logs === undefined">
                <b-alert variant="light" show>
                    {{ $t('no result') }}
                </b-alert>
            </div>
            <div class="bg-dark text-white">
                <template v-for="(log, i) in logs">
                    <log-line
                        level="TRACE"
                        filter=""
                        :exclude-metas="isFlowEdit ? ['namespace', 'flowId'] : []"
                        :log="log"
                        :key="`${log.taskRunId}-${i}`"
                    />
                </template>
            </div>
        </div>
        <pagination :size="pageSize" :page="pageNumber" :total="total" @onPageChanged="onPageChanged" />
    </div>
</template>

<script>
    import LogLine from "../logs/LogLine";
    import Pagination from "../layout/Pagination";
    import {mapState} from "vuex";
    import RouteContext from "../../mixins/routeContext";
    import MainLogFilter from "./MainLogFilter";
    import qb from "../../utils/queryBuilder";

    export default {
        mixins: [RouteContext],
        components: {LogLine, Pagination, MainLogFilter},
        props: {
            logLevel: {
                type: String,
                default: "INFO"
            },
            embed: {
                type: Boolean,
                default: false
            },
            pageSize: {
                type: Number,
                default: 25
            },
            pageNumber: {
                type: Number,
                default: 1
            },
        },
        data() {
            return {
                task: undefined,
                internalPageSize: undefined,
                internalPageNumber: undefined,
            };
        },
        created() {
            this.internalPageSize = this.pageSize;
            this.internalPageNumber = this.pageNumber;
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
                return this.$route.name === "flowEdit"
            }
        },
        methods: {
            onPageChanged(pagination) {
                this.internalPageSize = pagination.size;
                this.internalPageNumber = pagination.page;

                if (!this.embed) {
                    this.$router.push({
                        query: {...this.$route.query, ...pagination},
                    });
                }

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
                    page: this.$route.query.page || this.internalPageNumber,
                    size: this.$route.query.size  || this.internalPageSize,
                    minLevel: this.$route.query.level || this.logLevel
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

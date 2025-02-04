<template>
    <top-nav-bar v-if="!embed" :title="routeInfo.title" />
    <section v-bind="$attrs" :class="{'container': !embed}" class="log-panel">
        <div class="log-content">
            <data-table @page-changed="onPageChanged" ref="dataTable" :total="total" :size="pageSize" :page="pageNumber" :embed="embed">
                <template #navbar v-if="!embed || showFilters">
                    <KestraFilter
                        prefix="logs"
                        :include="['namespace', 'level', 'absolute_date', 'relative_date']"
                        :buttons="{
                            refresh: {shown: true, callback: refresh},
                            settings: {shown: true, charts: {shown: true, value: showChart, callback: onShowChartChange}}
                        }"
                    />
                </template>

                <template v-if="showStatChart()" #top>
                    <el-card shadow="never" class="mb-3" v-loading="!statsReady">
                        <div>
                            <template v-if="hasStatsData">
                                <Logs :data="logDaily" />
                            </template>
                            <NoData v-else />
                        </div>
                    </el-card>
                </template>

                <template #table v-if="logs !== undefined && logs.length > 0">
                    <div v-loading="isLoading">
                        <div class="logs-wrapper">
                            <log-line
                                v-for="(log, i) in logs"
                                :key="`${log.taskRunId}-${i}`"
                                level="TRACE"
                                filter=""
                                :exclude-metas="isFlowEdit ? ['namespace', 'flowId'] : []"
                                :log="log"
                            />
                        </div>
                    </div>
                </template>
            </data-table>
        </div>
    </section>
</template>

<script>
    import LogLine from "../logs/LogLine.vue";
    import {mapState} from "vuex";
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import RestoreUrl from "../../mixins/restoreUrl";
    import DataTableActions from "../../mixins/dataTableActions";
    import DataTable from "../../components/layout/DataTable.vue";
    import NoData from "../layout/NoData.vue";
    import _merge from "lodash/merge";
    import Logs from "../dashboard/components/charts/logs/Bar.vue";
    import {storageKeys} from "../../utils/constants";
    import KestraFilter from "../filter/KestraFilter.vue"

    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions],
        components: {
            KestraFilter,
            DataTable, LogLine, TopNavBar, Logs, NoData},
        props: {
            logLevel: {
                type: String,
                default: undefined
            },
            embed: {
                type: Boolean,
                default: false
            },
            charts: {
                type: Boolean,
                default: true
            },
            showFilters: {
                type: Boolean,
                default: false
            },
            filters: {
                type: Object,
                default: null
            },
        },
        data() {
            return {
                isDefaultNamespaceAllow: true,
                task: undefined,
                isLoading: false,
                lastRefreshDate: new Date(),
                statsReady: false,
                statsData: [],
                canAutoRefresh: false,
                showChart: ["true", null].includes(localStorage.getItem(storageKeys.SHOW_LOGS_CHART)),
            };
        },
        computed: {
            storageKeys() {
                return storageKeys
            },
            ...mapState("log", ["logs", "total", "level"]),
            ...mapState("stat", ["logDaily"]),
            routeInfo() {
                return {
                    title: this.$t("logs"),
                };
            },
            isFlowEdit() {
                return this.$route.name === "flows/update"
            },
            isNamespaceEdit() {
                return this.$route.name === "namespaces/update"
            },
            selectedLogLevel() {
                return this.logLevel || this.$route.query.level || localStorage.getItem("defaultLogLevel") || "INFO";
            },
            endDate() {
                if (this.$route.query.endDate) {
                    return this.$route.query.endDate;
                }
                return undefined;
            },
            startDate() {
                // we mention the last refresh date here to trick
                // VueJs fine grained reactivity system and invalidate
                // computed property startDate
                if (this.$route.query.startDate && this.lastRefreshDate) {
                    return this.$route.query.startDate;
                }
                if (this.$route.query.timeRange) {
                    return this.$moment().subtract(this.$moment.duration(this.$route.query.timeRange).as("milliseconds")).toISOString(true);
                }

                // the default is PT30D
                return this.$moment().subtract(7, "days").toISOString(true);
            },
            namespace() {
                return this.$route.params.namespace ?? this.$route.params.id;
            },
            flowId() {
                return this.$route.params.id;
            },
            countStats() {
                return [...(this.logDaily || [])].reduce((a, b) => {
                    return a + Object.values(b.counts).reduce((a, b) => a + b, 0);
                }, 0);
            },
            hasStatsData() {
                return this.countStats > 0;
            },
        },
        beforeRouteEnter(to, from, next) {
            const defaultNamespace = localStorage.getItem(storageKeys.DEFAULT_NAMESPACE);
            const query = {...to.query};
            if (defaultNamespace) {
                query.namespace = defaultNamespace;
            }
            next(vm => {
                vm.$router?.replace({query});
            });
        },
        methods: {
            onDateFilterTypeChange(event) {
                this.canAutoRefresh = event;
            },
            showStatChart() {
                return this.charts && this.showChart;
            },
            onShowChartChange(value) {
                this.showChart = value;
                localStorage.setItem(storageKeys.SHOW_LOGS_CHART, value);
                if (this.showStatChart()) {
                    this.loadStats();
                }
            },
            refresh() {
                this.lastRefreshDate = new Date();
                this.load();
            },
            loadQuery(base) {
                let queryFilter = this.filters ?? this.queryWithFilter();

                if (this.isFlowEdit) {
                    queryFilter["namespace"] = this.namespace;
                    queryFilter["flowId"] = this.flowId;
                } else if (this.isNamespaceEdit) {
                    queryFilter["namespace"] = this.namespace;
                }

                if (!queryFilter["startDate"] || !queryFilter["endDate"]) {
                    queryFilter["startDate"] = this.startDate;
                    queryFilter["endDate"] = this.endDate;
                }

                delete queryFilter["level"];

                return _merge(base, queryFilter)
            },
            load() {
                this.isLoading = true


                const data = {
                    page: this.filters ? this.internalPageNumber : this.$route.query.page || this.internalPageNumber,
                    size: this.filters ? this.internalPageSize : this.$route.query.size || this.internalPageSize,
                    ...this.filters
                };
                this.$store
                    .dispatch("log/findLogs", this.loadQuery({
                        ...data,
                        minLevel: this.filters ? null : this.selectedLogLevel,
                        sort: "timestamp:desc"
                    }))
                    .finally(() => {
                        this.isLoading = false
                        this.saveRestoreUrl();
                    });

                this.loadStats();
            },
            loadStats() {
                this.statsReady = false;
                this.$store
                    .dispatch("stat/logDaily", this.loadQuery({
                        startDate: this.$moment(this.startDate).toISOString(true),
                        endDate: this.$moment(this.endDate).toISOString(true)
                    }, true))
                    .then(() => {
                        this.statsReady = true;
                    });
            }
        },
    };
</script>
<style lang="scss" scoped>
    @import "@kestra-io/ui-libs/src/scss/variables";

    .log-panel {
        > div.log-content {
            margin-bottom: 1rem;
            .navbar {
                border: 1px solid var(--ks-border-primary);
            }
        }

        .logs-wrapper {
            margin-bottom: 1rem;
            border-radius: var(--bs-border-radius-lg);
            overflow: hidden;
            padding: $spacer;
            padding-top: .5rem;
            background-color: var(--ks-background-card);
            border: 1px solid var(--ks-border-primary);

            html.dark & {
                background-color: var(--bs-gray-100);
            }

            > * + * {
                border-top: 1px solid var(--ks-border-primary);
            }
        }
    }
</style>

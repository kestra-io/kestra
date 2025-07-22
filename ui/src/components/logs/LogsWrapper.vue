<template>
    <top-nav-bar v-if="!embed" :title="routeInfo.title" />
    <section v-bind="$attrs" :class="{'container': !embed}" class="log-panel">
        <div class="log-content">
            <data-table @page-changed="onPageChanged" ref="dataTable" :total="logsStore.total" :size="pageSize" :page="pageNumber" :embed="embed">
                <template #navbar v-if="!embed || showFilters">
                    <KestraFilter
                        prefix="logs"
                        :language="LogFilterLanguage"
                        :buttons="{
                            refresh: {shown: true, callback: refresh},
                            settings: {shown: true, charts: {shown: true, value: showChart, callback: onShowChartChange}}
                        }"
                    />
                </template>

                <template v-if="showStatChart()" #top>
                    <Sections :charts :dashboard="{id: 'default', charts: []}" show-default />
                </template>

                <template #table v-if="logsStore.logs !== undefined && logsStore.logs.length > 0">
                    <div v-loading="isLoading">
                        <div class="logs-wrapper">
                            <log-line
                                v-for="(log, i) in logsStore.logs"
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

<script setup lang="ts">
    import LogFilterLanguage from "../../composables/monaco/languages/filters/impl/logFilterLanguage";
    import Sections from "../dashboard/sections/Sections.vue";
    import DataTable from "../../components/layout/DataTable.vue";
    import KestraFilter from "../filter/KestraFilter.vue"
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import LogLine from "../logs/LogLine.vue";
</script>

<script lang="ts">
    import {mapStores} from "pinia";
    import RouteContext from "../../mixins/routeContext";
    import RestoreUrl from "../../mixins/restoreUrl";
    import DataTableActions from "../../mixins/dataTableActions";
    import _merge from "lodash/merge";
    import {storageKeys} from "../../utils/constants";
    import {decodeSearchParams} from "../filter/utils/helpers";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import YAML_CHART from "../dashboard/assets/logs_timeseries_chart.yaml?raw";
    import {useLogsStore} from "../../stores/logs";

    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions],
        props: {
            logLevel: {
                type: String,
                default: undefined
            },
            embed: {
                type: Boolean,
                default: false
            },
            withCharts: {
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
                canAutoRefresh: false,
                showChart: ["true", null].includes(localStorage.getItem(storageKeys.SHOW_LOGS_CHART)),
            };
        },
        computed: {
            storageKeys() {
                return storageKeys
            },
            ...mapStores(useLogsStore),
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
                const decodedParams = decodeSearchParams(this.$route.query, ["level"], []);
                const levelFilters = decodedParams.filter(item => item.label === "level");
                const decoded = levelFilters.length > 0 ? levelFilters[0].value : "INFO";
                return this.logLevel || decoded || localStorage.getItem("defaultLogLevel") || "INFO";
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
            charts() {
                return [
                    {...YAML_UTILS.parse(YAML_CHART), content: YAML_CHART}
                ];
            }
        },
        beforeRouteEnter(to, _, next) {
            const defaultNamespace = localStorage.getItem(
                storageKeys.DEFAULT_NAMESPACE,
            );
            const query = {...to.query};
            let queryHasChanged = false;

            const queryKeys = Object.keys(query);
            if (defaultNamespace && !queryKeys.some(key => key.startsWith("filters[namespace]"))) {
                query["filters[namespace][PREFIX]"] = defaultNamespace;
                queryHasChanged = true;
            }

            if (queryHasChanged) {
                next({
                    ...to,
                    query,
                    replace: true
                });
            } else {
                next();
            }
        },
        methods: {
            LogFilterLanguage() {
                return LogFilterLanguage
            },
            onDateFilterTypeChange(event) {
                this.canAutoRefresh = event;
            },
            showStatChart() {
                return this.showChart;
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
                    queryFilter["filters[namespace][EQUALS]"] = this.namespace;
                    queryFilter["filters[flowId][EQUALS]"] = this.flowId;
                } else if (this.isNamespaceEdit) {
                    queryFilter["filters[namespace][EQUALS]"] = this.namespace;
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
                this.logsStore.findLogs(this.loadQuery({
                    ...data,
                    minLevel: this.filters ? null : this.selectedLogLevel,
                    sort: "timestamp:desc"
                }))
                    .finally(() => {
                        this.isLoading = false
                        this.saveRestoreUrl();
                    });

            },
        },
    };
</script>
<style lang="scss" scoped>
    @import "@kestra-io/ui-libs/src/scss/variables";

    .shadow {
        box-shadow: 0px 2px 4px 0px var(--ks-card-shadow) !important;
    }

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

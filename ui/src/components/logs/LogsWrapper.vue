<template>
    <div class="log-panel">
        <div class="log-content">
            <data-table @onPageChanged="onPageChanged" ref="dataTable" :total="total" :size="pageSize" :page="pageNumber">
                <template #navbar v-if="embed === false">
                    <search-field />
                    <namespace-select
                        data-type="flow"
                        v-if="$route.name !== 'flows/update'"
                        :value="$route.query.namespace"
                        @input="onDataTableValue('namespace', $event)"
                    />
                    <log-level-selector
                        :value="$route.query.level"
                        @input="onDataTableValue('level', $event)"
                    />
                    <date-range
                        :start="$route.query.start"
                        :end="$route.query.end"
                        @input="onDataTableValue($event)"
                    />
                    <refresh-button class="float-right" @onRefresh="load" />
                </template>

                <template #table>
                    <b-overlay :show="isLoading" variant="transparent">
                        <div v-if="logs === undefined || logs.length === 0">
                            <b-alert variant="light" class="text-muted" show>
                                {{ $t('no result') }}
                            </b-alert>
                        </div>
                        <div v-else class="logs-wrapper mb-2 text-dark">
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
                    </b-overlay>
                </template>
            </data-table>
        </div>
    </div>
</template>

<script>
    import LogLine from "../logs/LogLine";
    import {mapState} from "vuex";
    import RouteContext from "../../mixins/routeContext";
    import qb from "../../utils/queryBuilder";
    import RestoreUrl from "../../mixins/restoreUrl";
    import DataTableActions from "../../mixins/dataTableActions";
    import NamespaceSelect from "../namespace/NamespaceSelect";
    import SearchField from "../layout/SearchField";
    import DateRange from "../layout/DateRange";
    import LogLevelSelector from "./LogLevelSelector";
    import DataTable from "../../components/layout/DataTable";
    import RefreshButton from "../../components/layout/RefreshButton";

    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions],
        components: {DataTable, LogLine, NamespaceSelect, DateRange, SearchField, LogLevelSelector, RefreshButton},
        props: {
            logLevel: {
                type: String,
                default: "INFO"
            },
            embed: {
                type: Boolean,
                default: false
            },
        },
        data() {
            return {
                isDefaultNamespaceAllow: true,
                task: undefined,
                isLoading: false
            };
        },
        computed: {
            ...mapState("log", ["logs", "total", "level"]),
            routeInfo() {
                return {
                    title: this.$t("logs"),
                };
            },
            isFlowEdit() {
                return this.$route.name === "flows/update"
            }
        },
        methods: {
            loadQuery() {
                let filter = []
                let query = this.queryWithFilter();

                if (query.namespace) {
                    filter.push(`namespace:${query.namespace}*`)
                }

                if (query.start) {
                    filter.push(`timestamp:[${query.start} TO *]`)
                }

                if (query.end) {
                    filter.push(`timestamp:[* TO ${query.end}]`)
                }

                if (query.q) {
                    filter.push(qb.toTextLucene(query.q));
                }

                if (this.isFlowEdit) {
                    filter.push(`namespace:${this.$route.params.namespace}`)
                    filter.push(`flowId:${this.$route.params.id}`)
                }

                return filter.join(" AND ") || "*"
            },
            load() {
                this.isLoading = true
                this.$store
                    .dispatch("log/findLogs", {
                        q: this.loadQuery(),
                        page: this.$route.query.page || this.internalPageNumber,
                        size: this.$route.query.size || this.internalPageSize,
                        minLevel: this.$route.query.level || this.logLevel
                    })
                    .finally(() => {
                        this.isLoading = false
                        this.saveRestoreUrl();
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
            border: 1px solid var(--table-border-color);
        }
    }

    .logs-wrapper {
        border: 1px solid var(--table-border-color);
    }

    .line:nth-child(odd) {
        background-color: var(--gray-100);

    }

    .line:nth-child(even) {
        background-color: var(--gray-100-lighten-5);
    }

}

</style>

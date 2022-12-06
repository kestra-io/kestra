<template>
    <div class="log-panel">
        <div class="log-content">
            <data-table @page-changed="onPageChanged" ref="dataTable" :total="total" :size="pageSize" :page="pageNumber">
                <template #navbar v-if="embed === false">
                    <el-form-item>
                        <search-field />
                    </el-form-item>
                    <el-form-item>
                        <namespace-select
                            data-type="flow"
                            v-if="$route.name !== 'flows/update'"
                            :value="$route.query.namespace"
                            @update:model-value="onDataTableValue('namespace', $event)"
                        />
                    </el-form-item>
                    <el-form-item>
                        <log-level-selector
                            :value="$route.query.level"
                            @update:model-value="onDataTableValue('level', $event)"
                        />
                    </el-form-item>
                    <el-form-item>
                        <date-range
                            :start-date="$route.query.startDate"
                            :end-date="$route.query.endDate"
                            @update:model-value="onDataTableValue($event)"
                        />
                    </el-form-item>
                    <el-form-item>
                        <refresh-button class="float-right" @refresh="load" />
                    </el-form-item>
                </template>

                <template #table>
                    <div v-loading="isLoading">
                        <div v-if="logs === undefined || logs.length === 0">
                            <el-alert type="info" :closable="false" class="text-muted" show-icon>
                                {{ $t('no result') }}
                            </el-alert>
                        </div>
                        <div v-else class="logs-wrapper">
                            <template v-for="(log, i) in logs" :key="`${log.taskRunId}-${i}`">
                                <log-line
                                    level="TRACE"
                                    filter=""
                                    :exclude-metas="isFlowEdit ? ['namespace', 'flowId'] : []"
                                    :log="log"
                                />
                            </template>
                        </div>
                    </div>
                </template>
            </data-table>
        </div>
    </div>
</template>

<script>
    import LogLine from "../logs/LogLine";
    import {mapState} from "vuex";
    import RouteContext from "../../mixins/routeContext";
    import RestoreUrl from "../../mixins/restoreUrl";
    import DataTableActions from "../../mixins/dataTableActions";
    import NamespaceSelect from "../namespace/NamespaceSelect";
    import SearchField from "../layout/SearchField";
    import DateRange from "../layout/DateRange";
    import LogLevelSelector from "./LogLevelSelector";
    import DataTable from "../../components/layout/DataTable";
    import RefreshButton from "../../components/layout/RefreshButton";
    import _merge from "lodash/merge";

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
            loadQuery(base) {
                let queryFilter = this.queryWithFilter();

                if (this.isFlowEdit) {
                    queryFilter["namespace"] = this.$route.params.namespace;
                    queryFilter["flowId"] = this.$route.params.id;
                }

                delete queryFilter["level"];

                return _merge(base, queryFilter)
            },
            load() {
                this.isLoading = true
                this.$store
                    .dispatch("log/findLogs", this.loadQuery({
                        page: this.$route.query.page || this.internalPageNumber,
                        size: this.$route.query.size || this.internalPageSize,
                        minLevel: this.$route.query.level || this.logLevel
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
    .log-panel {
        > div.log-content {
            margin-bottom: var(--spacer);
            .navbar {
                border: 1px solid var(--bs-border-color);
            }
        }

        .logs-wrapper {
            border: 1px solid var(--bs-border-color);
            margin-bottom: var(--spacer);
        }

        .line:nth-child(odd) {
            background-color: var(--bs-gray-100-lighten-5);

        }

        .line:nth-child(even) {
            background-color: var(--bs-gray-100);
        }
    }
</style>

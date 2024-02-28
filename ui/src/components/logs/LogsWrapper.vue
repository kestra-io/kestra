<template>
    <top-nav-bar v-if="!embed" :title="routeInfo.title" />
    <section :class="{'container': !embed}" class="log-panel">
        <div class="log-content">
            <data-table @page-changed="onPageChanged" ref="dataTable" :total="total" :size="pageSize" :page="pageNumber" :embed="embed">
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
                            :value="selectedLogLevel"
                            @update:model-value="onDataTableValue('level', $event)"
                        />
                    </el-form-item>
                    <el-form-item>
                        <date-range
                            :start-date="startDate"
                            :end-date="endDate"
                            @update:model-value="onDataTableValue($event)"
                        />
                    </el-form-item>
                    <el-form-item>
                        <refresh-button class="float-right" @refresh="refresh" />
                    </el-form-item>
                </template>

                <template #table>
                    <div v-loading="isLoading">
                        <div v-if="logs === undefined || logs.length === 0">
                            <el-alert type="info" :closable="false" class="text-muted">
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
    </section>
</template>

<script>
    import LogLine from "../logs/LogLine.vue";
    import {mapState} from "vuex";
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import RestoreUrl from "../../mixins/restoreUrl";
    import DataTableActions from "../../mixins/dataTableActions";
    import NamespaceSelect from "../namespace/NamespaceSelect.vue";
    import SearchField from "../layout/SearchField.vue";
    import DateRange from "../layout/DateRange.vue";
    import LogLevelSelector from "./LogLevelSelector.vue";
    import DataTable from "../../components/layout/DataTable.vue";
    import RefreshButton from "../../components/layout/RefreshButton.vue";
    import _merge from "lodash/merge";

    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions],
        components: {DataTable, LogLine, NamespaceSelect, DateRange, SearchField, LogLevelSelector, RefreshButton, TopNavBar},
        props: {
            logLevel: {
                type: String,
                default: undefined
            }
        },
        data() {
            return {
                isDefaultNamespaceAllow: true,
                task: undefined,
                isLoading: false,
                recomputeInterval: false
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
            },
            selectedLogLevel() {
                return this.logLevel || this.$route.query.level || localStorage.getItem("defaultLogLevel") || "INFO";
            },
            endDate() {
                // used to be able to force refresh the base interval when auto-reloading
                this.recomputeInterval;
                return this.$route.query.endDate ? this.$route.query.endDate : undefined;
            },
            startDate() {
                // used to be able to force refresh the base interval when auto-reloading
                this.recomputeInterval;
                return this.$route.query.startDate ? this.$route.query.startDate : this.$moment(this.endDate)
                    .add(-7, "days").toISOString(true);
            }
        },
        methods: {
            refresh() {
                this.recomputeInterval = !this.recomputeInterval;
                this.load();
            },
            loadQuery(base) {
                let queryFilter = this.queryWithFilter();

                if (this.isFlowEdit) {
                    queryFilter["namespace"] = this.$route.params.namespace;
                    queryFilter["flowId"] = this.$route.params.id;
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
                this.$store
                    .dispatch("log/findLogs", this.loadQuery({
                        page: this.$route.query.page || this.internalPageNumber,
                        size: this.$route.query.size || this.internalPageSize,
                        minLevel: this.selectedLogLevel,
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

    .log-panel {
        > div.log-content {
            margin-bottom: var(--spacer);
            .navbar {
                border: 1px solid var(--bs-border-color);
            }
        }

        .logs-wrapper {
            margin-bottom: var(--spacer);
            border-radius: var(--bs-border-radius-lg);
            overflow: hidden;
            padding: $spacer;
            background-color: var(--bs-white);
            border: 1px solid var(--bs-border-color);

            html.dark & {
                background-color: var(--bs-gray-100);
            }

            * + * {
                border-top: 1px solid var(--bs-border-color);
            }
        }
    }
</style>

<template>
    <TopNavBar v-if="!embed" :title="routeInfo.title" />
    <section v-bind="$attrs" :class="{'container': !embed}" class="log-panel">
        <div class="log-content">
            <DataTable @page-changed="onPageChanged" ref="dataTable" :total="logsStore.total" :size="pageSize" :page="pageNumber" :embed="embed">
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
                    <Sections ref="dashboard" :charts :dashboard="{id: 'default', charts: []}" showDefault />
                </template>

                <template #table v-if="logsStore.logs !== undefined && logsStore.logs.length > 0">
                    <div class="logs-header">
                        <BulkSelect
                            :selectAll="selectAll"
                            :selections="bulkSelect"
                            :total="logsStore.total"
                            @update:select-all="toggleSelectAllPages"
                            @unselect="unselectAll"
                            @select="toggleSelectAll"
                        >
                            <!-- Always visible buttons -->
                            <el-button :icon="Delete" @click="openConfirmationModal">
                                {{ $t("delete") }}
                            </el-button>
                        </BulkSelect>
                    </div>
                    <div v-loading="isLoading">
                        <div class="logs-wrapper">
                            <LogLine
                                v-for="(log, i) in logsStore.logs"
                                :key="`${log.taskRunId}-${i}`"
                                level="TRACE"
                                filter=""
                                :excludeMetas="isFlowEdit ? ['namespace', 'flowId'] : []"
                                :log="log"
                                :bulkSelect="bulkSelect"
                                :index="i"
                                :selectAll="selectAll"
                                :pageLength="pageSize"
                                @update-select-all="selectAll = $event"
                                @update:bulk-select="updateBulkSelect"
                                @update:bulk-select-length="bulkSelect.length = $event"
                            />
                        </div>
                    </div>
                </template>
            </DataTable>
            <template>
                <el-dialog
                    v-if="isOpenLabelsModal"
                    v-model="isOpenLabelsModal"
                    destroyOnClose
                    :appendToBody="true"
                    alignCenter
                >
                    <template #header>
                        <h5>{{ $t("Confirmation") }}</h5>
                    </template>
                    <div>Are you sure you want to delete the selected logs?</div>
                    <template #footer>
                        <el-button @click="isOpenLabelsModal = false">
                            {{ $t("cancel") }}
                        </el-button>
                        <el-button type="primary" @click="handleBulkDelete()">
                            {{ $t("ok") }}
                        </el-button>
                    </template>
                </el-dialog>
            </template>
        </div>
    </section>
</template>

<script setup lang="ts">
    import LogFilterLanguage from "../../composables/monaco/languages/filters/impl/logFilterLanguage";
    import Sections from "../dashboard/sections/Sections.vue";
    import DataTable from "../../components/layout/DataTable.vue";
    import KestraFilter from "../filter/KestraFilter.vue"
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import LogLine from "./LogLine.vue";
    import BulkSelect from "../layout/BulkSelect.vue";
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
    import {defaultNamespace} from "../../composables/useNamespaces";
    import {defineComponent} from "vue";

    export default defineComponent({
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
            reloadLogs: {
                type: Number,
                default: undefined
            }
        },
        data() {
            return {
                pageSize: 10,
                pageNumber: 1,
                isDefaultNamespaceAllow: true,
                task: undefined,
                isLoading: false,
                lastRefreshDate: new Date(),
                canAutoRefresh: false,
                showChart: ["true", null].includes(localStorage.getItem(storageKeys.SHOW_LOGS_CHART)),
                bulkSelect: [],
                selectAll: false,
                isOpenLabelsModal: false,
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
                const decodedParams = decodeSearchParams(this.$route.query);
                const levelFilters = decodedParams.filter(item => item.field === "level");
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
        beforeRouteEnter(to: any, _: any, next: (route?: any) => void) {
            const query = {...to.query};
            let queryHasChanged = false;

            const queryKeys = Object.keys(query);
            if (defaultNamespace() && !queryKeys.some(key => key.startsWith("filters[namespace]"))) {
                query["filters[namespace][PREFIX]"] = defaultNamespace();
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
            showStatChart() {
                return this.showChart;
            },
            onShowChartChange(value: boolean) {
                this.showChart = value;
                localStorage.setItem(storageKeys.SHOW_LOGS_CHART, value.toString());
                if (this.showStatChart()) {
                    this.loadStats();
                }
            },
            refresh() {
                this.lastRefreshDate = new Date();
                this.$refs.dashboard.refreshCharts();
                this.load();
            },
            loadQuery(base: any) {
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
            handleBulkDelete(){
                if(this.selectAll){
                    const data = {
                        page: this.filters ? this.internalPageNumber : this.$route.query.page || this.internalPageNumber,
                        size: this.filters ? this.internalPageSize : this.$route.query.size || this.internalPageSize,
                        ...this.filters
                    };
                    this.logsStore.deleteLogsByFilter(this.loadQuery({
                        ...data,
                        minLevel: this.filters ? null : this.selectedLogLevel,
                        sort: "timestamp:desc"
                    }))
                        .then(()=>{
                            this.bulkSelect = [];
                            this.refresh();
                        })
                        .finally(() => {
                            this.isOpenLabelsModal = false;
                        });
                }else{
                    const logsToDelete = [];
                    for(let i=0;i<this.bulkSelect.length;i++){
                        const logIndex = this.bulkSelect[i];
                        const logToDelete = this.logsStore.logs[logIndex];
                        logsToDelete.push(logToDelete);
                    }
                    this.logsStore.deleteBulkLogs(logsToDelete)
                        .then(() => {
                            this.bulkSelect = [];
                            this.refresh();
                        })
                        .finally(() => {
                            this.isOpenLabelsModal = false;
                        });
                }
            },
            toggleSelectAll(){
                this.bulkSelect = [];
                for(let i=0;i<this.logsStore.logs.length;i++){
                    this.bulkSelect.push(i);
                }
            },
            unselectAll(){
                this.bulkSelect = []
            },
            openConfirmationModal(){
                this.isOpenLabelsModal = true;
            },
            toggleSelectAllPages(){
                this.selectAll = !this.selectAll;
                this.bulkSelect = [];
                for(let i=0;i<this.logsStore.total;i++){
                    this.bulkSelect.push(i);
                }
            },
            onPageChanged({page, size}: { page: number; size: number }) {
                this.pageNumber = page;
                this.pageSize = size; // this will automatically update <LogLine :pageLength="pageSize">
                this.load();
            },
            updateBulkSelect(index: number, value: boolean) {
                if (value) {
                    this.bulkSelect.push(index); 
                } else {
                    const idx = this.bulkSelect.indexOf(index);
                    if (idx > -1) {
                        this.bulkSelect.splice(idx, 1);
                    }
                }
            }
        },
        emits:[ "update-select-all", "update:bulkSelect", "update:bulkSelectLength" ],
        watch: {
            reloadLogs(newValue) {
                if(newValue) this.refresh();
            },
        }
    });
</script>
<style scoped lang="scss">
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

        .logs-header{
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-radius: var(--bs-border-radius-lg);
            overflow: hidden;
            height: 60px;
            background-color: var(--ks-background-card);
            border: 1px solid var(--ks-border-primary);
            border-bottom-right-radius: 0;
            border-bottom-left-radius: 0;
            position: relative;
            padding-left: 0.7rem;
            top: 5px;
            html.dark & {
                background-color: var(--bs-gray-100);
            }
        }
    }

    .bulk-select-checkbox{
        margin-left: 1.2rem;
        align-self: flex-start;
    }
</style>

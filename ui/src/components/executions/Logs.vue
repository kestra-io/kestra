<template>
    <div data-component="FILENAME_PLACEHOLDER">
        <KSFilter
            :configuration="logExecutionsFilter"
            :tableOptions="{
                chart: {shown: false},
                columns: {shown: false},
                refresh: {shown: true, callback: loadLogs}
            }"
            @search="filter = $event"
            @filter="onFilterChange"
        />
        <Collapse>
            <el-form-item v-for="logLevel in currentLevelOrLower" :key="logLevel">
                <LogLevelNavigator
                    v-if="countByLogLevel[logLevel] > 0"
                    :cursorIdx="cursorLogLevel === logLevel ? cursorIdxForLevel : undefined"
                    :level="logLevel"
                    :totalCount="countByLogLevel[logLevel]"
                    @previous="previousLogForLevel(logLevel)"
                    @next="nextLogForLevel(logLevel)"
                    @close="logCursor = undefined"
                    class="w-100"
                />
            </el-form-item>
            <el-form-item>
                <el-button @click="expandCollapseAll()" :disabled="raw_view" :icon="logDisplayButtonIcon">
                    {{ logDisplayButtonText }}
                </el-button>
            </el-form-item>
            <el-form-item>
                <el-tooltip
                    :content="!raw_view ? $t('logs_view.raw_details') : $t('logs_view.compact_details')"
                >
                    <el-button @click="toggleViewType" :icon="logViewTypeButtonIcon">
                        {{ !raw_view ? $t('logs_view.raw') : $t('logs_view.compact') }}
                    </el-button>
                </el-tooltip>
            </el-form-item>
            <el-form-item>
                <el-button-group class="ks-b-group">
                    <Restart v-if="executionsStore.execution" :execution="executionsStore.execution" class="ms-0" @follow="forwardEvent('follow', $event)" />
                    <el-button @click="downloadContent()">
                        <Kicon :tooltip="$t('download logs')">
                            <Download />
                        </Kicon>
                    </el-button>
                    <el-button @click="copyAllLogs()">
                        <Kicon :tooltip="$t('copy logs')">
                            <ContentCopy />
                        </Kicon>
                    </el-button>
                </el-button-group>
            </el-form-item>
            <el-form-item>
                <el-button-group class="ks-b-group">
                    <el-button @click="loadLogs()">
                        <Kicon :tooltip="$t('refresh')">
                            <Refresh />
                        </Kicon>
                    </el-button>
                </el-button-group>
            </el-form-item>
        </Collapse>

        <TaskRunDetails
            v-if="!raw_view"
            ref="logs"
            :level="level"
            :excludeMetas="['namespace', 'flowId', 'taskId', 'executionId']"
            :filter="filter"
            :levelToHighlight="cursorLogLevel"
            @log-cursor="logCursor = $event"
            :logCursor="logCursor"
            @follow="forwardEvent('follow', $event)"
            @opened-taskruns-count="openedTaskrunsCount = $event"
            @log-indices-by-level="Object.entries($event).forEach(([levelName, indices]) => logIndicesByLevel[levelName] = indices)"
            :targetFlow="executionsStore.flow"
            :showProgressBar="false"
        />
        <el-card v-else class="attempt-wrapper">
            <DynamicScroller
                ref="logScroller"
                :items="temporalLogs"
                :minItemSize="50"
                keyField="index"
                class="log-lines temporal"
                :buffer="200"
                :prerender="20"
            >
                <template #default="{item, active}">
                    <DynamicScrollerItem
                        :item="item"
                        :active="active"
                        :sizeDependencies="[item.message]"
                        :data-index="item.index"
                    >
                        <LogLine
                            @click="logCursor = item.index.toString()"
                            class="line"
                            :class="{['log-bg-' + cursorLogLevel?.toLowerCase()]: cursorLogLevel === item.level, 'opacity-40': cursorLogLevel && cursorLogLevel !== item.level}"
                            :cursor="item.index.toString() === logCursor"
                            :excludeMetas="['namespace', 'flowId', 'executionId']"
                            :level="level"
                            :filter="filter"
                            :log="item"
                        />
                    </DynamicScrollerItem>
                </template>
            </DynamicScroller>
        </el-card>
    </div>
</template>

<script setup>
    import {useLogExecutionsFilter} from "../filter/configurations";

    const logExecutionsFilter = useLogExecutionsFilter();
</script>
<script>
    import TaskRunDetails from "../logs/TaskRunDetails.vue";
    import Download from "vue-material-design-icons/Download.vue";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import UnfoldMoreHorizontal from "vue-material-design-icons/UnfoldMoreHorizontal.vue";
    import UnfoldLessHorizontal from "vue-material-design-icons/UnfoldLessHorizontal.vue";
    import ViewList from "vue-material-design-icons/ViewList.vue";
    import ViewGrid from "vue-material-design-icons/ViewGrid.vue";
    import Kicon from "../Kicon.vue";
    import LogLevelNavigator from "../logs/LogLevelNavigator.vue";
    import {DynamicScroller, DynamicScrollerItem} from "vue-virtual-scroller";
    import "vue-virtual-scroller/dist/vue-virtual-scroller.css"
    import Collapse from "../layout/Collapse.vue";
    import {State, Utils as LibUtils} from "@kestra-io/ui-libs"
    import Utils from "../../utils/utils";
    import LogLine from "../logs/LogLine.vue";
    import Restart from "./overview/components/actions/Restart.vue";
    import * as LogUtils from "../../utils/logs";
    import Refresh from "vue-material-design-icons/Refresh.vue";
    import {mapStores} from "pinia";
    import {useExecutionsStore} from "../../stores/executions";
    import KSFilter from "../filter/components/KSFilter.vue";
    import {storageKeys} from "../../utils/constants";

    export default {
        components: {
            LogLine,
            TaskRunDetails,
            LogLevelNavigator,
            Kicon,
            Download,
            ContentCopy,
            Collapse,
            Restart,
            DynamicScroller,
            DynamicScrollerItem,
            Refresh,
            KSFilter
        },
        data() {
            return {
                fullscreen: false,
                level: undefined,
                filter: undefined,
                openedTaskrunsCount: 0,
                raw_view: (localStorage.getItem(storageKeys.LOGS_VIEW_TYPE) ?? "false").toLowerCase() === "true",
                logIndicesByLevel: Object.fromEntries(LogUtils.levelOrLower(undefined).map(level => [level, []])),
                logCursor: undefined
            };
        },
        created() {
            this.level = (this.$route.query.level || localStorage.getItem("defaultLogLevel") || "INFO");
            this.filter = (this.$route.query.q || undefined);
        },
        watch:{
            level: {
                handler() {
                    if (this.raw_view) {
                        this.loadLogs();
                    }
                }
            },
            logCursor(newValue) {
                if (newValue !== undefined && this.raw_view) {
                    this.scrollToLog(newValue);
                }
            }
        },
        computed: {
            State() {
                return State
            },
            temporalLogs() {
                if (!this.executionsStore.logs?.length) {
                    return [];
                }

                const filtered = this.executionsStore.logs.filter(log => {
                    if (!this.filter) return true;
                    return log.message?.toLowerCase().includes(this.filter.toLowerCase());
                });

                return filtered.map((logLine, index) => ({
                    ...logLine,
                    index
                }));
            },
            ...mapStores(useExecutionsStore),
            executionId() {
                return this.executionsStore.execution.id;
            },
            downloadName() {
                return `kestra-execution-${this.$moment().format("YYYYMMDDHHmmss")}-${this.executionId}.log`
            },
            logDisplayButtonText() {
                return this.openedTaskrunsCount === 0 ? this.$t("expand all") : this.$t("collapse all")
            },
            logDisplayButtonIcon() {
                return this.openedTaskrunsCount === 0 ? UnfoldMoreHorizontal : UnfoldLessHorizontal;
            },
            logViewTypeButtonIcon() {
                return this.raw_view ? ViewGrid : ViewList;
            },
            currentLevelOrLower() {
                return LogUtils.levelOrLower(this.level);
            },
            countByLogLevel() {
                return Object.fromEntries(Object.entries(this.viewTypeAwareLogIndicesByLevel).map(([level, indices]) => [level, indices.length]));
            },
            cursorLogLevel() {
                return Object.entries(this.viewTypeAwareLogIndicesByLevel).find(([_, indices]) => indices.includes(this.logCursor))?.[0];
            },
            cursorIdxForLevel() {
                return this.viewTypeAwareLogIndicesByLevel?.[this.cursorLogLevel]?.toSorted(this.sortLogsByViewOrder)?.indexOf(this.logCursor);
            },
            temporalViewLogIndicesByLevel() {
                const temporalViewLogIndicesByLevel = this.temporalLogs.reduce((acc, item) => {
                    if (!acc[item.level]) {
                        acc[item.level] = [];
                    }
                    acc[item.level].push(item.index.toString());
                    return acc;
                }, {});
                LogUtils.levelOrLower(undefined).forEach(level => {
                    if (!temporalViewLogIndicesByLevel[level]) {
                        temporalViewLogIndicesByLevel[level] = [];
                    }
                });

                return temporalViewLogIndicesByLevel
            },
            viewTypeAwareLogIndicesByLevel() {
                return this.raw_view ? this.temporalViewLogIndicesByLevel : this.logIndicesByLevel;
            },
        },
        methods: {
            loadLogs(){
                this.executionsStore.loadLogs({
                    executionId: this.executionId,
                    params: {
                        minLevel: this.level
                    }
                })
            },
            downloadContent() {
                this.executionsStore.downloadLogs({
                    executionId: this.executionId,
                    params: {
                        minLevel: this.level
                    }
                }).then((response) => {
                    Utils.downloadUrl(window.URL.createObjectURL(new Blob([response])), this.downloadName);
                });
            },
            copyAllLogs() {
                this.executionsStore.downloadLogs({
                    executionId: this.executionId,
                    params: {
                        minLevel: this.level,
                    }
                }).then((response) => {
                    Utils.copy(response);
                });
            },
            forwardEvent(type, event) {
                this.$emit(type, event);
            },
            prevent(event) {
                event.preventDefault();
            },
            onFilterChange(filters) {
                const levelFilter = filters.find(f => f.key === "level");
                if (levelFilter) {
                    this.level = Array.isArray(levelFilter.value) ? levelFilter.value[0] : levelFilter.value;
                } else {
                    this.level = undefined;
                }
            },
            expandCollapseAll() {
                if (this.$refs.logs && this.$refs.logs.toggleExpandCollapseAll) {
                    this.$refs.logs.toggleExpandCollapseAll();
                }
            },
            toggleViewType() {
                this.logCursor = undefined;
                this.raw_view = !this.raw_view;
                localStorage.setItem(storageKeys.LOGS_VIEW_TYPE, String(this.raw_view));
            },
            sortLogsByViewOrder(a, b) {
                const aSplit = a.split("/");
                const taskRunIndexA = aSplit?.[0];
                const bSplit = b.split("/");
                const taskRunIndexB = bSplit?.[0];
                if (taskRunIndexA === undefined) {
                    return taskRunIndexB === undefined ? 0 : -1;
                }
                if (taskRunIndexB === undefined) {
                    return 1;
                }
                if (taskRunIndexA === taskRunIndexB) {
                    return this.sortLogsByViewOrder(aSplit.slice(1).join("/"), bSplit.slice(1).join("/"));
                }

                return Number.parseInt(taskRunIndexA) - Number.parseInt(taskRunIndexB);
            },
            previousLogForLevel(level) {
                const logIndicesForLevel = this.viewTypeAwareLogIndicesByLevel[level];
                if (this.logCursor === undefined) {
                    this.logCursor = logIndicesForLevel?.[logIndicesForLevel.length - 1];
                    return;
                }

                const sortedIndices = [...logIndicesForLevel, this.logCursor].filter(LibUtils.distinctFilter).sort(this.sortLogsByViewOrder);
                this.logCursor = sortedIndices?.[sortedIndices.indexOf(this.logCursor) - 1] ?? sortedIndices[sortedIndices.length - 1];
            },
            nextLogForLevel(level) {
                const logIndicesForLevel = this.viewTypeAwareLogIndicesByLevel[level];
                if (this.logCursor === undefined) {
                    this.logCursor = logIndicesForLevel?.[0];
                    return;
                }

                const sortedIndices = [...logIndicesForLevel, this.logCursor].filter(LibUtils.distinctFilter).sort(this.sortLogsByViewOrder);
                this.logCursor = sortedIndices?.[sortedIndices.indexOf(this.logCursor) + 1] ?? sortedIndices[0];
            },
            scrollToLog(index) {
                this.$refs.logScroller.scrollToItem(index);
            }
        }
    };
</script>

<style scoped lang="scss">
    @import "@kestra-io/ui-libs/src/scss/variables";
    .attempt-wrapper {
        background-color: var(--ks-background-card);

        :deep(.vue-recycle-scroller__item-view + .vue-recycle-scroller__item-view) {
            border-top: 1px solid var(--ks-border-primary);
        }

        .attempt-wrapper & {
            border-radius: .25rem;
        }
    }

    .log-lines {
        max-height: calc(100vh - 335px);
        transition: max-height 0.2s ease-out;
        margin-top: .5rem;

        .line {
            padding: .5rem;
        }
    }

    .temporal {
        .line {
            align-items: flex-start;
        }
    }

    .ks-b-group {
        min-width: auto!important;
        max-width: max-content !important;
    }

    :deep(.el-form) {
        padding: 1rem 1rem 0.5rem 1rem;
        margin-bottom: 1rem;
        border: 1px solid var(--bs-border-color);
        border-radius: 0.5rem;
        background-color: var(--ks-background-panel);
        box-shadow: 2px 3px 3px 0px var(--ks-card-shadow);
    }

    :deep(.el-form-item) {
        margin-bottom: 0.5rem !important;
    }
</style>
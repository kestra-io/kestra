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
            @filter="syncFromAppliedFilters"
        />
        <Collapse>
            <KsFormItem v-for="logLevel in currentLevelOrLower" :key="logLevel">
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
            </KsFormItem>
            <KsFormItem>
                <KsButton @click="expandCollapseAll()" :disabled="raw_view" :icon="logDisplayButtonIcon">
                    {{ logDisplayButtonText }}
                </KsButton>
            </KsFormItem>
            <KsFormItem>
                <KsTooltip
                    :content="!raw_view ? $t('logs_view.raw_details') : $t('logs_view.compact_details')"
                >
                    <KsButton @click="toggleViewType" :icon="logViewTypeButtonIcon">
                        {{ !raw_view ? $t('logs_view.raw') : $t('logs_view.compact') }}
                    </KsButton>
                </KsTooltip>
            </KsFormItem>
            <KsFormItem>
                <KsButtonGroup class="ks-b-group">
                    <Restart v-if="executionsStore.execution" :execution="executionsStore.execution" @follow="forwardEvent('follow', $event)" />
                    <KsIconButton :tooltip="$t('download logs')" @click="downloadContent()">
                        <Download />
                    </KsIconButton>
                    <KsIconButton :tooltip="$t('copy logs')" @click="copyAllLogs()">
                        <ContentCopy />
                    </KsIconButton>
                    <KsIconButton :tooltip="$t('refresh')" @click="loadLogs()">
                        <Refresh />
                    </KsIconButton>
                </KsButtonGroup>
            </KsFormItem>
        </Collapse>

        <TaskRunDetails
            v-if="!raw_view"
            ref="logs"
            :level="effectiveLevel"
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
        <KsCard v-else class="attempt-wrapper" style="--kel-card-padding: 0">
            <KsEmpty
                v-if="Array.isArray(executionsStore.logs) && temporalLogs.length === 0"
                :description="$t('no_logs_data_description')"
            />
            <DynamicScroller
                v-if="temporalLogs.length > 0"
                ref="logScroller"
                :items="temporalLogs"
                :minItemSize="50"
                keyField="uid"
                class="log-lines temporal"
                :style="{maxHeight: 'calc(100vh - 335px)', marginTop: '0.5rem'}"
                :buffer="200"
                :prerender="20"
            >
                <template #default="{item, active}">
                    <DynamicScrollerItem
                        :item="item"
                        :active="active"
                        :sizeDependencies="[item.message]"
                        :data-index="item.index"
                        :key="item.uid"
                    >
                        <LogLine
                            @click="logCursor = item.index.toString()"
                            class="line"
                            :class="{['log-bg-' + cursorLogLevel?.toLowerCase()]: cursorLogLevel === item.level, 'opacity-40': cursorLogLevel && cursorLogLevel !== item.level}"
                            :cursor="item.index.toString() === logCursor"
                            :excludeMetas="['namespace', 'flowId', 'executionId']"
                            :level="effectiveLevel"
                            :filter="filter"
                            :log="item"
                        />
                    </DynamicScrollerItem>
                </template>
            </DynamicScroller>
        </KsCard>
    </div>
</template>

<script>
    import {computed} from "vue"
    import {useLogExecutionsFilter} from "../filter/configurations"
    import TaskRunDetails from "../logs/TaskRunDetails.vue"
    import Download from "vue-material-design-icons/Download.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import UnfoldMoreHorizontal from "vue-material-design-icons/UnfoldMoreHorizontal.vue"
    import UnfoldLessHorizontal from "vue-material-design-icons/UnfoldLessHorizontal.vue"
    import ViewList from "vue-material-design-icons/ViewList.vue"
    import ViewGrid from "vue-material-design-icons/ViewGrid.vue"
    import {KsIconButton} from "@kestra-io/design-system"
    import LogLevelNavigator from "../logs/LogLevelNavigator.vue"
    import {DynamicScroller, DynamicScrollerItem} from "vue-virtual-scroller"
    import "vue-virtual-scroller/dist/vue-virtual-scroller.css"
    import Collapse from "../layout/Collapse.vue"
    import {State} from "@kestra-io/design-system"

    import * as Utils from "../../utils/utils"
    import LogLine from "../logs/LogLine.vue"
    import Restart from "./overview/components/actions/Restart.vue"
    import * as LogUtils from "../../utils/logs"
    import Refresh from "vue-material-design-icons/Refresh.vue"
    import {mapStores} from "pinia"
    import {useExecutionsStore} from "../../stores/executions"
    import {KsFilter as KSFilter} from "@kestra-io/design-system"
    import {storageKeys} from "../../utils/constants"
    import {
        hasUnsupportedRouteLevelComparator,
        normalizeRouteLevelFilter,
        readAppliedLevelFilter,
        readRouteLevelFilter,
    } from "@kestra-io/design-system"
    import {useRouteFilterPolicy} from "@kestra-io/design-system"

    function distinctFilter(value, index, array) {
        return array.indexOf(value) === index
    }

    export default {
        components: {
            LogLine,
            TaskRunDetails,
            LogLevelNavigator,
            KsIconButton,
            Download,
            ContentCopy,
            Collapse,
            Restart,
            DynamicScroller,
            DynamicScrollerItem,
            Refresh,
            KSFilter,
        },
        setup() {
            const logExecutionsFilter = useLogExecutionsFilter()
            const defaultLogLevel = computed(
                () => localStorage.getItem("defaultLogLevel") || "INFO",
            )

            const {
                routeValue: routeLevel,
                effectiveValue: effectiveLevel,
                syncFromAppliedFilters,
            } = useRouteFilterPolicy({
                defaultValue: () => defaultLogLevel.value,
                applyDefaultIfMissing: () => true,
                fallbackValue: () => "TRACE",
                readFromRoute: readRouteLevelFilter,
                writeToRoute: normalizeRouteLevelFilter,
                hasUnsupportedRouteValue: hasUnsupportedRouteLevelComparator,
                readFromAppliedFilters: readAppliedLevelFilter,
            })

            return {
                logExecutionsFilter,
                routeLevel,
                effectiveLevel,
                syncFromAppliedFilters,
            }
        },
        data() {
            return {
                fullscreen: false,
                filter: undefined,
                openedTaskrunsCount: 0,
                raw_view: (localStorage.getItem(storageKeys.LOGS_VIEW_TYPE) ?? "false").toLowerCase() === "true",
                logIndicesByLevel: Object.fromEntries(LogUtils.levelOrLower(undefined).map(level => [level, []])),
                logCursor: undefined,
                logsLoading: false,
            }
        },
        created() {
            this.filter = (this.$route.query.q || undefined)
        },
        watch:{
            "executionsStore.execution": {
                immediate: true,
                handler(execution, oldExecution) {
                    if (execution && !oldExecution && this.raw_view && !this.logsLoading && !this.executionsStore.logs?.length) {
                        this.loadLogs()
                    }
                },
            },
            routeLevel: {
                handler() {
                    if (this.raw_view && this.executionsStore.execution) {
                        this.executionsStore.logs = {total: 0, results: []}
                        this.logsLoading = false
                        this.loadLogs()
                    }
                },
            },
            logCursor(newValue) {
                if (newValue !== undefined && this.raw_view) {
                    this.scrollToLog(newValue)
                }
            },
        },
        computed: {
            State() {
                return State
            },
            temporalLogs() {
                const logResults = this.executionsStore.logs ?? []

                if (!logResults.length) {
                    return []
                }

                const filtered = logResults.filter(log => {
                    if (!this.filter) return true
                    return log.message?.toLowerCase().includes(this.filter.toLowerCase())
                })

                return filtered.map((logLine, index) => ({
                    ...logLine,
                    index,
                    uid: `${logLine.taskRunId ?? ""}-${logLine.attemptNumber ?? 0}-${logLine.timestamp}-${index}`,
                }))
            },
            ...mapStores(useExecutionsStore),
            executionId() {
                return this.executionsStore.execution.id
            },
            downloadName() {
                return `kestra-execution-${this.$moment().format("YYYYMMDDHHmmss")}-${this.executionId}.log`
            },
            logDisplayButtonText() {
                return this.openedTaskrunsCount === 0 ? this.$t("expand all") : this.$t("collapse all")
            },
            logDisplayButtonIcon() {
                return this.openedTaskrunsCount === 0 ? UnfoldMoreHorizontal : UnfoldLessHorizontal
            },
            logViewTypeButtonIcon() {
                return this.raw_view ? ViewGrid : ViewList
            },
            currentLevelOrLower() {
                return LogUtils.levelOrLower(this.routeLevel)
            },
            countByLogLevel() {
                return Object.fromEntries(Object.entries(this.viewTypeAwareLogIndicesByLevel).map(([level, indices]) => [level, indices.length]))
            },
            cursorLogLevel() {
                return Object.entries(this.viewTypeAwareLogIndicesByLevel).find(([_, indices]) => indices.includes(this.logCursor))?.[0]
            },
            cursorIdxForLevel() {
                return this.viewTypeAwareLogIndicesByLevel?.[this.cursorLogLevel]?.toSorted(this.sortLogsByViewOrder)?.indexOf(this.logCursor)
            },
            temporalViewLogIndicesByLevel() {
                const temporalViewLogIndicesByLevel = this.temporalLogs.reduce((acc, item) => {
                    if (!acc[item.level]) {
                        acc[item.level] = []
                    }
                    acc[item.level].push(item.index.toString())
                    return acc
                }, {})
                LogUtils.levelOrLower(undefined).forEach(level => {
                    if (!temporalViewLogIndicesByLevel[level]) {
                        temporalViewLogIndicesByLevel[level] = []
                    }
                })

                return temporalViewLogIndicesByLevel
            },
            viewTypeAwareLogIndicesByLevel() {
                return this.raw_view ? this.temporalViewLogIndicesByLevel : this.logIndicesByLevel
            },
        },
        methods: {
            loadLogs(){
                if (this.logsLoading) return
                this.logsLoading = true
                this.executionsStore.loadLogs({
                    executionId: this.executionId,
                    params: {
                        minLevel: this.effectiveLevel,
                    },
                }).finally(() => {
                    this.logsLoading = false
                })
            },
            downloadContent() {
                this.executionsStore.downloadLogs({
                    executionId: this.executionId,
                    params: {
                        minLevel: this.effectiveLevel,
                    },
                }).then((response) => {
                    Utils.downloadUrl(window.URL.createObjectURL(new Blob([response])), this.downloadName)
                })
            },
            copyAllLogs() {
                this.executionsStore.downloadLogs({
                    executionId: this.executionId,
                    params: {
                        minLevel: this.effectiveLevel,
                    },
                }).then((response) => {
                    Utils.copy(response)
                })
            },
            forwardEvent(type, event) {
                this.$emit(type, event)
            },
            prevent(event) {
                event.preventDefault()
            },
            expandCollapseAll() {
                if (this.$refs.logs && this.$refs.logs.toggleExpandCollapseAll) {
                    this.$refs.logs.toggleExpandCollapseAll()
                }
            },
            toggleViewType() {
                this.logCursor = undefined
                this.raw_view = !this.raw_view
                localStorage.setItem(storageKeys.LOGS_VIEW_TYPE, String(this.raw_view))
            },
            sortLogsByViewOrder(a, b) {
                const aSplit = a.split("/")
                const taskRunIndexA = aSplit?.[0]
                const bSplit = b.split("/")
                const taskRunIndexB = bSplit?.[0]
                if (taskRunIndexA === undefined) {
                    return taskRunIndexB === undefined ? 0 : -1
                }
                if (taskRunIndexB === undefined) {
                    return 1
                }
                if (taskRunIndexA === taskRunIndexB) {
                    return this.sortLogsByViewOrder(aSplit.slice(1).join("/"), bSplit.slice(1).join("/"))
                }

                return Number.parseInt(taskRunIndexA) - Number.parseInt(taskRunIndexB)
            },
            previousLogForLevel(level) {
                const logIndicesForLevel = this.viewTypeAwareLogIndicesByLevel[level]
                if (this.logCursor === undefined) {
                    this.logCursor = logIndicesForLevel?.[logIndicesForLevel.length - 1]
                    return
                }

                const sortedIndices = [...logIndicesForLevel, this.logCursor].filter(distinctFilter).sort(this.sortLogsByViewOrder)
                this.logCursor = sortedIndices?.[sortedIndices.indexOf(this.logCursor) - 1] ?? sortedIndices[sortedIndices.length - 1]
            },
            nextLogForLevel(level) {
                const logIndicesForLevel = this.viewTypeAwareLogIndicesByLevel[level]
                if (this.logCursor === undefined) {
                    this.logCursor = logIndicesForLevel?.[0]
                    return
                }

                const sortedIndices = [...logIndicesForLevel, this.logCursor].filter(distinctFilter).sort(this.sortLogsByViewOrder)
                this.logCursor = sortedIndices?.[sortedIndices.indexOf(this.logCursor) + 1] ?? sortedIndices[0]
            },
            scrollToLog(index) {
                this.$refs.logScroller?.scrollToItem(index)
            },
        },
    }
</script>

<style scoped lang="scss">
        .attempt-wrapper {
        background-color: var(--ks-bg-surface);

        :deep(.vue-recycle-scroller__item-view + .vue-recycle-scroller__item-view) {
            border-top: 1px solid var(--ks-border-default);
        }

        .attempt-wrapper & {
            border-radius: .25rem;
        }
    }

    .log-lines {
        .line {
            padding: .5rem;
        }

        :deep(.vue-recycle-scroller__item-view > div) {
            min-height: 2rem;
        }
    }

    .log-lines.temporal {
        .line {
            align-items: flex-start;
        }
    }

    .ks-b-group {
        min-width: auto!important;
        max-width: max-content !important;
    }

    :deep(.kel-form) {
        padding: 1rem 1rem 0.5rem 1rem;
        margin-bottom: 1rem;
        border: 1px solid var(--ks-border-default);
        border-radius: 0.5rem;
        background-color: var(--ks-bg-surface);
        box-shadow: 2px 3px 3px 0px var(--ks-shadow-element);
    }

    :deep(.kel-form-item) {
        margin-bottom: 0.5rem !important;
    }
</style>


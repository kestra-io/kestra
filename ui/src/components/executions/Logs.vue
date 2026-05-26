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
                    :content="!raw_view ? t('logs_view.raw_details') : t('logs_view.compact_details')"
                >
                    <KsButton @click="toggleViewType" :icon="logViewTypeButtonIcon">
                        {{ !raw_view ? t('logs_view.raw') : t('logs_view.compact') }}
                    </KsButton>
                </KsTooltip>
            </KsFormItem>
            <KsFormItem>
                <KsButtonGroup class="ks-b-group">
                    <Restart v-if="executionsStore.execution" :execution="executionsStore.execution" @follow="emit('follow', $event)" />
                    <KsIconButton :tooltip="t('download logs')" @click="downloadContent()">
                        <Download />
                    </KsIconButton>
                    <KsIconButton :tooltip="t('copy logs')" @click="copyAllLogs()">
                        <ContentCopy />
                    </KsIconButton>
                    <KsIconButton :tooltip="t('refresh')" @click="loadLogs()">
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
            @follow="emit('follow', $event)"
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
                <template #default="{item: rawItem, active}: {item: Record<string, any>, active: boolean}">
                    <DynamicScrollerItem
                        :item="rawItem"
                        :active="active"
                        :sizeDependencies="[rawItem.message]"
                        :data-index="rawItem.index"
                        :key="rawItem.uid"
                    >
                        <LogLine
                            @click="logCursor = rawItem.index.toString()"
                            class="line"
                            :class="{['log-bg-' + cursorLogLevel?.toLowerCase()]: cursorLogLevel === rawItem.level, 'opacity-40': cursorLogLevel && cursorLogLevel !== rawItem.level}"
                            :cursor="rawItem.index.toString() === logCursor"
                            :excludeMetas="['namespace', 'flowId', 'executionId']"
                            :level="(effectiveLevel as any)"
                            :filter="filter"
                            :log="(rawItem as any)"
                        />
                    </DynamicScrollerItem>
                </template>
            </DynamicScroller>
        </KsCard>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, watch, useTemplateRef} from "vue"
    import {useRoute} from "vue-router"
    import {useI18n} from "vue-i18n"
    import moment from "moment"
    import {useLogExecutionsFilter} from "../filter/configurations"
    import TaskRunDetails, {LOG_LEVEL} from "../logs/TaskRunDetails.vue"
    import Download from "vue-material-design-icons/Download.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import UnfoldMoreHorizontal from "vue-material-design-icons/UnfoldMoreHorizontal.vue"
    import UnfoldLessHorizontal from "vue-material-design-icons/UnfoldLessHorizontal.vue"
    import ViewList from "vue-material-design-icons/ViewList.vue"
    import ViewGrid from "vue-material-design-icons/ViewGrid.vue"
    import {KsIconButton, KsFilter as KSFilter} from "@kestra-io/design-system"
    import LogLevelNavigator from "../logs/LogLevelNavigator.vue"
    import {DynamicScroller, DynamicScrollerItem} from "vue-virtual-scroller"
    import "vue-virtual-scroller/dist/vue-virtual-scroller.css"
    import Collapse from "../layout/Collapse.vue"
    import * as Utils from "../../utils/utils"
    import LogLine from "../logs/LogLine.vue"
    import Restart from "./overview/components/actions/Restart.vue"
    import * as LogUtils from "../../utils/logs"
    import Refresh from "vue-material-design-icons/Refresh.vue"
    import {useExecutionsStore} from "../../stores/executions"
    import {storageKeys} from "../../utils/constants"
    import {
        hasUnsupportedRouteLevelComparator,
        normalizeRouteLevelFilter,
        readAppliedLevelFilter,
        readRouteLevelFilter,
        useRouteFilterPolicy,
    } from "@kestra-io/design-system"

    function distinctFilter(value: unknown, index: number, array: unknown[]) {
        return array.indexOf(value) === index
    }

    const emit = defineEmits<{
        follow: [unknown]
    }>()

    const {t} = useI18n({useScope: "global"})
    const route = useRoute()
    const executionsStore = useExecutionsStore()

    const logExecutionsFilter = useLogExecutionsFilter()
    const defaultLogLevel = computed<LOG_LEVEL>(
        () => localStorage.getItem("defaultLogLevel") as LOG_LEVEL || "INFO",
    )

    const {
        routeValue: routeLevel,
        effectiveValue: effectiveLevel,
        syncFromAppliedFilters,
    } = useRouteFilterPolicy<LOG_LEVEL>({
        defaultValue: () => defaultLogLevel.value,
        applyDefaultIfMissing: () => true,
        fallbackValue: () => "TRACE",
        readFromRoute: readRouteLevelFilter,
        writeToRoute: normalizeRouteLevelFilter,
        hasUnsupportedRouteValue: hasUnsupportedRouteLevelComparator,
        readFromAppliedFilters: readAppliedLevelFilter,
    })

    const filter = ref<string | undefined>(route.query.q as string | undefined || undefined)
    const openedTaskrunsCount = ref(0)
    const raw_view = ref((localStorage.getItem(storageKeys.LOGS_VIEW_TYPE) ?? "false").toLowerCase() === "true")
    const logIndicesByLevel = ref<Record<string, string[]>>(
        Object.fromEntries(LogUtils.levelOrLower(undefined as any).map((level: string) => [level, []])),
    )
    const logCursor = ref<string | undefined>(undefined)
    const logsLoading = ref(false)

    const logs = useTemplateRef<InstanceType<typeof TaskRunDetails>>("logs")
    const logScroller = useTemplateRef<{ scrollToItem: (index: number) => void }>("logScroller")

    const executionId = computed(() => executionsStore.execution?.id)

    const downloadName = computed(() =>
        `kestra-execution-${moment().format("YYYYMMDDHHmmss")}-${executionId.value}.log`,
    )

    const temporalLogs = computed(() => {
        const logResults: Array<Record<string, any>> = (executionsStore.logs as any)?.results ?? []

        if (!logResults.length) {
            return []
        }

        const filtered = logResults.filter((log: Record<string, any>) => {
            if (!filter.value) return true
            return log.message?.toLowerCase().includes(filter.value.toLowerCase())
        })

        return filtered.map((logLine: Record<string, any>, index: number) => ({
            ...logLine,
            index,
            uid: `${logLine.taskRunId ?? ""}-${logLine.attemptNumber ?? 0}-${logLine.timestamp}-${index}`,
        }))
    })

    const logDisplayButtonText = computed(() =>
        openedTaskrunsCount.value === 0 ? t("expand all") : t("collapse all"),
    )

    const logDisplayButtonIcon = computed(() =>
        openedTaskrunsCount.value === 0 ? UnfoldMoreHorizontal : UnfoldLessHorizontal,
    )

    const logViewTypeButtonIcon = computed(() =>
        raw_view.value ? ViewGrid : ViewList,
    )

    const currentLevelOrLower = computed(() =>
        LogUtils.levelOrLower(routeLevel.value as any),
    )

    const countByLogLevel = computed(() =>
        Object.fromEntries(
            Object.entries(viewTypeAwareLogIndicesByLevel.value).map(([level, indices]) => [level, (indices as unknown[]).length]),
        ),
    )

    const cursorLogLevel = computed(() =>
        Object.entries(viewTypeAwareLogIndicesByLevel.value).find(([_, indices]) =>
            (indices as string[]).includes(logCursor.value!),
        )?.[0],
    )

    const cursorIdxForLevel = computed(() =>
        (viewTypeAwareLogIndicesByLevel.value as Record<string, string[]>)?.[cursorLogLevel.value!]
            ?.toSorted(sortLogsByViewOrder)
            ?.indexOf(logCursor.value!),
    )

    const temporalViewLogIndicesByLevel = computed(() => {
        const result: Record<string, string[]> = temporalLogs.value.reduce((acc: Record<string, string[]>, item: Record<string, any>) => {
            if (!acc[item.level]) {
                acc[item.level] = []
            }
            acc[item.level].push(item.index.toString())
            return acc
        }, {})
        LogUtils.levelOrLower(undefined as any).forEach((level: string) => {
            if (!result[level]) {
                result[level] = []
            }
        })
        return result
    })

    const viewTypeAwareLogIndicesByLevel = computed(() =>
        raw_view.value ? temporalViewLogIndicesByLevel.value : logIndicesByLevel.value,
    )

    watch(() => executionsStore.execution, (execution, oldExecution) => {
        if (execution && !oldExecution && raw_view.value && !logsLoading.value && !executionsStore.logs?.total) {
            loadLogs()
        }
    }, {immediate: true})

    watch(routeLevel, () => {
        if (raw_view.value && executionsStore.execution) {
            executionsStore.logs = {total: 0, results: []} as any
            logsLoading.value = false
            loadLogs()
        }
    })

    watch(logCursor, (newValue) => {
        if (newValue !== undefined && raw_view.value) {
            scrollToLog(newValue)
        }
    })

    function loadLogs() {
        if (logsLoading.value) return
        logsLoading.value = true
        executionsStore.loadLogs({
            executionId: executionId.value!,
            params: {
                minLevel: effectiveLevel.value,
            },
        }).finally(() => {
            logsLoading.value = false
        })
    }

    function downloadContent() {
        executionsStore.downloadLogs({
            executionId: executionId.value!,
            params: {
                minLevel: effectiveLevel.value,
            },
        }).then((response: string) => {
            Utils.downloadUrl(window.URL.createObjectURL(new Blob([response])), downloadName.value)
        })
    }

    function copyAllLogs() {
        executionsStore.downloadLogs({
            executionId: executionId.value!,
            params: {
                minLevel: effectiveLevel.value,
            },
        }).then((response: string) => {
            Utils.copy(response)
        })
    }

    function expandCollapseAll() {
        if (logs.value && (logs.value as any).toggleExpandCollapseAll) {
            (logs.value as any).toggleExpandCollapseAll()
        }
    }

    function toggleViewType() {
        logCursor.value = undefined
        raw_view.value = !raw_view.value
        localStorage.setItem(storageKeys.LOGS_VIEW_TYPE, String(raw_view.value))
    }

    function sortLogsByViewOrder(a: string, b: string): number {
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
            return sortLogsByViewOrder(aSplit.slice(1).join("/"), bSplit.slice(1).join("/"))
        }

        return Number.parseInt(taskRunIndexA) - Number.parseInt(taskRunIndexB)
    }

    function previousLogForLevel(level: string) {
        const logIndicesForLevel = (viewTypeAwareLogIndicesByLevel.value as Record<string, string[]>)[level]
        if (logCursor.value === undefined) {
            logCursor.value = logIndicesForLevel?.[logIndicesForLevel.length - 1]
            return
        }

        const sortedIndices = [...logIndicesForLevel, logCursor.value].filter(distinctFilter as (v: string, i: number, a: string[]) => boolean).sort(sortLogsByViewOrder)
        logCursor.value = sortedIndices?.[sortedIndices.indexOf(logCursor.value) - 1] ?? sortedIndices[sortedIndices.length - 1]
    }

    function nextLogForLevel(level: string) {
        const logIndicesForLevel = (viewTypeAwareLogIndicesByLevel.value as Record<string, string[]>)[level]
        if (logCursor.value === undefined) {
            logCursor.value = logIndicesForLevel?.[0]
            return
        }

        const sortedIndices = [...logIndicesForLevel, logCursor.value].filter(distinctFilter as (v: string, i: number, a: string[]) => boolean).sort(sortLogsByViewOrder)
        logCursor.value = sortedIndices?.[sortedIndices.indexOf(logCursor.value) + 1] ?? sortedIndices[0]
    }

    function scrollToLog(index: string) {
        logScroller.value?.scrollToItem(Number(index))
    }
</script>

<style scoped lang="scss">
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
        border: 1px solid var(--ks-border-primary);
        border-radius: 0.5rem;
        background-color: var(--ks-background-panel);
        box-shadow: 2px 3px 3px 0px var(--ks-card-shadow);
    }

    :deep(.kel-form-item) {
        margin-bottom: 0.5rem !important;
    }
</style>

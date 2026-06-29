<template>
    <DynamicScroller
        v-if="followedExecution && currentTaskRuns.length > 0"
        ref="taskRunScroller"
        :items="currentTaskRuns"
        :minItemSize="50"
        keyField="id"
        class="log-wrapper"
    >
        <template
            #default="{
                item: currentTaskRun,
                index: currentTaskRunIndex,
                active: isTaskRunActive,
            }"
        >
            <DynamicScrollerItem
                v-if="uniqueTaskRunDisplayFilter(asTaskRun(currentTaskRun))"
                :item="asTaskRun(currentTaskRun)"
                :active="isTaskRunActive"
                :data-index="currentTaskRunIndex"
            >
                <KsCard class="attempt-wrapper" shadow="never" :class="{'attempt-wrapper--transparent': hideTaskHeader}">
                    <TaskRunLine
                        :currentTaskRun="currentTaskRun"
                        :depth="asTaskRun(currentTaskRun).depth"
                        :followedExecution="followedExecution"
                        :flow="flow"
                        :forcedAttemptNumber="forcedAttemptNumber"
                        :taskRunId="taskRunId"
                        :selectedAttemptNumberByTaskRunId="
                            selectedAttemptNumberByTaskRunId
                        "
                        :shownAttemptsUid="shownAttemptsUid"
                        :logs="filteredLogs"
                        :hideHeader="hideTaskHeader"
                        @toggle-show-attempt="toggleShowAttempt"
                        @swap-displayed-attempt="swapDisplayedAttempt"
                        @update-logs="loadLogs"
                    >
                        <template #buttons>
                            <div id="buttons" />
                        </template>
                    </TaskRunLine>
                    <DynamicScroller
                        v-if="shouldDisplayLogs(currentTaskRun)"
                        :items="
                            displayItemsByAttemptUid[
                                attemptUid(
                                    asTaskRun(currentTaskRun).id,
                                    selectedAttemptNumberByTaskRunId[
                                        asTaskRun(currentTaskRun).id
                                    ],
                                )
                            ] ?? []
                        "
                        :minItemSize="32"
                        keyField="index"
                        class="log-lines"
                        :class="{'single-line': currentTaskRuns.length === 1}"
                        :ref="
                            (el) =>
                                logsScrollerRef(
                                    el,
                                    currentTaskRunIndex,
                                    attemptUid(
                                        asTaskRun(currentTaskRun).id,
                                        selectedAttemptNumberByTaskRunId[
                                            asTaskRun(currentTaskRun).id
                                        ],
                                    ),
                                )
                        "
                        @resize="scrollToBottomFailedTask"
                    >
                        <template #default="{item, active}">
                            <DynamicScrollerItem
                                :item="item"
                                :active="active"
                                :sizeDependencies="[item.message, item.image, item.isGroup, item.isGroup && isGroupExpanded(currentTaskRunIndex, item)]"
                                :data-index="item.index"
                            >
                                <template v-if="item.isGroup">
                                    <LogLine
                                        v-for="member in (isGroupExpanded(currentTaskRunIndex, item) ? item.members : item.members.slice(0, 1))"
                                        :key="member.index"
                                        class="line"
                                        :cursor="logCursor === `${currentTaskRunIndex}/${member.index}`"
                                        :class="{
                                            ['log-bg-' + levelToHighlight?.toLowerCase()]: levelToHighlight === member.level,
                                            'opacity-40': levelToHighlight && levelToHighlight !== member.level,
                                        }"
                                        :level="level as any"
                                        :log="member"
                                        :excludeMetas="excludeMetas as any"
                                    />
                                    <button
                                        type="button"
                                        class="log-group-more"
                                        :style="{borderLeftColor: `var(--ks-log-border-${item.level.toLowerCase()})`, fontSize: `${logsFontSize}px`}"
                                        :aria-expanded="isGroupExpanded(currentTaskRunIndex, item)"
                                        @click="toggleGroup(currentTaskRunIndex, item)"
                                    >
                                        <KsIcon class="log-group-chevron" :class="{collapsed: !isGroupExpanded(currentTaskRunIndex, item)}" size="s">
                                            <ChevronDown />
                                        </KsIcon>
                                        <span class="log-group-count">×{{ item.members.length }}</span>
                                        <span class="log-group-label">{{ isGroupExpanded(currentTaskRunIndex, item) ? t("collapse") : t("similar lines") }}</span>
                                    </button>
                                </template>
                                <template v-else>
                                    <Teleport v-if="item.logFile" to="#buttons">
                                        <KsButtonGroup class="line">
                                            <KsButton
                                                type="primary"
                                                tag="a"
                                                :href="fileUrl(item.logFile)"
                                                target="_blank"
                                                size="small"
                                                :icon="Download"
                                                rel="noopener noreferrer"
                                            >
                                                {{ t("download") }}
                                            </KsButton>
                                            <FilePreview
                                                :value="item.logFile"
                                                :executionId="followedExecution.id"
                                            />
                                            <KsButton
                                                disabled
                                                size="small"
                                                type="primary"
                                                v-if="
                                                    logFileSizeByPath[item.logFile]
                                                "
                                            >
                                                ({{
                                                    logFileSizeByPath[item.logFile]
                                                }})
                                            </KsButton>
                                        </KsButtonGroup>
                                    </Teleport>
                                    <LogLine
                                        class="line"
                                        :cursor="
                                            logCursor ===
                                                `${currentTaskRunIndex}/${item.index}`
                                        "
                                        :class="{
                                            ['log-bg-' +
                                                levelToHighlight?.toLowerCase()]:
                                                    levelToHighlight === item.level,
                                            'opacity-40':
                                                levelToHighlight &&
                                                levelToHighlight !== item.level,
                                        }"
                                        :key="item.index"
                                        :level="level as any"
                                        :log="item"
                                        :excludeMetas="excludeMetas as any"
                                        v-else-if="
                                            filter === '' ||
                                                item.message
                                                    ?.toLowerCase()
                                                    .includes(filter.toLowerCase())
                                        "
                                    />
                                    <TaskRunDetails
                                        v-if="
                                            !taskRunId &&
                                                isSubflow(currentTaskRun) &&
                                                shouldDisplaySubflow(
                                                    item.index,
                                                    currentTaskRun,
                                                ) &&
                                                asTaskRun(currentTaskRun).outputs?.executionId
                                        "
                                        :ref="
                                            (el) =>
                                                subflowTaskRunDetailsRef(
                                                    el,
                                                    currentTaskRunIndex +
                                                        '/' +
                                                        item.index,
                                                )
                                        "
                                        :logCursor="
                                            logCursor
                                                ?.split('/')
                                                ?.slice(2)
                                                .join('/')
                                        "
                                        @log-cursor="
                                            emitLogCursor(
                                                currentTaskRunIndex +
                                                    '/' +
                                                    item.index +
                                                    '/' +
                                                    $event,
                                            )
                                        "
                                        @log-indices-by-level="
                                            childLogIndicesByLevel(
                                                currentTaskRunIndex,
                                                item.index,
                                                $event,
                                            )
                                        "
                                        :levelToHighlight="levelToHighlight"
                                        :level="level as any"
                                        :excludeMetas="[
                                            'namespace',
                                            'flowId',
                                            'taskId',
                                            'executionId',
                                        ]"
                                        :filter="filter"
                                        :allowAutoExpandSubflows="false"
                                        :targetExecutionId="
                                            asTaskRun(currentTaskRun).outputs?.executionId
                                        "
                                        :class="
                                            $el.classList.contains('even')
                                                ? ''
                                                : 'even'
                                        "
                                        :showProgressBar="showProgressBar"
                                        :showLogs="showLogs"
                                    />
                                </template>
                            </DynamicScrollerItem>
                        </template>
                    </DynamicScroller>
                </KsCard>
                <div
                    v-if="taskType(currentTaskRun) === 'io.kestra.plugin.core.flow.Loop' && isTaskRunActive"
                    style="display:flex; align-items: center; gap: 10px; margin: 12px 0"
                >
                    <KsButton
                        :tag="RouterLink"
                        :to="{
                            name: 'executions/list',
                            query: {
                                'filters[parentId][EQUALS]': asTaskRun(currentTaskRun).executionId,
                                'filters[kind][EQUALS]': 'LOOP',
                            }
                        }"
                        size="small"
                    >
                        Iterations
                    </KsButton>
                    <KsProgress
                        :percentage="Math.ceil((loopOutputsByTaskRunId[asTaskRun(currentTaskRun).id]?.terminatedIterations ?? 0) / (loopOutputsByTaskRunId[asTaskRun(currentTaskRun).id]?.iterationCount ?? 1) * 100)"
                        :strokeWidth="7"
                        :radius="81"
                        class="progress-bar"
                    >
                        <span>{{ loopOutputsByTaskRunId[asTaskRun(currentTaskRun).id]?.terminatedIterations ?? 0 }} / {{ loopOutputsByTaskRunId[asTaskRun(currentTaskRun).id]?.iterationCount ?? '?' }}</span>
                    </KsProgress>
                </div>
            </DynamicScrollerItem>
        </template>
    </DynamicScroller>
</template>

<script setup lang="ts">
    import {computed, ref, watch, onMounted, onBeforeUnmount, nextTick, useTemplateRef} from "vue"
    import {logsFontSize} from "../../composables/useLogDisplay"
    import {useI18n} from "vue-i18n"
    import {RouterLink} from "vue-router"
    import Download from "vue-material-design-icons/Download.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import * as OutputsAPI from "@kestra-io/kestra-sdk/outputs"
    import LogLine from "./LogLine.vue"
    import {State, levelToRequestParams, KsProgress, type LevelFilterValue} from "@kestra-io/design-system"
    import _xor from "lodash/xor"
    import _groupBy from "lodash/groupBy"
    import moment from "moment"
    import "vue-virtual-scroller/dist/vue-virtual-scroller.css"
    import {logDisplayTypes} from "../../utils/constants"
    import {DynamicScroller, DynamicScrollerItem} from "vue-virtual-scroller"
    import {useCoreStore} from "../../stores/core"
    import {useExecutionsStore} from "../../stores/executions"
    import TaskRunLine from "../executions/TaskRunLine.vue"
    import * as FlowUtils from "../../utils/flowUtils"
    import FilePreview from "../executions/FilePreviewDrawer.vue"
    import {apiUrl} from "override/utils/route"
    import * as Utils from "../../utils/utils"
    import * as LogUtils from "../../utils/logs"
    import throttle from "lodash/throttle"
    import {useClient, type TaskRun, type TaskRunAttempt} from "@kestra-io/kestra-sdk"

    // Recursive component - self reference
    import TaskRunDetails from "./TaskRunDetails.vue"

    const {t} = useI18n()

    const $http = useClient()

    // The UI taskrun carries a computed `depth` (for nesting) and subflow `outputs`,
    // neither of which the SDK TaskRun type models.
    type TaskRunWithDepth = TaskRun & { depth: number; outputs?: Record<string, any> }

    // Cast helper for DynamicScroller slot items which lose type info
    function asTaskRun(item: unknown): TaskRunWithDepth {
        return item as TaskRunWithDepth
    }

    const coreStore = useCoreStore()
    const executionsStore = useExecutionsStore()

    // Props
    interface Props {
        logCursor?: string
        levelToHighlight?: string
        levelFilter?: LevelFilterValue
        filter?: string
        taskRunId?: string
        excludeMetas?: string[]
        forcedAttemptNumber?: number
        targetExecutionId?: string
        targetFlow?: any // FIXME: any
        allowAutoExpandSubflows?: boolean
        showProgressBar?: boolean
        level?: string
        showLogs?: boolean
        hideTaskHeader?: boolean
    }

    const props = withDefaults(defineProps<Props>(), {
        logCursor: undefined,
        levelToHighlight: undefined,
        levelFilter: () => ({value: "INFO", direction: "min" as const}),
        filter: "",
        taskRunId: undefined,
        excludeMetas: () => [],
        forcedAttemptNumber: undefined,
        targetExecutionId: undefined,
        targetFlow: undefined,
        allowAutoExpandSubflows: true,
        showProgressBar: true,
        level: undefined,
        showLogs: undefined,
        hideTaskHeader: false,
    })

    const emit = defineEmits<{
        "opened-taskruns-count": [count: number]
        follow: [event: unknown]
        "reset-expand-collapse-all-switch": []
        "log-cursor": [cursor: string]
        "log-indices-by-level": [indices: Record<string, string[]>]
    }>()

    // Reactive state
    const shownAttemptsUid = ref<string[]>([])
    const rawLogs = ref<any[]>([]) // FIXME: any
    const timer = ref<ReturnType<typeof moment> | undefined>(undefined)
    const timeout = ref<ReturnType<typeof setTimeout> | undefined>(undefined)
    const selectedAttemptNumberByTaskRunId = ref<Record<string, number>>({})
    const executionSSE = ref<any>(undefined) // FIXME: any
    const logsSSE = ref<any>(undefined) // FIXME: any
    const flow = ref<any>(undefined) // FIXME: any
    const logsBuffer = ref<any[]>([]) // FIXME: any
    const shownSubflowsIds = ref<{subflowExecutionId: string; taskRunIndex: number}[]>([])
    const logFileSizeByPath = ref<Record<string, string>>({})
    const childrenLogIndicesByLevelByChildUid = ref<Record<string, Record<string, string[]>>>({})
    const logsScrollerRefs = ref<Record<string | number, any>>({}) // FIXME: any
    const subflowTaskRunDetailsRefs = ref<Record<string, any>>({}) // FIXME: any
    const throttledExecutionUpdate = ref<ReturnType<typeof throttle> | undefined>(undefined)
    const targetExecution = ref<any>(undefined) // FIXME: any
    const loopOutputsByTaskRunId = ref<Record<string, any>>({}) // FIXME: any

    // Template ref
    const taskRunScroller = useTemplateRef<any>("taskRunScroller") // FIXME: any

    // Computed
    const followedExecution = computed(() =>
        props.targetExecutionId === undefined
            ? executionsStore.execution
            : targetExecution.value,
    )

    const currentTaskRuns = computed<TaskRunWithDepth[]>(() => {
        const taskRunList: TaskRun[] = followedExecution.value?.taskRunList ?? []

        if (props.taskRunId) {
            return taskRunList
                .filter((tr) => tr.id === props.taskRunId)
                .map((tr) => ({...tr, depth: 0}))
        }

        // Order taskruns parent → child and annotate depth, so dynamically-generated taskruns
        // (e.g. each Ansible play/task) render indented under their parent taskrun.
        const byId: Record<string, TaskRun> = Object.fromEntries(taskRunList.map((tr) => [tr.id, tr]))
        const childrenByParent: Record<string, TaskRun[]> = {}
        const roots: TaskRun[] = []
        for (const tr of taskRunList) {
            if (tr.parentTaskRunId && byId[tr.parentTaskRunId]) {
                (childrenByParent[tr.parentTaskRunId] ??= []).push(tr)
            } else {
                roots.push(tr)
            }
        }

        const ordered: TaskRunWithDepth[] = []
        const walk = (nodes: TaskRun[], depth: number) => {
            for (const node of nodes) {
                ordered.push({...node, depth})
                const children = childrenByParent[node.id]
                if (children) {
                    walk(children, depth + 1)
                }
            }
        }
        walk(roots, 0)
        return ordered
    })

    const taskRunById = computed(() =>
        Object.fromEntries(
            currentTaskRuns.value.map((taskRun) => [taskRun.id, taskRun]),
        ),
    )

    const logsWithIndexByAttemptUid = computed(() => {
        const logFilesWrappers = currentTaskRuns.value.flatMap((taskRun) =>
            attempts(taskRun)
                .filter((attempt) => attempt.logFile !== undefined)
                .map((attempt, attemptNumber: number) => ({
                    logFile: attempt.logFile,
                    taskRunId: taskRun.id,
                    attemptNumber,
                })),
        )

        logFilesWrappers.forEach((logFileWrapper: any) => // FIXME: any
            fetchAndStoreLogFileSize(logFileWrapper.logFile),
        )

        const indexedLogs = [...filteredLogs.value, ...logFilesWrappers]
            .filter(
                (logLine: any) => // FIXME: any
                    logLine.logFile !== undefined ||
                    props.filter === "" ||
                    logLine?.message
                        .toLowerCase()
                        .includes(props.filter.toLowerCase()) ||
                    isSubflow(taskRunById.value[logLine.taskRunId]),
            )
            .map((logLine: any, index: number) => ({...logLine, index})) // FIXME: any

        return _groupBy(indexedLogs, (indexedLog: any) => // FIXME: any
            attemptUid(indexedLog.taskRunId, indexedLog.attemptNumber),
        )
    })

    const expandedGroups = ref<Set<string>>(new Set())

    watch(() => props.filter, () => {
        expandedGroups.value = new Set()
    })

    function isCollapsibleLine(item: any): boolean { // FIXME: any
        return !!item.message
            && item.logFile === undefined
            && item.level !== "ERROR"
            && item.level !== "WARN"
            && !isSubflow(taskRunById.value[item.taskRunId])
    }

    function buildDisplayItems(items: any[]): any[] { // FIXME: any
        const result: any[] = []
        let run: any[] = []
        let runKey: string | null = null
        const flushRun = () => {
            if (run.length >= LogUtils.COLLAPSE_THRESHOLD) {
                result.push({isGroup: true, index: run[0].index, level: run[0].level, members: run})
            } else {
                result.push(...run)
            }
            run = []
            runKey = null
        }
        for (const item of items) {
            if (!isCollapsibleLine(item)) {
                flushRun()
                result.push(item)
                continue
            }
            const key = LogUtils.normalizeLogTemplate(item.message)
            if (run.length && runKey !== key) {
                flushRun()
            }
            if (!run.length) {
                runKey = key
            }
            run.push(item)
        }
        flushRun()
        return result
    }

    const displayItemsByAttemptUid = computed(() => {
        const source = logsWithIndexByAttemptUid.value
        const result: Record<string, any[]> = {}
        for (const uid in source) {
            result[uid] = buildDisplayItems(source[uid])
        }
        return result
    })

    function groupKey(taskRunIndex: number, item: any): string { // FIXME: any
        return `${taskRunIndex}:${item.index}`
    }

    function isGroupExpanded(taskRunIndex: number, item: any): boolean { // FIXME: any
        return expandedGroups.value.has(groupKey(taskRunIndex, item))
    }

    function toggleGroup(taskRunIndex: number, item: any) { // FIXME: any
        const key = groupKey(taskRunIndex, item)
        const next = new Set(expandedGroups.value)
        if (next.has(key)) {
            next.delete(key)
        } else {
            next.add(key)
        }
        expandedGroups.value = next
    }

    const autoExpandTaskRunStates = computed(() => {
        switch (
            localStorage.getItem("logDisplay") ||
            logDisplayTypes.DEFAULT
        ) {
        case logDisplayTypes.ERROR:
            return [State.FAILED, State.RUNNING, State.PAUSED]
        case logDisplayTypes.ALL:
            return State.arrayAllStates().map((s: any) => s.name) // FIXME: any
        case logDisplayTypes.HIDDEN:
            return []
        default:
            return State.arrayAllStates().map((s: any) => s.name) // FIXME: any
        }
    })

    const currentTaskRunsLogIndicesByLevel = computed(() =>
        currentTaskRuns.value.reduce(
            (indicesByLevel: Record<string, string[]>, taskRun, taskRunIndex: number) => {
                if (shouldDisplayLogs(taskRun)) {
                    const currentTaskRunLogs =
                        logsWithIndexByAttemptUid.value[
                            attemptUid(
                                taskRun.id,
                                selectedAttemptNumberByTaskRunId.value[taskRun.id],
                            )
                        ]
                    currentTaskRunLogs?.forEach((log: any) => { // FIXME: any
                        ;(indicesByLevel[log.level] ??= []).push(
                            taskRunIndex + "/" + log.index,
                        )
                    })
                }
                return indicesByLevel
            },
            {},
        ),
    )

    const allLogIndicesByLevel = computed(() => {
        const current = {...currentTaskRunsLogIndicesByLevel.value}
        return Object.entries(
            childrenLogIndicesByLevelByChildUid.value,
        ).reduce(
            (allLogIndices: Record<string, string[]>, [logUid, childrenLogIndicesByLevel]: [string, Record<string, string[]>]) => {
                Object.entries(childrenLogIndicesByLevel).forEach(
                    ([lvl, logIndices]) => {
                        const bucket = (allLogIndices[lvl] ??= [])
                        for (const logIndex of logIndices) {
                            bucket.push(logUid + "/" + logIndex)
                        }
                    },
                )
                return allLogIndices
            },
            current,
        )
    })

    const levelOrLower = computed(() =>
        LogUtils.levelOrLower(props.level as any), // FIXME: any
    )

    const filteredLogs = computed(() =>
        rawLogs.value.filter((log: any) => // FIXME: any
            levelOrLower.value.includes(log.level),
        ),
    )

    // Watchers
    watch(
        () => shownAttemptsUid.value.length,
        (openedTaskrunsCount) => {
            emit("opened-taskruns-count", openedTaskrunsCount)
        },
    )

    watch(
        () => props.levelFilter,
        () => {
            rawLogs.value = []
            if (!followedExecution.value) return

            if (logsSSE.value && State.isRunning(followedExecution.value.state.current)) {
                logsBuffer.value = []
                closeLogsSSE()
                followLogs(followedExecution.value.id)
            } else {
                loadLogs(followedExecution.value.id)
            }
        },
    )

    watch(
        currentTaskRuns,
        (taskRuns) => {
            // by default we preselect the last attempt for each task run
            selectedAttemptNumberByTaskRunId.value = Object.fromEntries(
                taskRuns.map((taskRun) => [
                    taskRun.id,
                    props.forcedAttemptNumber ??
                        attempts(taskRun).length - 1,
                ]),
            )
            autoExpandBasedOnSettings()
        },
        {immediate: true, deep: true},
    )

    watch(
        () => props.targetFlow,
        (flowSource) => {
            if (flowSource) {
                flow.value = flowSource
            }
        },
        {immediate: true},
    )

    watch(
        followedExecution,
        async (newExecution, oldExecution) => {
            if (!newExecution) {
                return
            }

            if (!oldExecution) {
                nextTick(() => {
                    const parentScroller =
                        (taskRunScroller.value as any)?.$el?.parentNode?.closest( // FIXME: any
                            ".vue-recycle-scroller",
                        )
                    if (parentScroller) {
                        const scrollerStyles =
                            window.getComputedStyle(parentScroller)
                        ;(taskRunScroller.value as any).$el.style.maxHeight = `${parseFloat(scrollerStyles.getPropertyValue("max-height")) - parentScroller.clientHeight}px` // FIXME: any
                    }
                })
            }

            if (!props.targetFlow) {
                flow.value = await executionsStore.loadFlowForExecution(
                    {
                        namespace: newExecution.namespace,
                        flowId: newExecution.flowId,
                        revision: newExecution.flowRevision,
                        store: false,
                    },
                )
            }

            for (const taskRun of currentTaskRuns.value) {
                if (taskType(taskRun) === "io.kestra.plugin.core.flow.Loop") {
                    updateLoopStatus(taskRun.id)
                }
            }

            if (!State.isRunning(followedExecution.value.state.current)) {
                // wait a bit to make sure we don't miss logs as log indexer is asynchronous
                setTimeout(() => {
                    closeLogsSSE()
                }, 2000)

                if (!logsSSE.value) {
                    loadLogs(newExecution.id)
                }

                return
            }

            // running or paused
            if (!logsSSE.value) {
                followLogs(newExecution.id)
            }
        },
        {immediate: true},
    )

    watch(allLogIndicesByLevel, (val) => {
        emit("log-indices-by-level", val)
    })

    watch(
        () => props.logCursor,
        (newValue) => {
            if (newValue !== undefined) {
                scrollToLog(newValue)
            }
        },
    )

    // Lifecycle
    onMounted(() => {
        throttledExecutionUpdate.value = throttle((executionEvent: any) => { // FIXME: any
            targetExecution.value = JSON.parse(executionEvent.data)
        }, 500)

        if (props.targetExecutionId) {
            followExecution(props.targetExecutionId)
        }

        autoExpandBasedOnSettings()

        for (const taskRun of currentTaskRuns.value) {
            if (taskType(taskRun) === "io.kestra.plugin.core.flow.Loop") {
                updateLoopStatus(taskRun.id)
            }
        }
    })

    onBeforeUnmount(() => {
        closeLogsSSE()
    })

    // Methods
    async function updateLoopStatus(taskRunId: string) {
        if (!followedExecution.value) return
        try {
            const outputs = await OutputsAPI.taskRunOutputs({
                executionId: followedExecution.value.id,
                taskRunId,
            })
            if (outputs === null || !outputs.iterationCount) {
                return
            }
            loopOutputsByTaskRunId.value[taskRunId] = outputs
        } catch {
            // ignore fetch errors
        }
    }

    function fileUrl(path: string): string {
        return `${apiUrl()}/executions/${followedExecution.value.id}/file?path=${path}`
    }

    async function fetchAndStoreLogFileSize(path: string) {
        if (logFileSizeByPath.value[path] !== undefined) {
            return
        }

        const axiosResponse = await $http(
            `${apiUrl()}/executions/${followedExecution.value.id}/file/metas?path=${path}`,
            {
                validateStatus: (status: number) =>
                    status === 200 || status === 404 || status === 422,
            },
        )
        logFileSizeByPath.value[path] = Utils.humanFileSize(
            axiosResponse.data.size,
        )
    }

    function closeLogsSSE() {
        if (logsSSE.value) {
            logsSSE.value.close()
            logsSSE.value = undefined
        }
    }

    function toggleExpandCollapseAll() {
        if (shownAttemptsUid.value.length === 0) {
            expandAll()
        } else {
            collapseAll()
        }
    }

    function autoExpandBasedOnSettings() {
        if (autoExpandTaskRunStates.value.length === 0) {
            return
        }

        if (followedExecution.value === undefined) {
            setTimeout(() => autoExpandBasedOnSettings(), 50)
            return
        }
        currentTaskRuns.value.forEach((taskRun) => {
            if (isSubflow(taskRun) && !props.allowAutoExpandSubflows) {
                return
            }

            if (
                props.taskRunId === taskRun.id ||
                autoExpandTaskRunStates.value.includes(taskRun.state.current)
            ) {
                showAttempt(
                    attemptUid(
                        taskRun.id,
                        selectedAttemptNumberByTaskRunId.value[taskRun.id],
                    ),
                )
            }
        })
    }

    function shouldDisplayLogs(taskRun: TaskRun): boolean {
        const uid = attemptUid(
            taskRun.id,
            selectedAttemptNumberByTaskRunId.value[taskRun.id],
        )
        return (
            !!(props.taskRunId || shownAttemptsUid.value.includes(uid)) &&
            (logsWithIndexByAttemptUid.value[uid]?.length ?? 0) > 0
        )
    }

    function closeTargetExecutionSSE() {
        if (executionSSE.value) {
            executionSSE.value.close()
            executionSSE.value = undefined
        }
    }

    function followExecution(executionId: string) {
        closeTargetExecutionSSE()
        executionsStore
            .followExecution({id: executionId, rawSSE: true}, (s: string) => s)
            .then((sse: any) => { // FIXME: any
                executionSSE.value = sse
                executionSSE.value.onmessage = (executionEvent: any) => { // FIXME: any
                    const isEnd =
                        executionEvent &&
                        executionEvent.lastEventId === "end"
                    // we are receiving a first "fake" event to force initializing the connection: ignoring it
                    if (executionEvent.lastEventId !== "start") {
                        throttledExecutionUpdate.value!(executionEvent)
                    }
                    if (isEnd) {
                        closeTargetExecutionSSE()
                        throttledExecutionUpdate.value!.flush()
                    }
                }
            })
    }

    function refreshLogs() {
        timer.value = moment()
        rawLogs.value = deduplicateLogs(rawLogs.value.concat(logsBuffer.value))
        logsBuffer.value = []
        scrollToBottomFailedTask()
    }

    function followLogs(executionId: string) {
        executionsStore.followLogs({id: executionId, params: buildLogParams()}).then((sse: any) => { // FIXME: any
            logsSSE.value = sse

            logsSSE.value.onmessage = (event: any) => { // FIXME: any
                // we are receiving a first "fake" event to force initializing the connection: ignoring it
                if (event.lastEventId !== "start") {
                    logsBuffer.value = logsBuffer.value.concat(
                        JSON.parse(event.data),
                    )
                }

                clearTimeout(timeout.value)
                timeout.value = setTimeout(() => {
                    refreshLogs()
                }, 100)

                // force at least 1 logs refresh / 500ms
                if (moment().diff(timer.value, "seconds") > 0.5) {
                    clearTimeout(timeout.value)
                    refreshLogs()
                }
            }

            logsSSE.value.onerror = (_: unknown) => {
                coreStore.message = {
                    variant: "error",
                    title: t("error"),
                    message: t(
                        "something_went_wrong.loading_execution",
                    ),
                }
            }
        })
    }

    function isSubflow(taskRun: TaskRunWithDepth): boolean {
        return taskRun?.outputs?.executionId
    }

    function shouldDisplaySubflow(taskRunIndex: number, taskRun: TaskRunWithDepth): boolean {
        const subflowExecutionId = taskRun.outputs?.executionId
        const index = shownSubflowsIds.value.findIndex(
            (item) => item.subflowExecutionId === subflowExecutionId,
        )
        if (index === -1) {
            shownSubflowsIds.value.push({
                subflowExecutionId: subflowExecutionId,
                taskRunIndex: taskRunIndex,
            })
            return true
        } else {
            return (
                shownSubflowsIds.value[index].taskRunIndex === taskRunIndex
            )
        }
    }

    function expandAll() {
        if (!followedExecution.value) {
            setTimeout(() => expandAll(), 50)
            return
        }

        shownAttemptsUid.value = currentTaskRuns.value.map((taskRun) =>
            attemptUid(
                taskRun.id,
                selectedAttemptNumberByTaskRunId.value[taskRun.id] ?? 0,
            ),
        )
        shownAttemptsUid.value.forEach((uid) =>
            logsScrollerRefs.value?.[uid]?.[0]?.scrollToBottom(),
        )

        expandSubflows()
    }

    function expandSubflows() {
        if (
            currentTaskRuns.value.some((taskRun) => isSubflow(taskRun))
        ) {
            const subflowLogsElements = Object.values(
                subflowTaskRunDetailsRefs.value,
            )
            if (subflowLogsElements.length === 0) {
                setTimeout(() => expandSubflows(), 50)
            }

            subflowLogsElements?.forEach((subflowLogs: any) => // FIXME: any
                subflowLogs.expandAll(),
            )
        }
    }

    function collapseAll() {
        shownAttemptsUid.value = []
    }

    function attemptUid(taskRunId: string, attemptNumber: number): string {
        return `${taskRunId}-${attemptNumber}`
    }

    function scrollToBottomFailedTask() {
        if (
            autoExpandTaskRunStates.value.includes(
                followedExecution.value?.state?.current,
            )
        ) {
            currentTaskRuns.value.forEach((taskRun) => {
                if (
                    taskRun.state.current === State.FAILED ||
                    taskRun.state.current === State.RUNNING
                ) {
                    const attemptNumber = taskRun.attempts
                        ? taskRun.attempts.length - 1
                        : (props.forcedAttemptNumber ?? 0)
                    if (
                        shownAttemptsUid.value.includes(
                            `${taskRun.id}-${attemptNumber}`,
                        )
                    ) {
                        logsScrollerRefs.value?.[
                            `${taskRun.id}-${attemptNumber}`
                        ]?.scrollToBottom()
                    }
                }
            })
        }
    }

    function uniqueTaskRunDisplayFilter(currentTaskRun: TaskRun): boolean {
        return !(props.taskRunId && props.taskRunId !== currentTaskRun.id)
    }

    function buildLogParams(): Record<string, unknown> {
        const p: Record<string, unknown> = {...levelToRequestParams(props.levelFilter)}
        const taskId = taskRunById.value[props.taskRunId as string]?.taskId
        if (taskId) {
            p["filters[taskId][EQUALS]"] = taskId
        }
        // Logs default to NORMAL kind on the backend; surface this execution's own kind when it
        // isn't NORMAL (e.g. PLAYGROUND) so its logs still load.
        const kind = followedExecution.value?.kind
        if (kind && kind !== "NORMAL") {
            p["filters[kind][IN]"] = kind
        }
        return p
    }

    function loadLogs(executionId?: string) {
        const p = buildLogParams()
        executionsStore
            .loadLogs({
                executionId: executionId!,
                params: p,
            })
            .then((logs: any) => { // FIXME: any
                // `loadLogs` returns a paginated response `{ results, total }`, and `rawLogs` must be an array of log lines.
                rawLogs.value = logs?.results ?? logs ?? []
                // Discard any buffered SSE logs to prevent duplicates after the full REST fetch replaces `rawLogs`.
                logsBuffer.value = []
            })
    }

    function attempts(taskRun: TaskRun): TaskRunAttempt[] {
        if (
            followedExecution.value.state.current === State.RUNNING ||
            props.forcedAttemptNumber === undefined
        ) {
            return taskRun.attempts ?? [{state: taskRun.state}]
        }

        return taskRun.attempts
            ? [taskRun.attempts[props.forcedAttemptNumber]]
            : []
    }

    function showAttempt(uid: string) {
        if (!shownAttemptsUid.value.includes(uid)) {
            shownAttemptsUid.value.push(uid)
        }
    }

    function toggleShowAttempt(uid: string) {
        shownAttemptsUid.value = _xor(shownAttemptsUid.value, [uid])
    }

    function swapDisplayedAttempt(event: {taskRunId: string; attemptNumber: number}) {
        const {taskRunId, attemptNumber: newDisplayedAttemptNumber} = event
        shownAttemptsUid.value = shownAttemptsUid.value.map((uid) =>
            uid.startsWith(`${taskRunId}-`)
                ? attemptUid(taskRunId, newDisplayedAttemptNumber)
                : uid,
        )

        selectedAttemptNumberByTaskRunId.value[taskRunId] =
            newDisplayedAttemptNumber
    }

    function taskType(taskRun: TaskRun | undefined): string | undefined {
        if (!taskRun) return undefined

        const task = FlowUtils.findTaskById(flow.value, taskRun?.taskId)
        const parentTaskRunId = taskRun.parentTaskRunId
        if (task === undefined && parentTaskRunId) {
            return taskType(taskRunById.value[parentTaskRunId])
        }
        return task ? (task as any).type : undefined // FIXME: any
    }

    function emitLogCursor(cursor: string) {
        emit("log-cursor", cursor)
    }

    function childLogIndicesByLevel(taskRunIndex: number, logIndex: number, logIndicesByLevel: Record<string, string[]>) {
        childrenLogIndicesByLevelByChildUid.value[
            `${taskRunIndex}/${logIndex}`
        ] = logIndicesByLevel
    }

    function logsScrollerRef(el: any, ...ids: Array<string | number>) { // FIXME: any
        ids.forEach((id) => (logsScrollerRefs.value[id] = el))
    }

    function subflowTaskRunDetailsRef(el: any, id: string) { // FIXME: any
        subflowTaskRunDetailsRefs.value[id] = el
    }

    function scrollToLog(logId: string) {
        const split = logId.split("/")
        const taskRunIndex = Number(split[0])
        const globalIndex = Number(split[1])
        ;(taskRunScroller.value as any)?.scrollToItem(taskRunIndex) // FIXME: any

        const taskRun = currentTaskRuns.value[taskRunIndex]
        const uid = taskRun
            ? attemptUid(asTaskRun(taskRun).id, selectedAttemptNumberByTaskRunId.value[asTaskRun(taskRun).id])
            : undefined
        const items: any[] = (uid && displayItemsByAttemptUid.value[uid]) || [] // FIXME: any

        let position = -1
        for (let i = 0; i < items.length; i++) {
            const item = items[i]
            if (item.isGroup) {
                if (item.members.some((member: any) => member.index === globalIndex)) { // FIXME: any
                    position = i
                    if (!isGroupExpanded(taskRunIndex, item)) {
                        toggleGroup(taskRunIndex, item)
                    }
                    break
                }
            } else if (item.index === globalIndex) {
                position = i
                break
            }
        }

        nextTick(() => {
            ;(logsScrollerRefs.value?.[taskRunIndex] as any)?.scrollToItem(position >= 0 ? position : 0) // FIXME: any
            if (split.length > 2) {
                subflowTaskRunDetailsRefs.value?.[
                    taskRunIndex + "/" + globalIndex
                ]?.scrollToLog(split.slice(2).join("/"))
            }
        })
    }

    function deduplicateLogs(logs: any[]): any[] { // FIXME: any
        const list = new Set<string>()

        return logs.filter((log) => {
            // Use the server-assigned index when present as it is the most stable unique identifier per log line per attempt.
            const key = log.index !== undefined
                ? `${log.taskRunId}-${log.attemptNumber}-${log.index}`
                : `${log.taskRunId}-${log.attemptNumber}-${log.timestamp}-${log.message}`

            if (list.has(key)) return false

            list.add(key)

            return true
        })
    }

    // Expose public methods for parent refs
    defineExpose({
        toggleExpandCollapseAll,
        expandAll,
        scrollToLog,
    })
</script>

<style scoped lang="scss">

.log-group-more {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    width: 100%;
    padding: var(--ks-spacing-1) var(--ks-spacing-3) var(--ks-spacing-1) 4.5rem;
    background: none;
    border: none;
    border-left: 2px solid transparent;
    cursor: pointer;
    color: var(--ks-text-dim);
    font-family: var(--ks-font-family-sans);
    text-align: left;

    &:hover {
        color: var(--ks-text-secondary);
        background: var(--ks-bg-hover);
    }

    :deep(.material-design-icon) {
        display: inline-flex;
        align-items: center;
        line-height: 0;
    }
}

.log-group-chevron {
    flex: none;
    transition: transform 0.15s ease;

    &.collapsed {
        transform: rotate(-90deg);
    }
}

.log-group-count {
    flex: none;
    background: var(--ks-bg-tag);
    color: var(--ks-text-primary);
    font-weight: 600;
    border-radius: var(--ks-radius-sm);
    padding: 1px var(--ks-spacing-2);
}

.log-group-label {
    color: var(--ks-text-dim);
}

.log-wrapper {
  :deep(
    > .vue-recycle-scroller__item-wrapper
      > .vue-recycle-scroller__item-view
      > div
  ) {
    padding-bottom: 1rem;
  }

  :deep(.line) {
    padding-left: 0;
  }

  .progress-bar {
    margin-block: .5rem;
    flex: 1;

    :deep(.kel-progress__text) {
      font-size: var(--ks-font-size-sm) !important;
      color: var(--ks-text-secondary);
    }
  }

  .attempt-wrapper {
    background-color: var(--ks-bg-input);
    margin-bottom: 0;
    border: 1px solid var(--ks-border-default);

    &.attempt-wrapper--transparent {
      background-color: transparent;
      border: none;

      .line {
        border-top: none;
      }
    }

    :deep(.kel-card__body) {
      padding: 0;
    }

    .attempt-wrapper & {
      border-radius: var(--ks-radius-base);
    }

    tbody:last-child & {
      border-bottom: 1px solid var(--ks-border-default);
    }

    .attempt-header {
      padding: 0 0.5rem 0.5rem;
      border-bottom: 1px solid var(--ks-border-default);
    }
  }

  .output {
    margin-right: 5px;
  }

  pre {
    border: 1px solid var(--light);
    background-color: var(--ks-scrollbar-content);
    padding: 10px;
    margin-top: 5px;
    margin-bottom: var(--ks-font-size-lg);
  }

  .log-lines {
    transition: max-height 0.2s ease-out;
    max-height: 300px;

    :deep(.vue-recycle-scroller__item-view > div) {
      min-height: 2rem;
    }

    &.single-line {
      max-height: calc(100vh - 250px);
    }

    :deep(.line) {
      padding: var(--ks-spacing-1) var(--ks-spacing-3);

      &.cursor {
        background-color: var(--ks-border-default);
      }
    }
  }
}
</style>

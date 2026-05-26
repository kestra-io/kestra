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
                v-if="uniqueTaskRunDisplayFilter(currentTaskRun)"
                :item="currentTaskRun"
                :active="isTaskRunActive"
                :data-index="currentTaskRunIndex"
            >   
                <KsCard class="attempt-wrapper">
                    <TaskRunLine
                        :currentTaskRun="currentTaskRun"
                        :followedExecution="followedExecution"
                        :flow="flow"
                        :forcedAttemptNumber="forcedAttemptNumber"
                        :taskRunId="taskRunId"
                        :selectedAttemptNumberByTaskRunId="
                            selectedAttemptNumberByTaskRunId
                        "
                        :shownAttemptsUid="shownAttemptsUid"
                        :logs="filteredLogs"
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
                            logsWithIndexByAttemptUid[
                                attemptUid(
                                    currentTaskRun.id,
                                    selectedAttemptNumberByTaskRunId[
                                        currentTaskRun.id
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
                                        currentTaskRun.id,
                                        selectedAttemptNumberByTaskRunId[
                                            currentTaskRun.id
                                        ],
                                    ),
                                )
                        "
                        @resize="scrollToBottomFailedTask"
                    >
                        <template #default="{item, index, active}">
                            <DynamicScrollerItem
                                :item="item"
                                :active="active"
                                :sizeDependencies="[item.message, item.image]"
                                :data-index="index"
                            >
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
                                            {{ $t("download") }}
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
                                            `${currentTaskRunIndex}/${index}`
                                    "
                                    :class="{
                                        ['log-bg-' +
                                            levelToHighlight?.toLowerCase()]:
                                                levelToHighlight === item.level,
                                        'opacity-40':
                                            levelToHighlight &&
                                            levelToHighlight !== item.level,
                                    }"
                                    :key="index"
                                    :level="level"
                                    :log="item"
                                    :excludeMetas="excludeMetas"
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
                                                index,
                                                currentTaskRun,
                                            ) &&
                                            currentTaskRun.outputs?.executionId
                                    "
                                    :ref="
                                        (el) =>
                                            subflowTaskRunDetailsRef(
                                                el,
                                                currentTaskRunIndex +
                                                    '/' +
                                                    index,
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
                                                index +
                                                '/' +
                                                $event,
                                        )
                                    "
                                    @log-indices-by-level="
                                        childLogIndicesByLevel(
                                            currentTaskRunIndex,
                                            index,
                                            $event,
                                        )
                                    "
                                    :levelToHighlight="levelToHighlight"
                                    :level="level"
                                    :excludeMetas="[
                                        'namespace',
                                        'flowId',
                                        'taskId',
                                        'executionId',
                                    ]"
                                    :filter="filter"
                                    :allowAutoExpandSubflows="false"
                                    :targetExecutionId="
                                        currentTaskRun.outputs.executionId
                                    "
                                    :class="
                                        $el.classList.contains('even')
                                            ? ''
                                            : 'even'
                                    "
                                    :showProgressBar="showProgressBar"
                                    :showLogs="showLogs"
                                />
                            </DynamicScrollerItem>
                        </template>
                    </DynamicScroller>
                </KsCard>
                <div 
                    v-if="taskType(currentTaskRun) === 'io.kestra.plugin.core.flow.Loop' && isTaskRunActive" 
                    style="display:flex; align-items: center; gap: 12px; margin-bottom: 12px"
                >
                    <KsButton
                        :tag="RouterLink"
                        :to="{
                            name: 'executions/list', 
                            query: {
                                'filters[parentId][EQUALS]': currentTaskRun.executionId,
                                'filters[kind][EQUALS]': 'LOOP',
                            }        
                        }"
                    >
                        Iterations
                    </KsButton>
                    <KsProgress 
                        :percentage="Math.ceil((loopOutputsByTaskRunId[currentTaskRun.id]?.terminatedIterations ?? 0) / (loopOutputsByTaskRunId[currentTaskRun.id]?.iterationCount ?? 1) * 100)" 
                        :strokeWidth="24"
                        :textInside="true"
                        class="progress-bar"
                    >
                        <span>{{ loopOutputsByTaskRunId[currentTaskRun.id]?.terminatedIterations ?? 0 }} / {{ loopOutputsByTaskRunId[currentTaskRun.id]?.iterationCount ?? '?' }}</span>
                    </KsProgress>
                </div>
            </DynamicScrollerItem>
        </template>
    </DynamicScroller>
</template>

<script setup lang="ts">
    import Download from "vue-material-design-icons/Download.vue"
    import {RouterLink} from "vue-router"
    import * as OutputsAPI from "@kestra-io/kestra-sdk/outputs"
    import LogLine from "./LogLine.vue"
    import {LOG_LEVEL_TYPE, State} from "@kestra-io/design-system"
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
    import FilePreview from "../executions/FilePreview.vue"
    import {apiUrl} from "override/utils/route"
    import * as Utils from "../../utils/utils"
    import * as LogUtils from "../../utils/logs"
    import throttle from "lodash/throttle"
    import {useClient} from "@kestra-io/kestra-sdk"
    import KsProgress from "@kestra-io/design-system/components/Data/KsProgress.vue"
    import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import type {Log} from "../../stores/logs"

    export interface Props {
        logCursor?: string
        levelToHighlight?: string
        level?: LOG_LEVEL_TYPE
        filter?: string
        taskRunId?: string
        excludeMetas?: (keyof Log)[]
        forcedAttemptNumber?: number
    /** allows to fetch the execution at startup */
        targetExecutionId?: string
        /** allows to pass directly a flow source (since it is already fetched by parent component) */
        targetFlow?: Record<string, any>
        allowAutoExpandSubflows?: boolean
        showProgressBar?: boolean
        showLogs?: boolean
    }

    const props = withDefaults(defineProps<Props>(), {
        logCursor: undefined,
        levelToHighlight: undefined,
        level: "INFO",
        filter: "",
        taskRunId: undefined,
        excludeMetas: () => [],
        forcedAttemptNumber: undefined,
        targetExecutionId: undefined,
        targetFlow: undefined,
        allowAutoExpandSubflows: true,
        showProgressBar: true,
        showLogs: undefined,
    })

    const emit = defineEmits<{
        (e: "opened-taskruns-count", count: number): void
        (e: "follow"): void
        (e: "reset-expand-collapse-all-switch"): void
        (e: "log-cursor", cursor: string): void
        (e: "log-indices-by-level", indices: Record<string, string[]>): void
    }>()

    const {t} = useI18n()
    const $http = useClient()
    const coreStore = useCoreStore()
    const executionsStore = useExecutionsStore()

    // Template refs
    const taskRunScroller = ref<any>(null)

    // State
    const shownAttemptsUid = ref<string[]>([])
    const rawLogs = ref<any[]>([])
    const timer = ref<any>(undefined)
    const timeout = ref<ReturnType<typeof setTimeout> | undefined>(undefined)
    const selectedAttemptNumberByTaskRunId = ref<Record<string, number>>({})
    const executionSSE = ref<any>(undefined)
    const logsSSE = ref<any>(undefined)
    const flow = ref<any>(undefined)
    const logsBuffer = ref<any[]>([])
    const shownSubflowsIds = ref<any[]>([])
    const logFileSizeByPath = ref<Record<string, string>>({})
    const childrenLogIndicesByLevelByChildUid = ref<Record<string, any>>({})
    const logsScrollerRefs = ref<Record<string, any>>({})
    const subflowTaskRunDetailsRefs = ref<Record<string, any>>({})
    const throttledExecutionUpdate = ref<any>(undefined)
    const targetExecution = ref<any>(undefined)
    const loopOutputsByTaskRunId = ref<Record<string, any>>({})

    // Computed
    const followedExecution = computed(() =>
        props.targetExecutionId === undefined
            ? executionsStore.execution
            : targetExecution.value,
    )

    const currentTaskRuns = computed((): Record<string, any>[] =>
        followedExecution.value?.taskRunList?.filter((tr: any) =>
            props.taskRunId ? tr.id === props.taskRunId : true,
        ) ?? [],
    )

    const taskRunById = computed(() =>
        Object.fromEntries(
            currentTaskRuns.value.map((taskRun: any) => [taskRun.id, taskRun]),
        ),
    )

    const levelOrLower = computed(() => LogUtils.levelOrLower(props.level!))

    const filteredLogs = computed(() =>
        rawLogs.value.filter((log: any) => levelOrLower.value.includes(log.level)),
    )

    const autoExpandTaskRunStates = computed(() => {
        switch (localStorage.getItem("logDisplay") || logDisplayTypes.DEFAULT) {
        case logDisplayTypes.ERROR:
            return [State.FAILED, State.RUNNING, State.PAUSED]
        case logDisplayTypes.ALL:
            return State.arrayAllStates().map((s: any) => s.name)
        case logDisplayTypes.HIDDEN:
            return []
        default:
            return State.arrayAllStates().map((s: any) => s.name)
        }
    })

    const logsWithIndexByAttemptUid = computed(() => {
        const logFilesWrappers = currentTaskRuns.value.flatMap((taskRun: any) =>
            attempts(taskRun)
                .filter((attempt: any) => attempt.logFile !== undefined)
                .map((attempt: any, attemptNumber: number) => ({
                    logFile: attempt.logFile,
                    taskRunId: taskRun.id,
                    attemptNumber,
                })),
        )

        logFilesWrappers.forEach((logFileWrapper: any) =>
            fetchAndStoreLogFileSize(logFileWrapper.logFile),
        )

        const indexedLogs = [...filteredLogs.value, ...logFilesWrappers]
            .filter(
                (logLine: any) =>
                    logLine.logFile !== undefined ||
                    props.filter === "" ||
                    logLine?.message
                        .toLowerCase()
                        .includes(props.filter.toLowerCase()) ||
                    isSubflow(taskRunById.value[logLine.taskRunId]),
            )
            .map((logLine: any, index: number) => ({...logLine, index}))

        return _groupBy(indexedLogs, (indexedLog: any) =>
            attemptUid(indexedLog.taskRunId, indexedLog.attemptNumber),
        )
    })

    const currentTaskRunsLogIndicesByLevel = computed(() =>
        currentTaskRuns.value.reduce(
            (acc: any, taskRun: any, taskRunIndex: number) => {
                if (shouldDisplayLogs(taskRun)) {
                    const currentTaskRunLogs =
                        logsWithIndexByAttemptUid.value[
                            attemptUid(
                                taskRun.id,
                                selectedAttemptNumberByTaskRunId.value[taskRun.id],
                            )
                        ]
                    currentTaskRunLogs?.forEach((log: any, logIndex: number) => {
                        acc[log.level] = [
                            ...(acc?.[log.level] ?? []),
                            taskRunIndex + "/" + logIndex,
                        ]
                    })
                }
                return acc
            },
            {},
        ),
    )

    const allLogIndicesByLevel = computed(() => {
        const base = {...currentTaskRunsLogIndicesByLevel.value}
        return Object.entries(childrenLogIndicesByLevelByChildUid.value).reduce(
            (acc: any, [logUid, childIndices]: [string, any]) => {
                Object.entries(childIndices).forEach(([level, logIndices]: [string, any]) => {
                    acc[level] = [
                        ...(acc?.[level] ?? []),
                        ...logIndices.map((logIndex: string) => logUid + "/" + logIndex),
                    ]
                })
                return acc
            },
            base,
        )
    })

    // Methods
    async function updateLoopStatus(taskRunId: string) {
        if (!followedExecution.value) return
        try {
            const outputs = await OutputsAPI.taskRunOutputs({
                executionId: followedExecution.value.id,
                taskRunId,
            })
            if (outputs === null || !outputs.iterationCount || !outputs.terminatedIterations) {
                return
            }
            loopOutputsByTaskRunId.value[taskRunId] = outputs
        } catch {
    // ignore fetch errors
        }
    }

    function fileUrl(path: string) {
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
        logFileSizeByPath.value[path] = Utils.humanFileSize(axiosResponse.data.size)
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
        currentTaskRuns.value.forEach((taskRun: any) => {
            if (isSubflow(taskRun) && !props.allowAutoExpandSubflows) {
                return
            }
            if (
                props.taskRunId === taskRun.id ||
                autoExpandTaskRunStates.value.includes(taskRun.state.current)
            ) {
                showAttempt(
                    attemptUid(taskRun.id, selectedAttemptNumberByTaskRunId.value[taskRun.id]),
                )
            }
        })
    }

    function shouldDisplayLogs(taskRun: any) {
        const uid = attemptUid(taskRun.id, selectedAttemptNumberByTaskRunId.value[taskRun.id])
        return (
            (props.taskRunId || shownAttemptsUid.value.includes(uid)) &&
            logsWithIndexByAttemptUid.value[uid]?.length > 0
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
        ;(executionsStore.followExecution as any)({id: executionId, rawSSE: true})
            .then((sse: any) => {
                executionSSE.value = sse
                executionSSE.value.onmessage = (executionEvent: any) => {
                    const isEnd = executionEvent && executionEvent.lastEventId === "end"
                    // we are receiving a first "fake" event to force initializing the connection: ignoring it
                    if (executionEvent.lastEventId !== "start") {
                        throttledExecutionUpdate.value(executionEvent)
                    }
                    if (isEnd) {
                        closeTargetExecutionSSE()
                        throttledExecutionUpdate.value.flush()
                    }
                }
            })
    }

    function refreshLogs() {
        timer.value = moment()
        rawLogs.value = deduplicateLogs(rawLogs.value.concat(logsBuffer.value))
        for (const taskRun of currentTaskRuns.value) {
            if (taskType(taskRun) === "io.kestra.plugin.core.flow.Loop") {
                updateLoopStatus(taskRun.id)
            }
        }
        logsBuffer.value = []
        scrollToBottomFailedTask()
    }

    function followLogs(executionId: string) {
        executionsStore.followLogs({id: executionId}).then((sse: any) => {
            logsSSE.value = sse

            logsSSE.value.onmessage = (event: any) => {
                // we are receiving a first "fake" event to force initializing the connection: ignoring it
                if (event.lastEventId !== "start") {
                    logsBuffer.value = logsBuffer.value.concat(JSON.parse(event.data))
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

            logsSSE.value.onerror = (_: any) => {
                coreStore.message = {
                    variant: "error",
                    title: t("error"),
                    message: t("something_went_wrong.loading_execution"),
                }
            }
        })
    }

    function isSubflow(taskRun: any) {
        return taskRun?.outputs?.executionId
    }

    function shouldDisplaySubflow(taskRunIndex: number, taskRun: any) {
        const subflowExecutionId = taskRun.outputs.executionId
        const index = shownSubflowsIds.value.findIndex(
            (item: any) => item.subflowExecutionId === subflowExecutionId,
        )
        if (index === -1) {
            shownSubflowsIds.value.push({subflowExecutionId, taskRunIndex})
            return true
        } else {
            return shownSubflowsIds.value[index].taskRunIndex === taskRunIndex
        }
    }

    function expandAll() {
        if (!followedExecution.value) {
            setTimeout(() => expandAll(), 50)
            return
        }
        shownAttemptsUid.value = currentTaskRuns.value.map((taskRun: any) =>
            attemptUid(taskRun.id, selectedAttemptNumberByTaskRunId.value[taskRun.id] ?? 0),
        )
        shownAttemptsUid.value.forEach((uid: string) =>
            logsScrollerRefs.value?.[uid]?.[0]?.scrollToBottom(),
        )
        expandSubflows()
    }

    function expandSubflows() {
        if (currentTaskRuns.value.some((taskRun: any) => isSubflow(taskRun))) {
            const subflowLogsElements = Object.values(subflowTaskRunDetailsRefs.value)
            if (subflowLogsElements.length === 0) {
                setTimeout(() => expandSubflows(), 50)
            }
            subflowLogsElements?.forEach((subflowLogs: any) => subflowLogs.expandAll())
        }
    }

    function collapseAll() {
        shownAttemptsUid.value = []
    }

    function attemptUid(taskRunId: string, attemptNumber: number) {
        return `${taskRunId}-${attemptNumber}`
    }

    function scrollToBottomFailedTask() {
        if (autoExpandTaskRunStates.value.includes(followedExecution.value?.state?.current)) {
            currentTaskRuns.value.forEach((taskRun: any) => {
                if (
                    taskRun.state.current === State.FAILED ||
                    taskRun.state.current === State.RUNNING
                ) {
                    const attemptNumber = taskRun.attempts
                        ? taskRun.attempts.length - 1
                        : (props.forcedAttemptNumber ?? 0)
                    if (shownAttemptsUid.value.includes(`${taskRun.id}-${attemptNumber}`)) {
                        logsScrollerRefs.value?.[`${taskRun.id}-${attemptNumber}`]?.scrollToBottom()
                    }
                }
            })
        }
    }

    function uniqueTaskRunDisplayFilter(currentTaskRun: any) {
        return !(props.taskRunId && props.taskRunId !== currentTaskRun.id)
    }

    function loadLogs(executionId: string) {
        executionsStore
            .loadLogs({
                executionId,
                params: {
                    minLevel: props.level,
                    taskId: taskRunById.value[props.taskRunId!]?.taskId,
                },
            })
            .then((logs: any) => {
                // `loadLogs` returns a paginated response `{ results, total }`, and `rawLogs` must be an array of log lines.
                rawLogs.value = logs?.results ?? logs ?? []
                // Discard any buffered SSE logs to prevent duplicates after the full REST fetch replaces `rawLogs`.
                logsBuffer.value = []
            })
    }

    function attempts(taskRun: any) {
        if (
            followedExecution.value.state.current === State.RUNNING ||
            props.forcedAttemptNumber === undefined
        ) {
            return taskRun.attempts ?? [{state: taskRun.state}]
        }
        return taskRun.attempts ? [taskRun.attempts[props.forcedAttemptNumber]] : []
    }

    function showAttempt(attemptUidValue: string) {
        if (!shownAttemptsUid.value.includes(attemptUidValue)) {
            shownAttemptsUid.value.push(attemptUidValue)
        }
    }

    function toggleShowAttempt(attemptUidValue: string) {
        shownAttemptsUid.value = _xor(shownAttemptsUid.value, [attemptUidValue])
    }

    function swapDisplayedAttempt(event: {taskRunId: string; attemptNumber: number}) {
        const {taskRunId, attemptNumber: newDisplayedAttemptNumber} = event
        shownAttemptsUid.value = shownAttemptsUid.value.map((uid: string) =>
            uid.startsWith(`${taskRunId}-`)
                ? attemptUid(taskRunId, newDisplayedAttemptNumber)
                : uid,
        )
        selectedAttemptNumberByTaskRunId.value[taskRunId] = newDisplayedAttemptNumber
    }

    function taskType(taskRun: any): string | undefined {
        if (!taskRun) return undefined
        const task = FlowUtils.findTaskById(flow.value, taskRun?.taskId)
        const parentTaskRunId = taskRun.parentTaskRunId
        if (task === undefined && parentTaskRunId) {
            return taskType(taskRunById.value[parentTaskRunId])
        }
        return task ? (task as any).type : undefined
    }

    function emitLogCursor(logCursor: string) {
        emit("log-cursor", logCursor)
    }

    function childLogIndicesByLevel(taskRunIndex: number, logIndex: number, logIndicesByLevel: any) {
        childrenLogIndicesByLevelByChildUid.value[`${taskRunIndex}/${logIndex}`] = logIndicesByLevel
    }

    function logsScrollerRef(el: any, ...ids: any[]) {
        ids.forEach((id) => (logsScrollerRefs.value[id] = el))
    }

    function subflowTaskRunDetailsRef(el: any, id: string) {
        subflowTaskRunDetailsRefs.value[id] = el
    }

    function scrollToLog(logId: string) {
        const split = logId.split("/")
        taskRunScroller.value?.scrollToItem(split[0])
        logsScrollerRefs.value?.[split[0]]?.scrollToItem(split[1])
        if (split.length > 2) {
            subflowTaskRunDetailsRefs.value?.[split[0] + "/" + split[1]]?.scrollToLog(
                split.slice(2).join("/"),
            )
        }
    }

    function deduplicateLogs(logs: any[]) {
        const list = new Set()
        return logs.filter((log: any) => {
            // Use the server-assigned index when present as it is the most stable unique identifier per log line per attempt.
            const key =
                log.index !== undefined
                    ? `${log.taskRunId}-${log.attemptNumber}-${log.index}`
                    : `${log.taskRunId}-${log.attemptNumber}-${log.timestamp}-${log.message}`
            if (list.has(key)) return false
            list.add(key)
            return true
        })
    }

    // Watchers
    watch(
        () => shownAttemptsUid.value.length,
        (openedTaskrunsCount: number) => {
            emit("opened-taskruns-count", openedTaskrunsCount)
        },
    )

    watch(
        () => props.level,
        () => {
            rawLogs.value = []
            if (followedExecution.value) loadLogs(followedExecution.value.id)
        },
    )

    watch(
        currentTaskRuns,
        (taskRuns: any[]) => {
            // by default we preselect the last attempt for each task run
            selectedAttemptNumberByTaskRunId.value = Object.fromEntries(
                taskRuns.map((taskRun: any) => [
                    taskRun.id,
                    props.forcedAttemptNumber ?? attempts(taskRun).length - 1,
                ]),
            )
            autoExpandBasedOnSettings()
        },
        {immediate: true, deep: true},
    )

    watch(
        () => props.targetFlow,
        (flowSource: any) => {
            if (flowSource) {
                flow.value = flowSource
            }
        },
        {immediate: true},
    )

    watch(
        followedExecution,
        async (newExecution: any, oldExecution: any) => {
            if (!newExecution) {
                return
            }

            if (!oldExecution) {
                nextTick(() => {
                    const parentScroller =
                        taskRunScroller.value?.$el?.parentNode?.closest(".vue-recycle-scroller")
                    if (parentScroller) {
                        const scrollerStyles = window.getComputedStyle(parentScroller)
                        taskRunScroller.value.$el.style.maxHeight = `${parseFloat(scrollerStyles.getPropertyValue("max-height")) - parentScroller.clientHeight}px`
                    }
                })
            }

            if (!props.targetFlow) {
                flow.value = await executionsStore.loadFlowForExecution({
                    namespace: newExecution.namespace,
                    flowId: newExecution.flowId,
                    revision: newExecution.flowRevision,
                    store: false,
                })
            }

            if (!State.isRunning(newExecution.state.current)) {
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

    watch(allLogIndicesByLevel, () => {
        emit("log-indices-by-level", allLogIndicesByLevel.value)
    })

    watch(
        () => props.logCursor,
        (newValue: string | undefined) => {
            if (newValue !== undefined) {
                scrollToLog(newValue)
            }
        },
    )

    // Lifecycle
    onMounted(() => {
        throttledExecutionUpdate.value = throttle((executionEvent: any) => {
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

    defineExpose({
        expandAll,
        scrollToLog,
        toggleExpandCollapseAll,
    })

</script>

<script lang="ts">
    // Needed only to preserve compat with parent components that rely on the resolved component name
    // for recursive self-reference in the template ($options.name).
    export default {name: "TaskRunDetails"}
</script>

<style scoped lang="scss">

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
    }

    .attempt-wrapper {
        background-color: var(--ks-bg-input);
        margin-bottom: 0;
        border: 1px solid var(--ks-border-default);

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

        .line {
            padding: 0.5rem;
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

        .line {
            padding: 1rem;

            &.cursor {
                background-color: var(--ks-border-default);
            }
        }
    }
}
</style>

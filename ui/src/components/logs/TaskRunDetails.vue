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
                        <template #default="{item, index, active}: {item: Record<string, any>, index: number, active: boolean}">
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
                                    :level="(level as any)"
                                    :log="(item as any)"
                                    :excludeMetas="(excludeMetas as any)"
                                    v-else-if="
                                        (filter ?? '') === '' ||
                                            item.message
                                                ?.toLowerCase()
                                                .includes((filter ?? '').toLowerCase())
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
            </DynamicScrollerItem>
        </template>
    </DynamicScroller>
</template>

<script setup lang="ts">
    import {ref, computed, watch, onMounted, onBeforeUnmount, useTemplateRef} from "vue"
    import {useI18n} from "vue-i18n"
    import moment from "moment"
    import Download from "vue-material-design-icons/Download.vue"
    import LogLine from "./LogLine.vue"
    import {State} from "@kestra-io/design-system"
    import _xor from "lodash/xor"
    import _groupBy from "lodash/groupBy"
    import "vue-virtual-scroller/dist/vue-virtual-scroller.css"
    import {logDisplayTypes} from "../../utils/constants"
    import {DynamicScroller, DynamicScrollerItem} from "vue-virtual-scroller"
    import {useCoreStore} from "../../stores/core"
    import {useExecutionsStore} from "../../stores/executions"
    import TaskRunLine from "../executions/TaskRunLine.vue"
    // @ts-ignore - no type declarations for JS utility
    import * as FlowUtils from "../../utils/flowUtils"
    import FilePreview from "../executions/FilePreview.vue"
    import {apiUrl} from "override/utils/route"
    import * as Utils from "../../utils/utils"
    import * as LogUtils from "../../utils/logs"
    import throttle from "lodash/throttle"
    import {useClient} from "@kestra-io/kestra-sdk"

    // self-reference for recursive usage
    import TaskRunDetails from "./TaskRunDetails.vue"

    defineOptions({name: "TaskRunDetails"})

    const props = defineProps<{
        logCursor?: string
        levelToHighlight?: string
        level?: string
        filter?: string
        taskRunId?: string
        excludeMetas?: string[]
        forcedAttemptNumber?: number
        // allows to fetch the execution at startup
        targetExecutionId?: string
        // allows to pass directly a flow source (since it is already fetched by parent component)
        targetFlow?: Record<string, any>
        allowAutoExpandSubflows?: boolean
        showProgressBar?: boolean
        showLogs?: boolean
    }>()

    const emit = defineEmits<{
        "opened-taskruns-count": [number]
        follow: [unknown]
        "reset-expand-collapse-all-switch": []
        "log-cursor": [string]
        "log-indices-by-level": [Record<string, string[]>]
    }>()

    const {t} = useI18n({useScope: "global"})
    const $http = useClient()
    const coreStore = useCoreStore()
    const executionsStore = useExecutionsStore()

    const taskRunScroller = useTemplateRef<{ scrollToItem: (index: number | string) => void; $el: HTMLElement }>("taskRunScroller")

    const shownAttemptsUid = ref<string[]>([])
    const rawLogs = ref<Array<Record<string, any>>>([])
    const timer = ref<ReturnType<typeof moment> | undefined>(undefined)
    const timeout = ref<ReturnType<typeof setTimeout> | undefined>(undefined)
    const selectedAttemptNumberByTaskRunId = ref<Record<string, number>>({})
    const executionSSE = ref<{ close: () => void; onmessage: ((e: MessageEvent) => void) | null } | undefined>(undefined)
    const logsSSE = ref<{ close: () => void; onmessage: ((e: MessageEvent) => void) | null; onerror: ((e: Event) => void) | null } | undefined>(undefined)
    const flow = ref<Record<string, any> | undefined>(undefined)
    const logsBuffer = ref<Array<Record<string, any>>>([])
    const shownSubflowsIds = ref<Array<{ subflowExecutionId: string; taskRunIndex: number }>>([])
    const logFileSizeByPath = ref<Record<string, string>>({})
    const childrenLogIndicesByLevelByChildUid = ref<Record<string, Record<string, string[]>>>({})
    const logsScrollerRefs = ref<Record<string, { scrollToBottom: () => void; scrollToItem: (index: number | string) => void } | null>>({})
    const subflowTaskRunDetailsRefs = ref<Record<string, { expandAll: () => void; scrollToLog: (logId: string) => void } | null>>({})
    const throttledExecutionUpdate = ref<ReturnType<typeof throttle> | undefined>(undefined)
    const targetExecution = ref<Record<string, any> | undefined>(undefined)

    const levelProp = computed(() => props.level ?? "INFO")
    const filterProp = computed(() => props.filter ?? "")
    const allowAutoExpandSubflowsProp = computed(() => props.allowAutoExpandSubflows ?? true)

    const followedExecution = computed<Record<string, any> | undefined>(() =>
        props.targetExecutionId === undefined
            ? executionsStore.execution
            : targetExecution.value,
    )

    const currentTaskRuns = computed(() =>
        (followedExecution.value?.taskRunList?.filter((tr: Record<string, any>) =>
            props.taskRunId ? tr.id === props.taskRunId : true,
        ) ?? []) as Array<Record<string, any>>,
    )

    const taskRunById = computed(() =>
        Object.fromEntries(
            currentTaskRuns.value.map((taskRun) => [taskRun.id, taskRun]),
        ),
    )

    const logsWithIndexByAttemptUid = computed(() => {
        const logFilesWrappers = currentTaskRuns.value.flatMap((taskRun) =>
            attempts(taskRun)
                .filter((attempt: Record<string, any>) => attempt.logFile !== undefined)
                .map((attempt: Record<string, any>, attemptNumber: number) => ({
                    logFile: attempt.logFile,
                    taskRunId: taskRun.id,
                    attemptNumber,
                })),
        )

        logFilesWrappers.forEach((logFileWrapper: { logFile: string }) =>
            fetchAndStoreLogFileSize(logFileWrapper.logFile),
        )

        const indexedLogs = [...filteredLogs.value, ...logFilesWrappers]
            .filter(
                (logLine: Record<string, any>) =>
                    logLine.logFile !== undefined ||
                    filterProp.value === "" ||
                    logLine?.message
                        .toLowerCase()
                        .includes(filterProp.value.toLowerCase()) ||
                    isSubflow(taskRunById.value[logLine.taskRunId]),
            )
            .map((logLine: Record<string, any>, index: number) => ({...logLine, index}))

        return _groupBy(indexedLogs, (indexedLog: Record<string, any>) =>
            attemptUid(indexedLog.taskRunId, indexedLog.attemptNumber),
        )
    })

    const autoExpandTaskRunStates = computed(() => {
        switch (
            localStorage.getItem("logDisplay") ||
            logDisplayTypes.DEFAULT
        ) {
        case logDisplayTypes.ERROR:
            return [State.FAILED, State.RUNNING, State.PAUSED]
        case logDisplayTypes.ALL:
            return State.arrayAllStates().map((s: { name: string }) => s.name)
        case logDisplayTypes.HIDDEN:
            return []
        default:
            return State.arrayAllStates().map((s: { name: string }) => s.name)
        }
    })

    const currentTaskRunsLogIndicesByLevel = computed(() =>
        currentTaskRuns.value.reduce(
            (acc: Record<string, string[]>, taskRun: Record<string, any>, taskRunIndex: number) => {
                if (shouldDisplayLogs(taskRun)) {
                    const currentTaskRunLogs =
                        logsWithIndexByAttemptUid.value[
                            attemptUid(
                                taskRun.id,
                                selectedAttemptNumberByTaskRunId.value[taskRun.id],
                            )
                        ]
                    currentTaskRunLogs?.forEach((log: Record<string, any>, logIndex: number) => {
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
        const current = {...currentTaskRunsLogIndicesByLevel.value}
        return Object.entries(
            childrenLogIndicesByLevelByChildUid.value,
        ).reduce(
            (all: Record<string, string[]>, [logUid, childrenLogIndicesByLevel]) => {
                Object.entries(childrenLogIndicesByLevel).forEach(
                    ([level, logIndices]) => {
                        all[level] = [
                            ...(all?.[level] ?? []),
                            ...logIndices.map(
                                (logIndex: string) => logUid + "/" + logIndex,
                            ),
                        ]
                    },
                )

                return all
            },
            current,
        )
    })

    const levelOrLower = computed(() =>
        LogUtils.levelOrLower(levelProp.value as any),
    )

    const filteredLogs = computed(() =>
        rawLogs.value.filter((log) =>
            levelOrLower.value.includes(log.level),
        ),
    )

    watch(() => shownAttemptsUid.value.length, (openedTaskrunsCount: number) => {
        emit("opened-taskruns-count", openedTaskrunsCount)
    })

    watch(levelProp, () => {
        rawLogs.value = []
        if (followedExecution.value)
            loadLogs(followedExecution.value.id)
    })

    watch(currentTaskRuns, (taskRuns) => {
        selectedAttemptNumberByTaskRunId.value = Object.fromEntries(
            taskRuns.map((taskRun) => [
                taskRun.id,
                props.forcedAttemptNumber ??
                    attempts(taskRun).length - 1,
            ]),
        )
        autoExpandBasedOnSettings()
    }, {immediate: true, deep: true})

    watch(() => props.targetFlow, (flowSource) => {
        if (flowSource) {
            flow.value = flowSource
        }
    }, {immediate: true})

    watch(followedExecution, async (newExecution, oldExecution) => {
        if (!newExecution) {
            return
        }

        if (!oldExecution) {
            const el = taskRunScroller.value?.$el
            if (el) {
                const parentScroller = (el.parentNode as HTMLElement | null)?.closest?.(".vue-recycle-scroller") as HTMLElement | null
                if (parentScroller) {
                    const scrollerStyles = window.getComputedStyle(parentScroller)
                    el.style.maxHeight = `${scrollerStyles.getPropertyValue("max-height") as unknown as number - parentScroller.clientHeight}px`
                }
            }
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

        if (!State.isRunning(followedExecution.value!.state.current)) {
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
    }, {immediate: true})

    watch(allLogIndicesByLevel, () => {
        emit("log-indices-by-level", allLogIndicesByLevel.value)
    })

    watch(() => props.logCursor, (newValue) => {
        if (newValue !== undefined) {
            scrollToLog(newValue)
        }
    })

    onMounted(() => {
        throttledExecutionUpdate.value = throttle((executionEvent: MessageEvent) => {
            targetExecution.value = JSON.parse(executionEvent.data)
        }, 500)

        if (props.targetExecutionId) {
            followExecution(props.targetExecutionId)
        }

        autoExpandBasedOnSettings()
    })

    onBeforeUnmount(() => {
        closeLogsSSE()
    })

    function fileUrl(path: string) {
        return `${apiUrl()}/executions/${followedExecution.value!.id}/file?path=${path}`
    }

    async function fetchAndStoreLogFileSize(path: string) {
        if (logFileSizeByPath.value[path] !== undefined) {
            return
        }

        const axiosResponse = await $http(
            `${apiUrl()}/executions/${followedExecution.value!.id}/file/metas?path=${path}`,
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
            if (isSubflow(taskRun) && !allowAutoExpandSubflowsProp.value) {
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

    function shouldDisplayLogs(taskRun: Record<string, any>) {
        const uid = attemptUid(
            taskRun.id,
            selectedAttemptNumberByTaskRunId.value[taskRun.id],
        )
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
        executionsStore
            .followExecution({id: executionId, rawSSE: true}, t)
            .then((sse: { close: () => void; onmessage: ((e: MessageEvent) => void) | null }) => {
                executionSSE.value = sse
                executionSSE.value.onmessage = (executionEvent: MessageEvent) => {
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

    function followLogs(executionId: string) {
        executionsStore.followLogs({id: executionId}).then((sse: { close: () => void; onmessage: ((e: MessageEvent) => void) | null; onerror: ((e: Event) => void) | null }) => {
            logsSSE.value = sse

            logsSSE.value.onmessage = (event: MessageEvent) => {
                // we are receiving a first "fake" event to force initializing the connection: ignoring it
                if (event.lastEventId !== "start") {
                    logsBuffer.value = logsBuffer.value.concat(
                        JSON.parse(event.data),
                    )
                }

                clearTimeout(timeout.value)
                timeout.value = setTimeout(() => {
                    timer.value = moment()
                    rawLogs.value = deduplicateLogs(rawLogs.value.concat(logsBuffer.value))
                    logsBuffer.value = []
                    scrollToBottomFailedTask()
                }, 100)

                // force at least 1 logs refresh / 500ms
                if (moment().diff(timer.value, "seconds") > 0.5) {
                    clearTimeout(timeout.value)
                    timer.value = moment()
                    rawLogs.value = deduplicateLogs(rawLogs.value.concat(logsBuffer.value))
                    logsBuffer.value = []
                    scrollToBottomFailedTask()
                }
            }

            logsSSE.value.onerror = () => {
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

    function isSubflow(taskRun: Record<string, any>) {
        return taskRun.outputs?.executionId
    }

    function shouldDisplaySubflow(taskRunIndex: number, taskRun: Record<string, any>) {
        const subflowExecutionId = taskRun.outputs.executionId
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
            logsScrollerRefs.value?.[uid]?.scrollToBottom(),
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

            subflowLogsElements?.forEach((subflowLogs) =>
                subflowLogs?.expandAll(),
            )
        }
    }

    function collapseAll() {
        shownAttemptsUid.value = []
    }

    function attemptUid(taskRunId: string, attemptNumber: number) {
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

    function uniqueTaskRunDisplayFilter(currentTaskRun: Record<string, any>) {
        return !(props.taskRunId && props.taskRunId !== currentTaskRun.id)
    }

    function loadLogs(executionId: string) {
        executionsStore
            .loadLogs({
                executionId,
                params: {
                    minLevel: levelProp.value,
                    taskId: taskRunById.value[props.taskRunId!]?.taskId,
                },
            })
            .then((logs: { results?: Array<Record<string, any>> } | Array<Record<string, any>>) => {
                // `loadLogs` returns a paginated response `{ results, total }`, and `rawLogs` must be an array of log lines.
                rawLogs.value = (logs as any)?.results ?? logs ?? []
                // Discard any buffered SSE logs to prevent duplicates after the full REST fetch replaces `rawLogs`.
                logsBuffer.value = []
            })
    }

    function attempts(taskRun: Record<string, any>) {
        if (
            followedExecution.value!.state.current === State.RUNNING ||
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

    function swapDisplayedAttempt(event: { taskRunId: string; attemptNumber: number }) {
        const {taskRunId, attemptNumber: newDisplayedAttemptNumber} =
            event
        shownAttemptsUid.value = shownAttemptsUid.value.map((uid) =>
            uid.startsWith(`${taskRunId}-`)
                ? attemptUid(taskRunId, newDisplayedAttemptNumber)
                : uid,
        )

        selectedAttemptNumberByTaskRunId.value[taskRunId] =
            newDisplayedAttemptNumber
    }

    function emitLogCursor(logCursorVal: string) {
        emit("log-cursor", logCursorVal)
    }

    function childLogIndicesByLevel(taskRunIndex: number, logIndex: number, logIndicesByLevel: Record<string, string[]>) {
        childrenLogIndicesByLevelByChildUid.value[
            `${taskRunIndex}/${logIndex}`
        ] = logIndicesByLevel
    }

    function logsScrollerRef(el: any, ...ids: (number | string)[]) {
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
            subflowTaskRunDetailsRefs.value?.[
                split[0] + "/" + split[1]
            ]?.scrollToLog(split.slice(2).join("/"))
        }
    }

    function deduplicateLogs(logs: Array<Record<string, any>>) {
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

    // expose methods for parent components to call
    defineExpose({
        toggleExpandCollapseAll,
        expandAll,
        scrollToLog,
    })
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

<template>
    <div class="debug-overlay">
        <section
            v-if="shouldRender"
            v-show="isOpen"
            role="region"
            :aria-labelledby="headingId"
            class="failure-debug-panel"
            @keydown="onKeydown"
        >
            <header class="failure-debug-panel__header">
                <div class="failure-debug-panel__heading">
                    <h3 :id="headingId" ref="panelHeadingRef" tabindex="-1">
                        {{ $t("failureDebugPanel.title") }}
                    </h3>
                    <p v-if="focusedTaskRun" class="failure-debug-panel__subtitle">
                        {{ $t("failureDebugPanel.subtitle", {taskId: focusedTaskRun.taskId}) }}
                        <code v-if="focusedTaskRun.value" class="failure-debug-panel__value">{{ focusedTaskRun.value }}</code>
                        <span v-if="focusedAttemptCount > 1" class="failure-debug-panel__attempt">
                            {{ $t("attempt") }} {{ focusedAttemptIndex + 1 }}/{{ focusedAttemptCount }}
                        </span>
                    </p>
                </div>
                <KsIconButton :tooltip="$t('close')" placement="top" @click="close">
                    <Close />
                </KsIconButton>
            </header>

            <div
                v-if="failedTaskRuns.length > 1"
                role="tablist"
                :aria-label="$t('failureDebugPanel.switcher.label')"
                class="failure-switcher"
            >
                <button
                    v-for="taskRun in failedTaskRuns"
                    :key="taskRun.id"
                    type="button"
                    role="tab"
                    :aria-selected="taskRun.id === focusedId ? 'true' : 'false'"
                    :tabindex="taskRun.id === focusedId ? 0 : -1"
                    class="failure-switcher__tab"
                    :class="{'is-active': taskRun.id === focusedId}"
                    @click="focusFailure(taskRun.id)"
                >
                    <KsExecutionStatus size="small" :status="taskRun.state.current" tabindex="-1" />
                    <code>{{ taskRun.taskId }}</code>
                    <code v-if="taskRun.value" class="failure-switcher__value">{{ taskRun.value }}</code>
                </button>
            </div>

            <KsCard v-if="focusedTaskRun" shadow="never" class="failure-debug-panel__error">
                <template #header>
                    <h4>{{ $t("error") }}</h4>
                </template>
                <FailureErrorSummary :text="focusedErrorText" :loading="focusedErrorLoading" />
            </KsCard>

            <div v-if="focusedTaskRun" class="failure-debug-panel__actions">
                <p class="failure-debug-panel__restart-caption">{{ $t("failureDebugPanel.restart.caption") }}</p>
                <div class="failure-debug-panel__actions-buttons">
                    <div class="failure-debug-panel__actions-secondary">
                        <KsButton v-if="canUseCopilot" :icon="AiIcon" link :disabled="!focusedErrorText" @click="askCopilot">
                            {{ $t("failureDebugPanel.copilot.ask") }}
                        </KsButton>
                        <KsButton v-if="canEditFlow" tag="router-link" :to="editFlowRoute" :icon="Pencil" link>
                            {{ $t("edit flow") }}
                        </KsButton>
                        <SubFlowLink
                            v-if="focusedSubflowExecutionId"
                            component="KsButton"
                            link
                            showLabel
                            tabExecution="gantt"
                            :executionId="focusedSubflowExecutionId"
                        />
                        <KsButton :icon="ContentCopy" link :disabled="!focusedErrorText" @click="copyFocusedError">
                            {{ $t("failureDebugPanel.copyError") }}
                        </KsButton>
                    </div>
                    <Restart
                        component="KsButton"
                        isReplay
                        tooltipPosition="bottom"
                        :execution="execution"
                        :taskRun="focusedTaskRun"
                        :attemptIndex="focusedAttemptIndex"
                    />
                </div>
            </div>

            <KsCard shadow="never" class="failure-debug-panel__timeline">
                <template #header>
                    <h4>{{ $t("failureDebugPanel.miniTimeline.title") }}</h4>
                </template>
                <div class="failure-debug-panel__timeline-body">
                    <p class="failure-debug-panel__hint">{{ $t("failureDebugPanel.miniTimeline.dragHint") }}</p>
                    <FailureMiniTimeline
                        v-if="focusedId"
                        ref="miniTimelineRef"
                        :nodes="timelineNodes"
                        :focusedId="focusedId"
                        @focus-task="focusFailureFromNeighbor"
                        @select-range="onSelectRange"
                    />
                </div>
            </KsCard>

            <KsCard v-if="focusedTaskRun" shadow="never" class="failure-debug-panel__context">
                <KsTabs v-model="activeContextTab" type="box">
                    <KsTabPane name="stateHistory" :label="$t('failureDebugPanel.stateHistory.title')">
                        <div class="failure-debug-panel__tab-pane failure-debug-panel__stacked">
                            <div>
                                <h4>{{ $t("failureDebugPanel.attempts.title") }}</h4>
                                <FailureAttempts :taskRun="focusedTaskRun" />
                            </div>
                            <div>
                                <h4>{{ $t("failureDebugPanel.stateHistory.title") }}</h4>
                                <FailureStateHistory :taskRun="focusedTaskRun" />
                            </div>
                        </div>
                    </KsTabPane>
                    <KsTabPane name="resolvedConfig" :label="$t('failureDebugPanel.resolvedConfig.title')">
                        <div class="failure-debug-panel__tab-pane">
                            <FailureResolvedConfig
                                :rawBlock="focusedRawTaskBlock"
                                :flowLoading="focusedFlowLoading"
                                :flowError="focusedFlowError"
                                :executionId="execution.id"
                                :taskRunId="focusedTaskRun.id"
                            />
                        </div>
                    </KsTabPane>
                    <KsTabPane name="inputsOutputs" :label="$t('failureDebugPanel.inputsOutputs.title')">
                        <div class="failure-debug-panel__tab-pane failure-debug-panel__stacked">
                            <div>
                                <h4>{{ $t("failureDebugPanel.executionInputs.title") }}</h4>
                                <FailureExecutionInputs
                                    :inputIds="flowInputIds"
                                    :executionId="execution.id"
                                    :taskRunId="focusedTaskRun.id"
                                />
                            </div>
                            <div>
                                <h4>{{ $t("failureDebugPanel.upstreamOutputs.title") }}</h4>
                                <FailureUpstreamOutputs
                                    :referencedTaskIds="referencedOutputTaskIds"
                                    :taskRunList="taskRunList"
                                    :executionId="execution.id"
                                />
                            </div>
                        </div>
                    </KsTabPane>
                </KsTabs>
            </KsCard>

            <div class="failure-debug-panel__grid">
                <KsCard shadow="never">
                    <template #header>
                        <h4>{{ $t("failureDebugPanel.structuralImpact.title") }}</h4>
                    </template>
                    <FailureStructuralImpact
                        v-if="focusedId"
                        :nodes="structuralNodes"
                        :focusedId="focusedId"
                        :flow="flow"
                        :rawBlocks="structuralRawBlocks"
                        @focus="focusFailureFromNeighbor"
                    />
                </KsCard>

                <KsCard shadow="never">
                    <template #header>
                        <h4>{{ $t("failureDebugPanel.logs.title") }}</h4>
                    </template>
                    <FailureLogPanel
                        v-if="focusedTaskRun"
                        :executionId="execution.id"
                        :executionKind="execution.kind ?? undefined"
                        :taskRunId="focusedTaskRun.id"
                        :timeRange="timeRange"
                    />
                </KsCard>
            </div>
        </section>

        <div role="status" aria-live="polite" class="visually-hidden">
            {{ announcement }}
        </div>

        <div class="debug-underlay" :class="{'is-dimmed': shouldRender && isOpen}">
            <!-- The reopen trigger is placed by the caller (next to "Copy All Logs" in Gantt.vue) -->
            <slot
                :shouldRender="shouldRender"
                :isOpen="isOpen"
                :reopen="reopen"
                :setReopenRef="setReopenWrapperRef"
            />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, nextTick, ref, watch, type ComponentPublicInstance} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute} from "vue-router"
    import {State, KsExecutionStatus, KsTabs, KsTabPane} from "@kestra-io/design-system"
    import Close from "vue-material-design-icons/Close.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import Pencil from "vue-material-design-icons/Pencil.vue"

    import AiIcon from "../../ai/AiIcon.vue"
    import SubFlowLink from "../../flows/SubFlowLink.vue"
    import Restart from "../overview/components/actions/Restart.vue"
    import FailureErrorSummary from "./FailureErrorSummary.vue"
    import FailureMiniTimeline from "./FailureMiniTimeline.vue"
    import FailureAttempts from "./FailureAttempts.vue"
    import FailureStateHistory from "./FailureStateHistory.vue"
    import FailureResolvedConfig from "./FailureResolvedConfig.vue"
    import FailureExecutionInputs from "./FailureExecutionInputs.vue"
    import FailureUpstreamOutputs from "./FailureUpstreamOutputs.vue"
    import FailureStructuralImpact from "./FailureStructuralImpact.vue"
    import FailureLogPanel from "./FailureLogPanel.vue"

    import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
    import {flow as fetchFlow} from "@kestra-io/kestra-sdk/flows"
    import resource from "../../../models/resource"
    import action from "../../../models/action"
    import * as Utils from "../../../utils/utils"
    import {useToast} from "../../../utils/toast"
    import {useExecutionsStore, type Execution} from "../../../stores/executions"
    import {useAuthStore} from "override/stores/auth"
    import {useMiscStore} from "override/stores/misc"
    import type {TimeRange} from "../../../composables/useTimeRangeSelection"
    import type {FailureTaskRun, StructuralNode} from "./types"

    const MAX_STRUCTURAL_NEIGHBORS = 6

    const props = defineProps<{
        execution: Execution
        flow?: unknown
    }>()

    const taskRunList = computed(() => (props.execution.taskRunList ?? []) as FailureTaskRun[])

    const {t} = useI18n()
    const route = useRoute()
    const toast = useToast()
    const authStore = useAuthStore()
    const miscStore = useMiscStore()
    const executionsStore = useExecutionsStore()

    const headingId = "failure-debug-panel-heading"

    const panelHeadingRef = ref<HTMLElement>()
    const reopenWrapperRef = ref<HTMLElement>()
    function setReopenWrapperRef(el: Element | ComponentPublicInstance | null) {
        reopenWrapperRef.value = (el as HTMLElement) ?? undefined
    }
    const miniTimelineRef = ref<InstanceType<typeof FailureMiniTimeline>>()

    const isOpen = ref(false)
    const hasAnnounced = ref(false)
    const announcement = ref("")
    const timeRange = ref<TimeRange | undefined>(undefined)
    const focusedId = ref<string | undefined>(undefined)
    const activeContextTab = ref("stateHistory")

    const ts = (date: string): number => new Date(date).getTime()

    const isDebuggableExecution = computed(() =>
        props.execution.kind !== "PLAYGROUND" &&
        (props.execution.state?.current === State.FAILED || props.execution.state?.current === State.KILLED),
    )

    function isFailedTaskRun(taskRun: FailureTaskRun): boolean {
        return taskRun.state?.current === State.FAILED || taskRun.state?.current === State.KILLED
    }

    const failedTaskRuns = computed<FailureTaskRun[]>(() =>
        taskRunList.value
            .filter(isFailedTaskRun)
            .slice()
            .sort((a, b) => ts(a.state.histories[0].date) - ts(b.state.histories[0].date)),
    )

    const shouldRender = computed(() => isDebuggableExecution.value && failedTaskRuns.value.length > 0)

    const focusedTaskRun = computed(() => failedTaskRuns.value.find((taskRun) => taskRun.id === focusedId.value))

    const focusedSubflowExecutionId = computed(() => {
        const executionId = focusedTaskRun.value?.outputs?.executionId
        return typeof executionId === "string" && executionId.length > 0 ? executionId : undefined
    })

    const focusedAttempts = computed(() => focusedTaskRun.value?.attempts ?? [])
    const focusedAttemptCount = computed(() => focusedAttempts.value.length)
    const focusedAttemptIndex = computed(() =>
        focusedAttemptCount.value > 0 ? focusedAttemptCount.value - 1 : 0,
    )

    const canUseCopilot = computed(() => !!authStore.user?.hasAny(resource.COPILOT))

    const canEditFlow = computed(() =>
        authStore.user?.isAllowed(resource.FLOW, action.UPDATE, props.execution.namespace),
    )

    const editFlowRoute = computed(() => ({
        name: "flows/update/edit",
        params: {
            namespace: props.execution.namespace,
            id: props.execution.flowId,
            tenant: route.params.tenant as string,
        },
        query: focusedTaskRun.value ? {editTask: focusedTaskRun.value.taskId} : undefined,
    }))

    const structuralNodes = computed<StructuralNode[]>(() => {
        const focused = focusedTaskRun.value
        if (!focused) return []

        const byId = new Map(taskRunList.value.map((taskRun) => [taskRun.id, taskRun]))
        const parent = focused.parentTaskRunId ? byId.get(focused.parentTaskRunId) : undefined
        const children = taskRunList.value.filter((taskRun) => taskRun.parentTaskRunId === focused.id)
        const siblings = taskRunList.value
            .filter((taskRun) => taskRun.id !== focused.id && taskRun.parentTaskRunId === focused.parentTaskRunId)
            .sort((a, b) =>
                Math.abs(ts(a.state.histories[0].date) - ts(focused.state.histories[0].date)) -
                Math.abs(ts(b.state.histories[0].date) - ts(focused.state.histories[0].date)),
            )

        const nodes: StructuralNode[] = [{taskRun: focused, relation: "focused"}]
        const seen = new Set([focused.id])

        const add = (taskRun: FailureTaskRun | undefined, relation: StructuralNode["relation"]) => {
            if (!taskRun || seen.has(taskRun.id) || nodes.length >= MAX_STRUCTURAL_NEIGHBORS) return
            seen.add(taskRun.id)
            nodes.push({taskRun, relation})
        }

        add(parent, "parent")
        children.forEach((child) => add(child, "child"))
        siblings.forEach((sibling) => add(sibling, "sibling"))

        return nodes
    })

    const timelineNodes = computed<StructuralNode[]>(() =>
        [...structuralNodes.value].sort((a, b) => ts(a.taskRun.state.histories[0].date) - ts(b.taskRun.state.histories[0].date)),
    )

    const focusedFlow = ref<{source?: string; inputs?: Array<{id: string}>} | undefined>(undefined)
    const focusedFlowLoading = ref(false)
    const focusedFlowError = ref(false)

    watch(
        () => `${props.execution.namespace}/${props.execution.flowId}/${props.execution.flowRevision}`,
        async () => {
            focusedFlowLoading.value = true
            focusedFlowError.value = false
            focusedFlow.value = undefined
            try {
                focusedFlow.value = await fetchFlow({
                    namespace: props.execution.namespace,
                    id: props.execution.flowId,
                    revision: props.execution.flowRevision,
                    source: true,
                })
            } catch {
                focusedFlowError.value = true
            } finally {
                focusedFlowLoading.value = false
            }
        },
        {immediate: true},
    )

    const focusedRawTaskBlock = computed<string | undefined>(() => {
        const source = focusedFlow.value?.source
        const taskId = focusedTaskRun.value?.taskId
        if (!source || !taskId) return undefined
        return YAML_UTILS.extractBlock({source, section: "tasks", key: taskId})
    })

    const flowInputIds = computed<string[]>(() => (focusedFlow.value?.inputs ?? []).map((input) => input.id))

    const referencedOutputTaskIds = computed<string[]>(() => {
        const block = focusedRawTaskBlock.value
        if (!block) return []
        const matches = block.matchAll(/\{\{[^}]*\boutputs(?:\[['"]|\.)([A-Za-z0-9_-]+)/g)
        return [...new Set([...matches].map((match) => match[1]))]
    })

    const structuralRawBlocks = computed<Record<string, string | undefined>>(() => {
        const source = focusedFlow.value?.source
        if (!source) return {}
        return Object.fromEntries(
            structuralNodes.value.map((node) => [
                node.taskRun.id,
                YAML_UTILS.extractBlock({source, section: "tasks", key: node.taskRun.taskId}),
            ]),
        )
    })

    watch(
        failedTaskRuns,
        (list) => {
            if (list.length === 0) {
                focusedId.value = undefined
                return
            }
            if (!focusedId.value || !list.some((taskRun) => taskRun.id === focusedId.value)) {
                focusedId.value = list[0].id
            }
        },
        {immediate: true},
    )

    watch(
        shouldRender,
        (value) => {
            if (value && !hasAnnounced.value) {
                announcement.value = t("failureDebugPanel.announce", {
                    count: failedTaskRuns.value.length,
                    taskId: failedTaskRuns.value[0]?.taskId,
                })
                hasAnnounced.value = true
            }
        },
        {immediate: true},
    )

    watch(isOpen, async (open) => {
        await nextTick()
        if (open) {
            panelHeadingRef.value?.focus()
        } else {
            (reopenWrapperRef.value?.querySelector("button") as HTMLElement | null)?.focus()
        }
    })

    function focusFailure(id: string) {
        if (id === focusedId.value) return
        focusedId.value = id
        timeRange.value = undefined
        miniTimelineRef.value?.clearSelection()
    }

    function focusFailureFromNeighbor(id: string) {
        if (!failedTaskRuns.value.some((taskRun) => taskRun.id === id)) return
        focusFailure(id)
    }

    function onSelectRange(range: TimeRange | undefined) {
        timeRange.value = range
    }

    function close() {
        isOpen.value = false
    }

    function reopen() {
        isOpen.value = true
    }

    function onKeydown(event: KeyboardEvent) {
        if (event.key !== "Escape") return
        if (timeRange.value) {
            event.stopPropagation()
            miniTimelineRef.value?.clearSelection()
            timeRange.value = undefined
            return
        }
        if (isOpen.value) {
            event.stopPropagation()
            close()
        }
    }

    async function fetchErrorText(taskRunId: string): Promise<string> {
        const response = await executionsStore
            .loadLogs({
                store: false,
                executionId: props.execution.id,
                params: {taskRunId, minLevel: "ERROR"},
                showMessageOnError: false,
            })
            .catch(() => [])

        const results = ((response as {results?: unknown[]})?.results ?? response ?? []) as Array<{level?: string; message?: string}>
        const errors = results.filter((log) => (log.level ?? "").toString().toUpperCase() === "ERROR" && (log.message ?? "").length > 0)
        if (errors.length > 0) return errors.map((log) => log.message).join("\n")

        return [...results].reverse().find((log) => (log.message ?? "").length > 0)?.message ?? ""
    }

    const focusedErrorText = ref("")
    const focusedErrorLoading = ref(false)

    const loadedErrorTaskRunId = ref<string | undefined>(undefined)

    watch(
        [() => focusedTaskRun.value?.id, isOpen],
        async ([taskRunId, open]) => {
            if (!taskRunId || !open || loadedErrorTaskRunId.value === taskRunId) return

            loadedErrorTaskRunId.value = taskRunId
            focusedErrorText.value = ""
            focusedErrorLoading.value = true
            try {
                const text = await fetchErrorText(taskRunId)
                if (focusedTaskRun.value?.id === taskRunId) focusedErrorText.value = text
            } finally {
                if (focusedTaskRun.value?.id === taskRunId) focusedErrorLoading.value = false
            }
        },
        {immediate: true},
    )

    function askCopilot() {
        const taskRun = focusedTaskRun.value
        const errorLines = focusedErrorText.value
        if (!taskRun || !errorLines) return

        const prompt = `Fix the task ${taskRun.taskId} as it generated the following error:\n${errorLines}`
        miscStore.promptCopilot(prompt, {title: t("ai.copilot.fixThread.task", {id: taskRun.taskId}), newThread: true})
    }

    async function copyFocusedError() {
        const errorLines = focusedErrorText.value
        if (!errorLines) return
        await Utils.copy(errorLines)
        toast.success(t("copied"))
    }
</script>

<style scoped lang="scss">
    .debug-overlay {
        position: relative;
    }

    .debug-underlay {
        &.is-dimmed {
            filter: blur(1.5px) saturate(0.7);
            opacity: 0.55;
            pointer-events: none;
            user-select: none;
        }
    }

    .failure-debug-panel {
        position: relative;
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-4);
        margin-bottom: calc(-1 * var(--ks-spacing-6));
        padding: var(--ks-spacing-5);
        background: var(--ks-bg-elevated);
        border: 1px solid var(--ks-status-border-failed);
        border-radius: var(--ks-radius-xl);
        box-shadow: 0 8px 24px 0 var(--ks-shadow-elevated);
        z-index: 5;

        h3, h4 {
            margin: 0;
        }

        h4 {
            font-size: var(--ks-font-size-sm);
            color: var(--ks-text-primary);
        }

        h3:focus-visible, h3:focus {
            outline: none;
        }
    }

    .failure-debug-panel__header {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: var(--ks-spacing-3);
    }

    .failure-debug-panel__heading h3 {
        font-size: var(--ks-font-size-lg);
        color: var(--ks-text-primary);
    }

    .failure-debug-panel__subtitle {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--ks-spacing-2);
        margin: var(--ks-spacing-1) 0 0;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
    }

    .failure-debug-panel__value {
        padding: 0 var(--ks-spacing-1);
        background: var(--ks-bg-active);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        color: var(--ks-text-primary);
        font-size: var(--ks-font-size-xs);
    }

    .failure-debug-panel__attempt {
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
    }

    .failure-switcher {
        display: flex;
        flex-wrap: wrap;
        gap: var(--ks-spacing-2);
    }

    .failure-switcher__tab {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-1) var(--ks-spacing-3);
        background: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        cursor: pointer;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);

        &.is-active {
            border-color: var(--ks-status-border-failed);
            color: var(--ks-text-primary);
        }

        &:hover {
            background: var(--ks-bg-hover);
        }
    }

    .failure-switcher__value {
        padding: 0 var(--ks-spacing-1);
        background: var(--ks-bg-active);
        border-radius: var(--ks-radius-base);
        color: var(--ks-text-primary);
    }

    .failure-debug-panel__actions {
        display: flex;
        align-items: center;
        justify-content: space-between;
        flex-wrap: wrap;
        gap: var(--ks-spacing-3);
    }

    .failure-debug-panel__actions-buttons {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: var(--ks-spacing-2);
    }

    .failure-debug-panel__actions-secondary {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: var(--ks-spacing-1);
        padding: var(--ks-spacing-1);
        background: var(--ks-bg-active);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
    }

    .failure-debug-panel__restart-caption {
        margin: 0;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
    }

    .failure-debug-panel__timeline-body {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
    }

    .failure-debug-panel__grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(18rem, 1fr));
        gap: var(--ks-spacing-4);
        align-items: start;
    }

    .failure-debug-panel__tab-pane {
        padding-top: var(--ks-spacing-4);
    }

    .failure-debug-panel__stacked {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-4);
    }

    .failure-debug-panel__hint {
        margin: 0;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
    }

    .visually-hidden {
        position: absolute;
        width: 1px;
        height: 1px;
        padding: 0;
        margin: -1px;
        overflow: hidden;
        clip: rect(0, 0, 0, 0);
        white-space: nowrap;
        border: 0;
    }
</style>

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
                </button>
            </div>

            <div v-if="focusedTaskRun" class="failure-debug-panel__actions">
                <div class="failure-debug-panel__actions-row">
                    <Restart
                        component="KsButton"
                        isReplay
                        tooltipPosition="bottom"
                        :execution="execution"
                        :taskRun="focusedTaskRun"
                        :attemptIndex="focusedAttemptIndex"
                    />
                    <div class="failure-debug-panel__actions-secondary">
                        <KsButton v-if="canUseCopilot" :icon="AiIcon" link @click="askCopilot">
                            {{ $t("failureDebugPanel.copilot.ask") }}
                        </KsButton>
                        <KsButton v-if="canEditFlow" tag="router-link" :to="editFlowRoute" :icon="Pencil" link>
                            {{ $t("edit flow") }}
                        </KsButton>
                        <KsButton :icon="ContentCopy" link @click="copyFocusedError">
                            {{ $t("failureDebugPanel.copyError") }}
                        </KsButton>
                    </div>
                </div>
                <p class="failure-debug-panel__restart-caption">{{ $t("failureDebugPanel.restart.caption") }}</p>
            </div>

            <KsCard shadow="never" class="failure-debug-panel__timeline">
                <template #header>
                    <h4>{{ $t("failureDebugPanel.miniTimeline.title") }}</h4>
                </template>
                <p class="failure-debug-panel__hint">{{ $t("failureDebugPanel.miniTimeline.dragHint") }}</p>
                <FailureMiniTimeline
                    v-if="focusedId"
                    ref="miniTimelineRef"
                    :nodes="structuralNodes"
                    :focusedId="focusedId"
                    @focus-task="focusFailureFromNeighbor"
                    @select-range="onSelectRange"
                />
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

        <div v-if="shouldRender && !isOpen" ref="reopenWrapperRef" class="failure-debug-reopen">
            <KsButton :icon="BugOutline" @click="reopen">
                {{ $t("failureDebugPanel.reopen", {count: failedTaskRuns.length}) }}
            </KsButton>
        </div>

        <div role="status" aria-live="polite" class="visually-hidden">
            {{ announcement }}
        </div>

        <div class="debug-underlay" :class="{'is-dimmed': shouldRender && isOpen}">
            <slot />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, nextTick, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute} from "vue-router"
    import {State, KsExecutionStatus} from "@kestra-io/design-system"
    import Close from "vue-material-design-icons/Close.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import BugOutline from "vue-material-design-icons/BugOutline.vue"
    import Pencil from "vue-material-design-icons/Pencil.vue"

    import AiIcon from "../../ai/AiIcon.vue"
    import Restart from "../overview/components/actions/Restart.vue"
    import FailureMiniTimeline from "./FailureMiniTimeline.vue"
    import FailureStructuralImpact from "./FailureStructuralImpact.vue"
    import FailureLogPanel from "./FailureLogPanel.vue"

    import resource from "../../../models/resource"
    import action from "../../../models/action"
    import * as Utils from "../../../utils/utils"
    import {useToast} from "../../../utils/toast"
    import {useExecutionsStore, type Execution} from "../../../stores/executions"
    import {useAuthStore} from "override/stores/auth"
    import {useMiscStore} from "override/stores/misc"
    import type {TimeRange} from "../../../composables/useTimeRangeSelection"
    import type {FailureTaskRun, StructuralNode} from "./types"

    // Structural impact and the mini-timeline stay scoped to a handful of neighbors regardless of
    // execution size, so a very long execution never renders its full task list in this panel.
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
    const miniTimelineRef = ref<InstanceType<typeof FailureMiniTimeline>>()

    // Starts closed: a failed execution is common enough that hijacking the Gantt view on every
    // visit would be disruptive. The reopen affordance below doubles as the initial entry point.
    const isOpen = ref(false)
    const hasAnnounced = ref(false)
    const announcement = ref("")
    const timeRange = ref<TimeRange | undefined>(undefined)
    const focusedId = ref<string | undefined>(undefined)

    const ts = (date: string): number => new Date(date).getTime()

    // Playground runs go through a different (non-persisted) restart path; the restart action
    // below only knows how to replay a real, persisted execution.
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

    const focusedAttemptIndex = computed(() => {
        const attempts = focusedTaskRun.value?.attempts
        return attempts && attempts.length > 0 ? attempts.length - 1 : 0
    })

    const canUseCopilot = computed(() => !!authStore.user?.hasAny(resource.COPILOT))

    const canEditFlow = computed(() =>
        authStore.user?.isAllowed(resource.FLOW, action.UPDATE, props.execution.namespace),
    )

    // The editor is its own child route now, so target it directly: passing `tab` to the parent
    // still resolves through the redirect but logs a discarded-param warning on every render.
    // `editTask` deep-links straight to the failing task's no-code edit tab (see
    // MultiPanelFlowEditorView's onMounted handling), rather than just opening the flow at large.
    // Sourced from `props.execution` rather than `route.params`: the panel already receives the
    // execution's own namespace/flowId as props, so there is no need to depend on this component
    // always being mounted under a route that happens to carry those same values as URL params.
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

    // No `immediate: true`: focus should only move in response to the user actually opening or
    // closing the panel, never just because the component mounted with the panel closed.
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

    async function loadFocusedErrorText(): Promise<string> {
        const taskRun = focusedTaskRun.value
        if (!taskRun) return ""

        const response = await executionsStore
            .loadLogs({
                store: false,
                executionId: props.execution.id,
                params: {taskRunId: taskRun.id, minLevel: "ERROR"},
                showMessageOnError: false,
            })
            .catch(() => [])

        const results = ((response as {results?: unknown[]})?.results ?? response ?? []) as Array<{level?: string; message?: string}>
        const errors = results.filter((log) => (log.level ?? "").toString().toUpperCase() === "ERROR" && (log.message ?? "").length > 0)
        if (errors.length > 0) return errors.map((log) => log.message).join("\n")

        return [...results].reverse().find((log) => (log.message ?? "").length > 0)?.message ?? ""
    }

    async function askCopilot() {
        const taskRun = focusedTaskRun.value
        if (!taskRun) return

        const errorLines = await loadFocusedErrorText()
        const prompt = `Fix the task ${taskRun.taskId} as it generated the following error:\n${errorLines}`
        miscStore.promptCopilot(prompt, {title: t("ai.copilot.fixThread.task", {id: taskRun.taskId}), newThread: true})
    }

    async function copyFocusedError() {
        const errorLines = await loadFocusedErrorText()
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
        box-shadow: 0 12px 32px 0 var(--ks-shadow-elevated);
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
        margin: var(--ks-spacing-1) 0 0;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
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

    .failure-debug-panel__actions {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
    }

    .failure-debug-panel__actions-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        flex-wrap: wrap;
        gap: var(--ks-spacing-3);
    }

    // The three utility actions share one visual language (link-style KsButton, icon + label)
    // rather than mixing a labelled button with a bare icon button — same weight, same shape.
    .failure-debug-panel__actions-secondary {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: var(--ks-spacing-1);
    }

    .failure-debug-panel__restart-caption {
        margin: 0;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
    }

    // The mini-timeline gets its own full-width row rather than sharing a 3-column grid with the
    // taller structural-impact/logs cards: it never grows to fill a stretched grid row (its own
    // content doesn't expand), which used to leave a block of dead space under a couple of short
    // bars. Full width also gives long task ids more room on the label side of the track.
    .failure-debug-panel__timeline {
        :deep(.kel-card__body) {
            display: flex;
            flex-direction: column;
            gap: var(--ks-spacing-2);
        }
    }

    .failure-debug-panel__grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(18rem, 1fr));
        gap: var(--ks-spacing-4);
        align-items: start;
    }

    .failure-debug-panel__hint {
        margin: 0;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
    }

    .failure-debug-reopen {
        position: relative;
        z-index: 5;
        margin-bottom: var(--ks-spacing-4);
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

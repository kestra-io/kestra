<template>
    <div ref="vueFlow" class="vueflow">
        <slot name="top-bar" />
        <Topology
            :key="`topology-${!!executionsStore.execution?.id}`"
            :id="vueflowId"
            :isHorizontal="isHorizontal"
            :isReadOnly="isReadOnly"
            :isAllowedEdit="isAllowedEdit"
            :source="source"
            :toggleOrientationButton="toggleOrientationButton"
            :flowGraph="augmentedFlowGraph"
            :flowId="flowId"
            :namespace="namespace"
            :expandedSubflows="props.expandedSubflows"
            :icons="pluginsStore.icons"
            :loadIcon="pluginsStore.loadIcon"
            :execution="executionsStore.execution"
            :subflowsExecutions="executionsStore.graphExecutions"
            :playgroundEnabled="playgroundStore.enabled"
            :playgroundReadyToStart="playgroundStore.readyToStart"
            :replayEnabled="replayEnabled"
            :validationIssuesByTask="validationIssuesByTask"
            :focusedTaskId="focusedTaskId"
            :loopLanes="loopLanes"
            :artifactCounts="artifactCounts"
            @loop-step="({uid, delta}) => loopScoping.stepLane(uid, delta)"
            @loop-scope-failed="({uid}) => loopScoping.scopeFirstFailure(uid)"
            @toggle-orientation="toggleOrientation"
            @edit="onEditTask"
            @delete="onDelete"
            @duplicate="onDuplicate"
            @addTrigger="onAddTrigger"
            @editFlow="flowPropertiesOpen = true"
            :flowDescription="flowDescription"
            :flowLabels="flowLabels"
            @open-link="openFlow"
            @show-logs="showLogs"
            @show-outputs="showOutputs"
            @show-artifacts="onShowArtifacts"
            @replay-task="onReplayTask"
            @show-description="showDescription"
            @show-condition="showCondition"
            @on-add-flowable-error="onAddFlowableError"
            @add-task="onCreateNewTask"
            @move-task="onMoveTask"
            @expand-subflow="expandSubflow"
            @run-task="playgroundStore.runUntilTask($event.task.id)"
        >
            <template #loopScope="{uid, lane}">
                <LoopIterationPicker
                    :lane="lane"
                    :hostExecutionId="loopScoping.hostExecutionId(uid)"
                    @select="loopScoping.scopeLane(uid, $event)"
                    @clear="loopScoping.clearLane(uid)"
                    @step="loopScoping.stepLane(uid, $event)"
                />
            </template>
            <template v-if="hasLoopLanes" #scopeBar="{setLanesCollapsed, collapsedLanes}">
                <LoopScopeBar
                    :entries="loopScoping.scopeTrail.value"
                    :canJumpToFailure="Boolean(loopScoping.firstFailedLaneUid())"
                    :failuresOnly="isFailuresOnly(collapsedLanes)"
                    :failure="loopScoping.scopeFailure.value"
                    @clear="loopScoping.clearScope()"
                    @retry="loopScoping.retryScope()"
                    @jump-to-failure="jumpToFirstFailure"
                    @toggle-failures-only="toggleFailuresOnly(setLanesCollapsed, collapsedLanes)"
                />
            </template>
            <template #taskActions="taskProps">
                <template v-for="context in [taskActionsContext(taskProps.execution?.id, taskProps.taskRun?.id, taskProps.taskRuns.map(run => run.id))]" :key="taskProps.taskRun?.id">
                    <KsIconButton
                        v-if="isReadOnly && context && taskProps.variant === 'task'"
                        class="node-action-button"
                        :aria-label="$t('task_run_popup.open')"
                        data-test="task-run-popup-trigger"
                        @click.stop="openTaskPopup({execution: context.execution, taskId: taskProps.task?.id, task: taskProps.task, actions: taskProps.actions})"
                    >
                        <InformationOutline />
                    </KsIconButton>
                    <TaskRunActions
                        v-else-if="isReadOnly && context"
                        class="node-action-button"
                        :taskRun="context.taskRun"
                        :taskType="taskProps.task?.type"
                        :taskRuns="context.taskRuns"
                        :execution="context.execution"
                        :flow="flowStore.flow"
                        :nodeActions="taskProps.actions
                            .filter(a => !EXCLUDED_NODE_ACTIONS.includes(a.key))
                            .map((a, i) => i === 0 ? {...a, divided: true} : a)
                        "
                        @follow="$emit('follow', $event)"
                    />
                    <NodeMenu v-else :actions="taskProps.actions" />
                </template>
            </template>
        </Topology>

        <BlockTaskPicker :picker="taskPicker" modal />

        <TaskEditModal
            v-if="modalTarget"
            :task="modalTaskData"
            :taskRaw="modalTaskRaw"
            :section="modalSection"
            :flowId="props.flowId ?? ''"
            :namespace="props.namespace ?? ''"
            :editorKey="modalItemPath"
            :parentPath="modalTarget.parentPath"
            :refPath="modalTarget.refPath"
            :blockSchemaPath="modalTarget.blockSchemaPath"
            :editedPath="modalPath"
            :crumbs="modalCrumbs"
            :creating="modalTarget.creating"
            @update:task="onModalTaskEdited"
            @close="closeModal"
            @open-in-tabs="onModalOpenInTabs"
            @navigate="popModalTo"
            @select-nested="onModalSelectNested"
            @created="resolveCreatedTarget"
        />

        <UndoToast :state="visibleUndoState" @undo="performUndo" />

        <BlockShortcutsDialog v-model:open="shortcutsOpen" :groups="shortcutGroups" />

        <FlowPropertiesModal v-if="flowPropertiesOpen" @close="flowPropertiesOpen = false" />

        <BlockCommandMenu
            v-if="commandMenuOpen"
            :items="commandMenuItems"
            :contextLabel="commandMenuContextLabel"
            @close="commandMenuOpen = false"
        />

        <KsDrawer v-if="isDrawerOpen && selectedTask" v-model="isDrawerOpen">
            <template #header>
                <code>{{ selectedTask.id }}</code>
            </template>
            <KsTabs v-if="isInspectOpen" v-model="inspectTab" type="box" class="inspect-tabs">
                <KsTabPane :label="$t('logs')" name="logs">
                    <div class="tab-body">
                        <TaskRunLogs
                            v-model:level="inspectLogLevel"
                            :executionId="selectedTask.execution?.id"
                            :taskRunIds="(selectedTask.taskRuns ?? []).map(taskRun => taskRun.id)"
                            @follow="emit('follow', $event)"
                        />
                    </div>
                </KsTabPane>
                <KsTabPane :label="$t('outputs')" name="outputs">
                    <div class="tab-body outputs-view">
                        <section
                            v-for="taskRun in selectedTask.taskRuns"
                            :key="taskRun.id"
                            class="taskrun-card"
                            v-ks-loading="isLoadingTaskRunOutputs(taskRun.id)"
                        >
                            <div v-if="selectedTask.taskRuns && selectedTask.taskRuns.length > 1" class="taskrun-card__header">
                                <KsExecutionStatus size="small" :status="taskRun.state.current" />
                                <code class="taskrun-card__value">{{ taskRun.value ?? taskRun.id }}</code>
                            </div>
                            <Vars
                                v-if="taskRunOutputsById[taskRun.id] && Object.keys(taskRunOutputsById[taskRun.id]).length > 0"
                                :data="taskRunOutputsById[taskRun.id]"
                            />
                            <span
                                v-else-if="!isLoadingTaskRunOutputs(taskRun.id)"
                                class="taskrun-card__empty"
                            >{{ $t("no outputs available") }}</span>
                        </section>
                    </div>
                </KsTabPane>
                <KsTabPane :label="$t('metrics')" name="metrics" lazy>
                    <div class="tab-body outputs-view">
                        <section v-for="taskRun in selectedTask.taskRuns" :key="taskRun.id" class="taskrun-card">
                            <div v-if="selectedTask.taskRuns && selectedTask.taskRuns.length > 1" class="taskrun-card__header">
                                <KsExecutionStatus size="small" :status="taskRun.state.current" />
                                <code class="taskrun-card__value">{{ taskRun.value ?? taskRun.id }}</code>
                            </div>
                            <MetricsTable :taskRunId="taskRun.id" :execution="selectedTask.execution">
                                <template #empty>
                                    <span class="taskrun-card__empty">{{ $t("no metrics available") }}</span>
                                </template>
                            </MetricsTable>
                        </section>
                    </div>
                </KsTabPane>
            </KsTabs>
            <div v-if="isReplayPickerOpen" class="replay-picker">
                <span class="replay-picker__hint">{{ $t("replay select taskrun") }}</span>
                <div v-for="taskRun in selectedTask.taskRuns" :key="taskRun.id" class="replay-picker__item">
                    <KsExecutionStatus size="small" :status="taskRun.state.current" />
                    <code class="replay-picker__value">{{ taskRun.value ?? taskRun.id }}</code>
                    <KsButton size="small" :icon="PlayBoxMultiple" @click="openReplayDialog(selectedTask.execution, taskRun)">
                        {{ $t("replay") }}
                    </KsButton>
                </div>
            </div>
            <div v-if="isShowDescriptionOpen">
                <KsMarkdown
                    :content="selectedTask.description"
                />
            </div>
            <div v-if="isShowConditionOpen">
                <KsEditor
                    v-bind="editorBindings"
                    :readOnly="true"
                    :inline="true"
                    :options="{fullHeight: false}"
                    :navbar="false"
                    :modelValue="selectedTask.runIf"
                    lang="yaml"
                    class="detail"
                />
            </div>
        </KsDrawer>

        <TaskRunPopup
            v-if="taskPopup && taskPopupExecution?.taskRunList?.some(run => run.taskId === taskPopup?.taskId)"
            v-model="isTaskPopupOpen"
            :execution="taskPopupExecution"
            :taskId="taskPopup.taskId"
            :taskType="taskPopup.task?.type"
            :description="taskPopup.task ? taskWithSource(taskPopup.task).description : undefined"
            :flow="flowStore.flow"
            :nodeActions="taskPopup.actions"
            :initialTab="taskPopup.tab"
            @follow="emit('follow', $event)"
            @closed="taskPopup = null"
        />

        <Restart
            v-if="replayExecution && replayTaskRun"
            ref="replayRef"
            isReplay
            :trigger="false"
            :execution="replayExecution"
            :taskRun="replayTaskRun"
            :attemptIndex="replayAttemptIndex"
        />
    </div>
</template>

<script setup lang="ts">
    import {nextTick, onBeforeUnmount, onMounted, ref, inject, watch, computed} from "vue"
    import {useShownValidationErrors} from "../no-code/utils/useFieldValidationErrors"

    import {useI18n} from "vue-i18n"
    import {useStorage} from "@vueuse/core"
    import {useRouter} from "vue-router"
    import {storageKeys, topologyOrientations} from "../../utils/constants"
    import {useVueFlow} from "@vue-flow/core"

    import TaskRunLogs from "../executions/TaskRunLogs.vue"
    import {defaultTaskRunLogLevel} from "../executions/taskRunLogLevel"
    import type {LevelKey} from "../../utils/logs"
    import Vars from "../executions/Vars.vue"
    import MetricsTable from "../executions/MetricsTable.vue"
    import Restart from "../executions/overview/components/actions/Restart.vue"
    import PlayBoxMultiple from "vue-material-design-icons/PlayBoxMultiple.vue"

    import {Topology, NodeMenu} from "@kestra-io/topology"
    import type {FlowGraph, NodeAction} from "@kestra-io/topology"
    import {SECTIONS, State, KsMarkdown, KsEditor, vKsLoading} from "@kestra-io/design-system"
    import type {Execution, TaskRun, Task} from "@kestra-io/kestra-sdk"
    import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
    import type {AddTaskTarget} from "@kestra-io/topology/vue-flow-utils"
    import TaskRunActions from "../executions/TaskRunActions.vue"
    import TaskRunPopup, {type TaskRunPopupTab} from "../executions/TaskRunPopup.vue"
    import {EXECUTION_ARTIFACTS_KEY} from "../executions/composables/useExecutionArtifacts"
    import InformationOutline from "vue-material-design-icons/InformationOutline.vue"
    import LoopIterationPicker from "../executions/LoopIterationPicker.vue"
    import LoopScopeBar from "../executions/LoopScopeBar.vue"
    import {useLoopScoping} from "../../composables/useLoopScoping"
    import {useEditorBindings} from "../../composables/useEditorBindings"
    import {loadTaskRunOutputs} from "../../composables/useTaskRunOutputs"
    import {TOPOLOGY_CLICK_INJECTION_KEY} from "../no-code/injectionKeys"
    import type {BlockType} from "../no-code/utils/types"
    import BlockTaskPicker from "../no-code/blocks/BlockTaskPicker.vue"
    import TaskEditModal from "../no-code/blocks/TaskEditModal.vue"
    import UndoToast from "../no-code/blocks/UndoToast.vue"
    import {useTaskPicker} from "../no-code/blocks/useTaskPicker"
    import {useTaskEditModalStack, modalItemPathOf} from "../no-code/blocks/useTaskEditModalStack"
    import {useEditTarget, taskCrumbAt} from "../no-code/blocks/useEditTarget"
    import {useYamlUndo} from "../no-code/blocks/useYamlUndo"
    import type {Crumb} from "../no-code/utils/useFieldNavigation"
    import {
        laneDisplayLabelFromPath,
        sectionDisplayLabel,
        sectionFromParentPath,
        resolveTaskInsertionTarget,
        resolveTaskInsertionTargetInAnySection,
        moveTaskOntoEdge,
        removeTaskById,
        isTaskListPath,
    } from "../no-code/blocks/blockSections"
    import {useBlockEditorProvides} from "../no-code/blocks/useBlockEditorProvides"
    import {BLOCK_EDITOR_KEYMAP} from "../no-code/blocks/keymap"
    import BlockShortcutsDialog from "../no-code/blocks/BlockShortcutsDialog.vue"
    import {flowDescriptionOf, flowLabelEntriesOf} from "../no-code/blocks/flowSummary"
    import FlowPropertiesModal from "../no-code/blocks/FlowPropertiesModal.vue"
    import BlockCommandMenu, {type BlockCommandMenuItem} from "../no-code/blocks/BlockCommandMenu.vue"
    import {
        buildCommandMenuContextLabel,
        buildCommandMenuItems,
        type BlockCommandMenuContext,
    } from "../no-code/blocks/blockCommandMenu"
    import {buildShortcutGroups} from "../no-code/blocks/shortcutHints"
    import {useBlockEditorKeyboard} from "../no-code/blocks/useBlockEditorKeyboard"
    import {useAuthoringSurface} from "../no-code/blocks/useAuthoringSurface"
    import {
        buildTopologyFocusOrder,
        firstChildOf,
        moveWithinSiblings,
        parentOf,
    } from "../no-code/blocks/topologyFocus"
    import {
        duplicateBlockAtPath,
        errorsLaneTarget,
        moveBlockAtPath,
        groupValidationIssuesByTask,
        updateBlockAtPath,
        type BlockSection,
    } from "../../utils/flowableBlockOps"
    import {trackAuthoringAction} from "../../utils/tabTracking"
    import {useAuthStore} from "override/stores/auth"
    import action from "../../models/action"
    import resource from "../../models/resource"
    import {useCoreStore} from "../../stores/core"
    import {usePluginsStore} from "../../stores/plugins"
    import {useExecutionsStore} from "../../stores/executions"
    import {usePlaygroundStore} from "../../stores/playground"
    import {useFlowStore, type ParsedFlow} from "../../stores/flow"
    import {useToast} from "../../utils/toast"
    import {openFlowInNewTab} from "../../utils/openFlow"

    interface FlowTask {
        id?: string;
        type?: string;
        version?: string;
        description?: string;
        runIf?: string;
        taskRunner?: {
            type?: string;
            version?: string;
            [key: string]: unknown;
        };
        tasks?: FlowTask[];
        [key: string]: unknown;
    }

    interface DrawerTaskInspection {
        id?: string;
        description?: string;
        runIf?: string;
        execution?: Execution;
        taskRuns?: TaskRun[];
        type?: string;
        taskRunner?: { type?: string; [key: string]: unknown };
        [key: string]: unknown;
    }

    interface TaskPopupTarget {
        executionId: string;
        taskId: string;
        task?: FlowTask;
        actions: NodeAction[];
        tab: TaskRunPopupTab;
    }

    const EXCLUDED_NODE_ACTIONS = ["outputs", "replay", "edit"]
    const POPUP_EXCLUDED_NODE_ACTIONS = [...EXCLUDED_NODE_ACTIONS, "logs", "description"]

    const router = useRouter()

    const vueflowId = ref(Math.random().toString())
    const {fitView, getNodes} = useVueFlow(vueflowId.value)

    const topologyClick = inject(TOPOLOGY_CLICK_INJECTION_KEY, ref())

    const executionsStore = useExecutionsStore()
    const playgroundStore = usePlaygroundStore()
    const flowStore = useFlowStore()

    function taskActionsContext(executionId: string | undefined, taskRunId: string | undefined, taskRunIds: string[]) {
        const execution = executionId === executionsStore.execution?.id
            ? executionsStore.execution
            : executionId ? executionsStore.graphExecutions[executionId] : undefined
        const taskRuns = taskRunIds.flatMap(id => {
            const run = execution?.taskRunList?.find(run => run.id === id)
            return run ? [run] : []
        })
        const taskRun = execution?.taskRunList?.find(run => run.id === taskRunId)
        return execution && taskRun ? {execution, taskRun, taskRuns} : undefined
    }
    const taskPopup = ref<TaskPopupTarget | null>(null)
    const isTaskPopupOpen = ref(false)
    const taskPopupExecution = computed(() => {
        const executionId = taskPopup.value?.executionId
        if (!executionId) return undefined
        return executionId === executionsStore.execution?.id
            ? executionsStore.execution
            : executionsStore.graphExecutions[executionId]
    })

    function openTaskPopup(event: {execution?: {id?: string}; taskId?: string; task?: unknown; actions: NodeAction[]; tab?: TaskRunPopupTab}) {
        if (!event.execution?.id || !event.taskId) return
        taskPopup.value = {
            executionId: event.execution.id,
            taskId: event.taskId,
            task: isTask(event.task) ? event.task : undefined,
            actions: event.actions
                .filter(a => !POPUP_EXCLUDED_NODE_ACTIONS.includes(a.key))
                .map((a, i) => i === 0 ? {...a, divided: true} : a),
            tab: event.tab ?? "logs",
        }
        isTaskPopupOpen.value = true
    }

    const onShowArtifacts = (event: {id?: string; task?: unknown; execution?: {id?: string}; actions: NodeAction[]}) =>
        openTaskPopup({execution: event.execution, taskId: event.id, task: event.task, actions: event.actions, tab: "artifacts"})

    const artifactsContext = inject(EXECUTION_ARTIFACTS_KEY, undefined)
    const artifactCounts = computed(() => {
        const counts: Record<string, number> = {}
        for (const artifact of artifactsContext?.artifacts.value ?? []) {
            counts[artifact.taskId] = (counts[artifact.taskId] ?? 0) + 1
        }
        return counts
    })

    // The store relaxes ids the SDK Execution requires, and the topology-task-modal slot contract is typed with the SDK one.
    const exec = computed(() => executionsStore.execution as Execution | undefined)

    const flowSource = computed(() => flowStore.flowYaml || props.source)

    const effectiveFlowGraph = computed(() =>
        playgroundStore.enabled ? (executionsStore.scopedFlowGraph ?? props.flowGraph) : props.flowGraph,
    )

    const isTask = (value: unknown): value is FlowTask & Task =>
        typeof value === "object" && value !== null
        && "id" in value && typeof value.id === "string" && value.id !== ""
        && "type" in value && typeof value.type === "string" && value.type !== ""

    const runnerOf = (task: FlowTask | undefined) => task?.taskRunner

    const collectTasksById = (node: unknown, into: Record<string, FlowTask>) => {
        if (Array.isArray(node)) {
            node.forEach((item) => collectTasksById(item, into))
            return
        }
        if (!node || typeof node !== "object") return
        if (isTask(node) && !(node.id in into)) {
            into[node.id] = node
        }
        Object.values(node).forEach((value) => collectTasksById(value, into))
    }

    // Only the root task collections: an `inputs`, `outputs`, `sla` or `triggers` entry carries an
    // `id` and a `type` too, and walking from the document root would let one shadow a task that
    // shares its id.
    const TASK_SECTIONS = ["tasks", "errors", "finally", "afterExecution"]

    const indexTasks = (source: string | undefined): Record<string, FlowTask> => {
        const parsed = YAML_UTILS.parse<ParsedFlow>(source, false)
        const result: Record<string, FlowTask> = {}
        TASK_SECTIONS.forEach((section) => collectTasksById(parsed?.[section], result))
        return result
    }

    // The same document the `source` prop carries, so an artifact's `task` and `source` never
    // disagree.
    const sourceTaskById = computed(() => indexTasks(flowSource.value))

    const runnersOf = (byId: Record<string, FlowTask>): Record<string, FlowTask["taskRunner"]> =>
        Object.fromEntries(
            Object.entries(byId)
                .filter(([, task]) => task?.taskRunner?.type)
                .map(([id, task]) => [id, task.taskRunner]),
        )

    // Runner-specific fallback, needed by the graph augmentation alone: in execution view flowYaml
    // can be a stale draft whose taskRunner is gone while props.source still has it.
    const taskRunnerById = computed((): Record<string, FlowTask["taskRunner"]> => {
        const fromFlowSource = runnersOf(sourceTaskById.value)
        if (Object.keys(fromFlowSource).length || flowSource.value === props.source) return fromFlowSource
        return runnersOf(indexTasks(props.source))
    })

    // forExecution() strips taskRunner from graph nodes. Re-inject the task's own runner, whole,
    // from the source index so the runner label shows in execution view too. Same object identity
    // back when there is nothing to inject: the topology regenerates its whole graph on any change
    // of this prop.
    const augmentedFlowGraph = computed(() => {
        const graph = effectiveFlowGraph.value
        if (!graph) return graph
        const byId = taskRunnerById.value
        let injected = false
        const nodes = (graph.nodes ?? []).map((n) => {
            const task = n.task
            const taskRunner = task?.id ? byId[task.id] : undefined
            if (!task || !taskRunner?.type || runnerOf(task)?.type) return n
            injected = true
            return {...n, task: {...task, taskRunner}}
        })
        return injected ? {...graph, nodes} : graph
    })

    // A graph node's task comes from forExecution(), so source fills back in what it drops without
    // overwriting what the executed revision carried — the source is the flow's latest revision, or
    // an unsaved draft. The exception is the two keys forExecution() REDUCES rather than drops:
    // `tasks` is the children flattened to id and type, and `taskRunner` is re-injected into the
    // node, so the node's copy of either is derived rather than executed and source wins — but only
    // where source has them, otherwise the node's reduced copy is all there is.
    const taskWithSource = <T extends FlowTask>(task: T): T => {
        const fromSource = task.id ? sourceTaskById.value[task.id] : undefined
        if (!fromSource) return task
        return {
            ...fromSource,
            ...task,
            tasks: fromSource.tasks ?? task.tasks,
            taskRunner: fromSource.taskRunner ?? task.taskRunner,
        }
    }

    const editorBindings = useEditorBindings()

    const props = withDefaults(
        defineProps<{
            flowGraph: FlowGraph;
            flowId?: string;
            namespace?: string;
            isReadOnly?: boolean;
            source?: string;
            isAllowedEdit?: boolean;
            horizontalDefault?: boolean;
            toggleOrientationButton?: boolean;
            expandedSubflows?: string[];
        }>(),
        {
            flowId: undefined,
            namespace: undefined,
            isReadOnly: false,
            source: "",
            isAllowedEdit: false,
            horizontalDefault: undefined,
            toggleOrientationButton: true,
            expandedSubflows: () => [],
        })

    const loopScoping = useLoopScoping(computed(() => augmentedFlowGraph.value), computed(() => props.isReadOnly && Boolean(exec.value?.id)))
    const loopLanes = computed(() => (exec.value?.id ? loopScoping.lanes.value : {}))
    const hasLoopLanes = computed(() => Boolean(exec.value?.id) && loopScoping.laneNodes.value.length > 0)

    const isCollapsedLane = (collapsedLanes: Set<string>, uid: string) =>
        [...collapsedLanes].some((collapsedUid) => uid === collapsedUid || uid.startsWith(`${collapsedUid}.`))

    function isFailuresOnly(collapsedLanes: Set<string>) {
        const uids = loopScoping.lanesWithoutFailures.value
        return uids.length > 0 && uids.every((uid) => isCollapsedLane(collapsedLanes, uid))
    }

    function toggleFailuresOnly(setLanesCollapsed: (uids: string[], collapsed: boolean) => void, collapsedLanes: Set<string>) {
        setLanesCollapsed(loopScoping.lanesWithoutFailures.value, !isFailuresOnly(collapsedLanes))
    }

    function jumpToFirstFailure() {
        const uid = loopScoping.firstFailedLaneUid()
        if (uid) loopScoping.scopeFirstFailure(uid)
    }

    const emit = defineEmits([
        "follow",
        "loading",
        "expand-subflow",
    ])

    const coreStore = useCoreStore()
    const toast = useToast()
    const {t} = useI18n()

    const pluginsStore = usePluginsStore()

    // `horizontalDefault` is a per-instance, responsive override (e.g. the execution Overview
    // switching orientation based on viewport width). It must never be persisted as the user's
    // remembered preference, so `writeDefaults` is disabled: the key is only ever written when
    // the user explicitly toggles orientation (see `toggleOrientation` below).
    const isHorizontalLS = useStorage<boolean | undefined>(
        storageKeys.TOPOLOGY_ORIENTATION,
        undefined,
        localStorage,
        {writeDefaults: false},
    )
    const defaultTopologyOrientation = localStorage.getItem(storageKeys.DEFAULT_TOPOLOGY_ORIENTATION)
    const isHorizontal = ref(
        props.horizontalDefault ??
            (isHorizontalLS.value !== undefined
                ? isHorizontalLS.value?.toString() === "true"
                : defaultTopologyOrientation === topologyOrientations.HORIZONTAL),
    )

    watch(() => props.horizontalDefault, (value) => {
        if (value !== undefined && value !== isHorizontal.value) {
            isHorizontal.value = value
        }
    })
    const vueFlow = ref<HTMLDivElement>()
    const isDrawerOpen = ref(false)
    const isShowDescriptionOpen = ref(false)
    const isShowConditionOpen = ref(false)
    const isInspectOpen = ref(false)
    const inspectTab = ref<"logs" | "outputs" | "metrics">("logs")
    const isReplayPickerOpen = ref(false)
    const selectedTask = ref<DrawerTaskInspection | null>(null)
    const inspectLogLevel = ref<LevelKey>(defaultTaskRunLogLevel())
    const taskRunOutputsById = ref<Record<string, Record<string, unknown>>>({})
    const loadingOutputsTaskRunIds = ref<Set<string>>(new Set())
    const replayExecution = ref<Execution>()
    const replayTaskRun = ref<TaskRun>()
    const replayRef = ref<InstanceType<typeof Restart>>()

    const authStore = useAuthStore()

    const replayEnabled = computed(() => {
        const currentExecution = executionsStore.execution
        if (!currentExecution?.state || State.isRunning(currentExecution.state.current)) {
            return false
        }
        return authStore.user?.isAllowed(resource.EXECUTION, action.REPLAY, currentExecution.namespace) === true
    })

    const replayAttemptIndex = computed(() =>
        replayTaskRun.value?.attempts ? replayTaskRun.value.attempts.length - 1 : undefined,
    )

    onMounted(() => {
        pluginsStore.fetchIcons()
    })

    watch(() => executionsStore.execution?.id, (id) => {
        if (id) {
            executionsStore.loadAugmentedGraph({
                id,
            })
        }
    }, {immediate: true})

    const resetDrawerSections = () => {
        isShowDescriptionOpen.value = false
        isShowConditionOpen.value = false
        isInspectOpen.value = false
        isReplayPickerOpen.value = false
    }

    watch(
        () => isDrawerOpen.value,
        () => {
            if (!isDrawerOpen.value) {
                resetDrawerSections()
                selectedTask.value = null
            }
        },
    )

    // Topology renders the whole graph, so every graph-originated mutation needs the graph
    // regenerated from the new YAML — unlike the No-code canvas, which never reads flowGraph.
    const {undoState, applyYaml: applyYamlWithUndo, deleteWithUndo, performUndo} = useYamlUndo(
        flowStore,
        (name: string) => t("block_editor.block_deleted", {name}),
    )

    function applyGraphYaml(yaml: string) {
        applyYamlWithUndo(yaml)
        flowStore.loadGraphFromSource({flow: yaml}).catch((error: unknown) => {
            console.error("Error loading graph:", error)
        })
    }

    const onDelete = (event: {id: string; section?: string}) => {
        const flowParsed = YAML_UTILS.parse<ParsedFlow>(flowSource.value)
        toast.confirm(
            t("delete task confirm", {taskId: event.id}),
            async () => {
                const section = event.section ? event.section.toLowerCase() : SECTIONS.TASKS.toLowerCase()
                if (
                    section === SECTIONS.TASKS.toLowerCase() &&
                    flowParsed?.tasks?.length === 1 &&
                    flowParsed.tasks.some((e) => e.id === event.id)
                ) {
                    coreStore.message = {
                        variant: "error",
                        title: t("can not delete"),
                        content: t("can not have less than 1 task"),
                    }
                    return
                }
                const taskType = flowParsed?.tasks?.find((e) => e.id === event.id)?.type
                deleteWithUndo(event.id, () => {
                    const source = flowSource.value ?? ""
                    // A trigger is not a task, so it resolves to no task lane and takes the section path.
                    const updatedYmlSource =
                        removeTaskById(source, event.id) ??
                        YAML_UTILS.deleteBlock({source, section, key: event.id})
                    applyGraphYaml(updatedYmlSource)
                    trackAuthoringAction("task_deleted", "topology", {task_type: taskType})
                })
            },
        )
    }

    function blockSchemaPathFor(section: BlockSection): string {
        return [pluginsStore.flowSchema?.$ref, "properties", section, "items"].join("/")
    }

    const onCreateNewTask = (event: AddTaskTarget) => {
        const target = resolveTaskInsertionTargetInAnySection(flowSource.value ?? "", event.refId)
        if (!target) return
        taskPicker.openTaskPickerAtPath(
            target.parentPath,
            target.refIndex,
            undefined,
            event.position,
            undefined,
            event.dagDependency,
        )
    }

    const onMoveTask = (event: {taskId: string; target: AddTaskTarget}) => {
        const moved = moveTaskOntoEdge(flowSource.value ?? "", event.taskId, event.target)
        if (moved === flowSource.value) return
        applyGraphYaml(moved)
        trackAuthoringAction("task_moved", "topology", {
            task_type: sourceTaskById.value[event.taskId]?.type,
            position: event.target.position,
        })
    }

    const onEditTask = (event: {
        task: FlowTask;
        section?: string;
    }) => {
        const section = sectionFromParentPath((event.section ?? SECTIONS.TASKS).toLowerCase())
        const target = event.task.id ? resolveTaskInsertionTarget(flowSource.value ?? "", section, event.task.id) : undefined
        if (!target) return
        pushModalTarget({
            parentPath: target.parentPath,
            blockSchemaPath: blockSchemaPathFor(section),
            refPath: target.refIndex,
        })
    }

    const {modalStack, modalTarget, pushModalTarget, resolveCreatedTarget, popModalTo, closeModal} = useTaskEditModalStack()

    const modalItemPath = computed<string>(() => {
        const target = modalTarget.value
        return target ? modalItemPathOf(target) : ""
    })

    const modalCrumbs = computed<Crumb[]>(() =>
        modalStack.value.map((target) => taskCrumbAt(flowSource.value, modalItemPathOf(target))),
    )

    const alwaysResolved = computed(() => true)

    const {
        path: modalPath,
        data: modalTaskData,
        raw: modalTaskRaw,
    } = useEditTarget(flowSource, modalItemPath, alwaysResolved, alwaysResolved)

    const modalSection = computed<BlockSection>(() =>
        modalTarget.value ? sectionFromParentPath(modalTarget.value.parentPath) : "tasks",
    )

    function onModalTaskEdited(newContent: string) {
        if (!modalPath.value) return
        applyGraphYaml(updateBlockAtPath(flowSource.value, modalPath.value, newContent))
        const taskType = modalTaskData.value?.type
        trackAuthoringAction("task_edited", "topology", {task_type: typeof taskType === "string" ? taskType : undefined})
    }

    function onModalOpenInTabs() {
        const target = modalTarget.value
        const id = modalTaskData.value?.id
        if (!target || id == null) return
        topologyClick.value = {
            action: "edit",
            params: {
                section: sectionFromParentPath(target.parentPath) as BlockType,
                id: String(id),
            },
        }
        closeModal()
    }

    function onModalSelectNested(parentPath: string, blockSchemaPath: string, refPath: number | undefined) {
        pushModalTarget({parentPath, blockSchemaPath, refPath})
    }

    const shownValidationErrors = useShownValidationErrors()

    const validationIssuesByTask = computed<Map<string, string[]>>(() =>
        groupValidationIssuesByTask(shownValidationErrors.value, flowStore.flowParsed),
    )

    const taskPicker = useTaskPicker({
        pluginsStore,
        editorEl: vueFlow,
        focusedId: ref<string | undefined>(undefined),
        focusedAnchor: () => undefined,
        focusedBlockPath: () => undefined,
        focusCanvasCard: (id) => {
            if (!id) return
            // The insert already landed, so the source has to be read live from the store —
            // props.source only catches up on the next render.
            const yaml = flowStore.flowYaml ?? ""
            // Triggers are resolved separately: the shared helper deliberately skips them so the
            // edge `+` can never target one, but a trigger just inserted still has to open its
            // editor — a Schedule with no cron would otherwise leave the flow invalid with nothing
            // on screen to fix it.
            const inTasks = resolveTaskInsertionTargetInAnySection(yaml, id)
            const asTrigger = inTasks
                ? undefined
                : resolveTaskInsertionTarget(yaml, "triggers", id)
            const target = inTasks
                ?? (asTrigger ? {...asTrigger, section: sectionFromParentPath("triggers")} : undefined)
            if (!target) return
            pushModalTarget({
                parentPath: target.parentPath,
                blockSchemaPath: blockSchemaPathFor(target.section),
                refPath: target.refIndex,
            })
        },
        sectionList: (section) => {
            const list = YAML_UTILS.parse<ParsedFlow>(flowSource.value ?? "")?.[section]
            return Array.isArray(list) ? list : []
        },
        sectionDisplayLabel: (section) => sectionDisplayLabel(t, section),
        laneDisplayLabel: (parentPath) => laneDisplayLabelFromPath(t, parentPath),
        flowYaml: computed(() => flowSource.value ?? ""),
        applyYaml: applyGraphYaml,
        surface: "topology",
    })

    const onPickerEscape = (event: KeyboardEvent) => {
        if (event.key === "Escape") {
            taskPicker.taskPickerVisible.value = false
        }
    }

    watch(taskPicker.taskPickerVisible, (visible) => {
        if (visible) {
            window.addEventListener("keydown", onPickerEscape)
        } else {
            window.removeEventListener("keydown", onPickerEscape)
        }
    })

    onBeforeUnmount(() => {
        window.removeEventListener("keydown", onPickerEscape)
    })

    const shortcutsOpen = ref(false)
    const shortcutGroups = buildShortcutGroups()
    const commandMenuOpen = ref(false)
    const flowPropertiesOpen = ref(false)

    const parsedFlowSummary = computed(() =>
        YAML_UTILS.parse<Record<string, unknown>>(flowSource.value ?? ""),
    )
    const flowDescription = computed(() => flowDescriptionOf(parsedFlowSummary.value))
    const flowLabels = computed(() => flowLabelEntriesOf(parsedFlowSummary.value))

    const focusedTaskId = ref<string | undefined>(undefined)
    const focusOrder = computed(() => buildTopologyFocusOrder(flowSource.value ?? ""))

    const isAuthoringOverlayOpen = () =>
        shortcutsOpen.value
        || commandMenuOpen.value
        || flowPropertiesOpen.value
        || taskPicker.taskPickerVisible.value
        || Boolean(modalTarget.value)
        || isDrawerOpen.value

    /** The focus walk reaches every task-holding lane, so the action has to follow it there. */
    function focusedSection(): BlockSection {
        const node = focusOrder.value.find(entry => entry.id === focusedTaskId.value)
        return node ? sectionFromParentPath(node.parentPath) : "tasks"
    }

    function openFocusedTask() {
        const id = focusedTaskId.value
        const task = id ? sourceTaskById.value[id] : undefined
        if (!task) return
        onEditTask({task, section: focusedSection()})
    }

    function duplicateTaskById(id: string | undefined) {
        const node = focusOrder.value.find(entry => entry.id === id)
        if (!node) return false
        const updated = duplicateBlockAtPath(flowSource.value ?? "", node.path)
        if (updated === flowSource.value) return false
        applyGraphYaml(updated)
        trackAuthoringAction("task_duplicated", "topology", {
            task_type: sourceTaskById.value[node.id]?.type,
        })
        return true
    }

    function duplicateFocusedTask() {
        return duplicateTaskById(focusedTaskId.value)
    }

    const onDuplicate = (event: {id?: string}) => duplicateTaskById(event.id)

    // Triggers are not tasks, so no edge `+` can ever target them; the triggers box is their only
    // entry point on the canvas.
    const onAddTrigger = () => taskPicker.openTaskPicker("triggers")

    function reorderFocusedTask(direction: "up" | "down") {
        const node = focusOrder.value.find(entry => entry.id === focusedTaskId.value)
        if (!node) return false
        const updated = moveBlockAtPath(flowSource.value ?? "", node.path, direction)
        // Already at the end of its lane: nothing moved, so the key should not look handled.
        if (updated === flowSource.value) return false
        applyGraphYaml(updated)
        trackAuthoringAction("task_moved", "topology", {
            task_type: sourceTaskById.value[node.id]?.type,
        })
        return true
    }

    function insertRelativeToFocused(position: "before" | "after") {
        const id = focusedTaskId.value
        if (!id) return
        const target = resolveTaskInsertionTargetInAnySection(flowSource.value ?? "", id)
        if (!target) return
        taskPicker.openTaskPickerAtPath(target.parentPath, target.refIndex, undefined, position)
    }

    function dispatchTopologyShortcut(id: string, event: KeyboardEvent) {
        // Reading the shortcut list changes nothing, so it stays available on a read-only flow.
        if (id === "help") {
            shortcutsOpen.value = !shortcutsOpen.value
            return
        }
        if (shortcutsOpen.value && id === "clear") {
            shortcutsOpen.value = false
            return
        }
        if (commandMenuOpen.value && id === "clear") {
            commandMenuOpen.value = false
            return
        }
        if (props.isReadOnly || !props.isAllowedEdit) return false
        // Checked here rather than through a watch: watching the order would re-parse the whole
        // flow on every keystroke in the code editor, which shares this source.
        if (focusedTaskId.value && !focusOrder.value.some(entry => entry.id === focusedTaskId.value)) {
            focusedTaskId.value = undefined
        }
        switch (id) {
        case "move":
            focusedTaskId.value = moveWithinSiblings(
                focusOrder.value,
                focusedTaskId.value,
                event.key === "ArrowDown" || event.key === "j" ? 1 : -1,
            )
            return
        case "step-into":
            focusedTaskId.value = firstChildOf(focusOrder.value, focusedTaskId.value) ?? focusedTaskId.value
            return
        case "step-out":
            focusedTaskId.value = parentOf(focusOrder.value, focusedTaskId.value) ?? focusedTaskId.value
            return
        case "open":
            openFocusedTask()
            return
        case "delete":
            if (!focusedTaskId.value) return false
            onDelete({id: focusedTaskId.value, section: focusedSection()})
            return
        case "insert-after":
            insertRelativeToFocused("after")
            return
        case "insert-before":
            insertRelativeToFocused("before")
            return
        case "quick-insert":
        case "command-menu":
            openCommandMenu()
            return
        case "duplicate":
            return duplicateFocusedTask() ? undefined : false
        case "reorder":
            return reorderFocusedTask(event.key === "ArrowDown" ? "down" : "up") ? undefined : false
        case "undo":
            return performUndo()
        case "save":
            saveFlow()
            return
        case "clear":
            // Dismissing the picker or the modal must not also cost the user their place.
            if (isAuthoringOverlayOpen()) return false
            if (!focusedTaskId.value) return false
            focusedTaskId.value = undefined
            return
        default:
            return false
        }
    }

    const commandMenuContext = computed<BlockCommandMenuContext>(() => ({
        t,
        focusedId: focusedTaskId.value,
        focusedBlockDisplayName: () => focusedTaskId.value ?? "",
        sectionDisplayLabel: (section) => sectionDisplayLabel(t, section),
        laneDisplayLabelFromPath: (parentPath) => laneDisplayLabelFromPath(t, parentPath),
        close: () => (commandMenuOpen.value = false),
        addAfterFocused: () => insertRelativeToFocused("after"),
        addBeforeFocused: () => insertRelativeToFocused("before"),
        insertInSection: (section) => taskPicker.openTaskPicker(section),
        openFocused: openFocusedTask,
        duplicateFocused: () => duplicateFocusedTask(),
        deleteFocused: () => {
            if (focusedTaskId.value) onDelete({id: focusedTaskId.value, section: focusedSection()})
        },
        // The canvas has no scroll of its own: the graph is laid out server-side, so jumping to a
        // section means moving the focus ring to its first task.
        goToSection: (section) => {
            const first = focusOrder.value.find(
                entry => sectionFromParentPath(entry.parentPath) === section,
            )
            if (first) focusedTaskId.value = first.id
        },
        saveFlow: () => saveFlow(),
        taskEntries: taskPicker.focusedContextEntries.value,
        insertTaskType: taskPicker.insertTaskInFocusedContext,
    }))

    const commandMenuContextLabel = computed(() => buildCommandMenuContextLabel(commandMenuContext.value))
    const commandMenuItems = computed<BlockCommandMenuItem[]>(() =>
        buildCommandMenuItems(commandMenuContext.value),
    )

    function openCommandMenu() {
        taskPicker.ensurePluginData()
        commandMenuOpen.value = true
    }

    const authoringSurface = useAuthoringSurface(vueFlow)

    // The history is shared with No-code, so both surfaces hold the same badge: only the one the
    // user is on should show it, or a delete raises two identical toasts side by side.
    const visibleUndoState = computed(() => (authoringSurface.isActive() ? undoState.value : null))

    useBlockEditorKeyboard({
        keymap: BLOCK_EDITOR_KEYMAP,
        dispatch: (id, event) => (authoringSurface.isActive() ? dispatchTopologyShortcut(id, event) : false),
        isOverlayOpen: isAuthoringOverlayOpen,
        root: vueFlow,
    })



    const onAddFlowableError = (event: {task: {id: string}}) => {
        const target = errorsLaneTarget(flowSource.value ?? "", event.task.id)
        if (!target) return
        taskPicker.openTaskPickerAtPath(target.parentPath, target.refIndex)
    }

    async function saveFlow() {
        const outcome = await flowStore.save?.()
        if (outcome === "blocked") {
            coreStore.message = {
                variant: "error",
                title: t("block_editor.save_blocked.title"),
                content: flowStore.flowErrors?.join("\n") ?? t("block_editor.save_blocked.message"),
            }
        }
    }

    // Only a lane of tasks can be filled from the task picker; every other list (e.g. flow
    // inputs) needs its own schema-driven form, stacked on the modal like any nested edit.
    function createNestedBlock(parentPath: string, blockSchemaPath: string, refPath: number | undefined, anchorEl?: HTMLElement) {
        if (isTaskListPath(parentPath)) {
            taskPicker.openTaskPickerAtPath(parentPath, refPath ?? -1, undefined, "after", anchorEl)
            return
        }
        pushModalTarget({parentPath, blockSchemaPath, refPath, creating: true})
    }

    // TaskEditModalForm re-provides the path/section keys it needs for its own subtree — this
    // only has to cover what it doesn't: schema resolution and the nested-edit/save callbacks.
    useBlockEditorProvides({
        props: {},
        flowYaml: flowSource,
        validationIssuesByTask,
        createTask: createNestedBlock,
        editTask: (parentPath, blockSchemaPath, refPath) => pushModalTarget({parentPath, blockSchemaPath, refPath}),
        closeTask: () => closeModal(),
        updateYaml: (yaml: string) => applyGraphYaml(yaml),
        saveFlow: () => saveFlow(),
    })

    const FOCUS_MAX_ZOOM = 1

    function focusTask(taskId: string) {
        const node = getNodes.value.find((candidate) => candidate.id.split(".").at(-1) === taskId)
        if (node) fitView({nodes: [node.id], maxZoom: FOCUS_MAX_ZOOM, duration: 300})
    }

    defineExpose({focusTask})

    const toggleOrientation = () => {
        isHorizontal.value = !isHorizontal.value
        isHorizontalLS.value = isHorizontal.value
    }

    const openFlow = (data: { link: { namespace: string; id: string; executionId?: string } }) => {
        openFlowInNewTab(
            {
                namespace: data.link.namespace,
                flowId: data.link.id,
                executionId: data.link.executionId,
                tab: "overview",
            },
            router,
        )
    }

    const openInspect = (event: DrawerTaskInspection, tab: "logs" | "outputs" | "metrics") => {
        resetDrawerSections()
        selectedTask.value = event
        inspectTab.value = tab
        isInspectOpen.value = true
        isDrawerOpen.value = true
    }

    const showLogs = (event: DrawerTaskInspection) => openInspect(event, "logs")

    const showOutputs = (event: DrawerTaskInspection) => openInspect(event, "outputs")

    function isLoadingTaskRunOutputs(taskRunId: string): boolean {
        return loadingOutputsTaskRunIds.value.has(taskRunId)
    }

    async function fetchTaskRunOutputs(executionId: string, taskRunId: string) {
        if (taskRunOutputsById.value[taskRunId] || loadingOutputsTaskRunIds.value.has(taskRunId)) {
            return
        }
        loadingOutputsTaskRunIds.value.add(taskRunId)
        try {
            taskRunOutputsById.value = {
                ...taskRunOutputsById.value,
                [taskRunId]: await loadTaskRunOutputs(executionId, taskRunId),
            }
        } finally {
            loadingOutputsTaskRunIds.value.delete(taskRunId)
        }
    }

    // Task run outputs live behind a dedicated endpoint since Kestra 2.0 (they are no
    // longer embedded on the taskRun objects in selectedTask.taskRuns) — fetch them
    // lazily once the outputs tab is actually shown.
    watch(
        [selectedTask, inspectTab, isInspectOpen],
        ([task, tab, open]) => {
            const executionId = task?.execution?.id
            if (!open || tab !== "outputs" || !executionId) {
                return
            }
            for (const taskRun of task.taskRuns ?? []) {
                fetchTaskRunOutputs(executionId, taskRun.id)
            }
        },
        {immediate: true},
    )

    const openReplayDialog = (taskExecution: Execution | undefined, taskRun: TaskRun) => {
        replayExecution.value = taskExecution
        replayTaskRun.value = taskRun
        isDrawerOpen.value = false
        nextTick(() => replayRef.value?.open())
    }

    const onReplayTask = (event: {execution?: Execution; taskRuns: TaskRun[]}) => {
        if (event.taskRuns.length === 1) {
            openReplayDialog(event.execution, event.taskRuns[0])
            return
        }
        resetDrawerSections()
        selectedTask.value = event
        isReplayPickerOpen.value = true
        isDrawerOpen.value = true
    }

    const showDescription = (event: FlowTask) => {
        resetDrawerSections()
        selectedTask.value = event
        isShowDescriptionOpen.value = true
        isDrawerOpen.value = true
    }

    const showCondition = (event: {task: FlowTask}) => {
        resetDrawerSections()
        selectedTask.value = event.task
        isShowConditionOpen.value = true
        isDrawerOpen.value = true
    }

    const expandSubflow = (event: string[]) => {
        emit("expand-subflow", event)
    }
</script>

<style scoped>
.detail {
    margin-top: var(--ks-spacing-4);
}

.tab-body {
    padding-block: var(--ks-spacing-3) var(--ks-spacing-6);
}

.outputs-view {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-5);
}

.taskrun-card__header {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    margin-bottom: var(--ks-spacing-2);
}

.taskrun-card__value {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.taskrun-card__empty {
    display: block;
    padding: var(--ks-spacing-3);
    border: 1px dashed var(--ks-border-default);
    border-radius: var(--ks-radius-base);
    color: var(--ks-text-secondary);
    font-size: var(--ks-font-size-xs);
}

.replay-picker {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-2);
    margin-top: var(--ks-spacing-4);
}

.replay-picker__hint {
    color: var(--ks-text-secondary);
    font-size: var(--ks-font-size-xs);
}

.replay-picker__item {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-3);
    padding: var(--ks-spacing-2) var(--ks-spacing-3);
    border: 1px solid var(--ks-border-default);
    border-radius: var(--ks-radius-base);
    background: var(--ks-bg-surface);
    transition: background-color 0.1s ease;

    &:hover {
        background: var(--ks-bg-hover);
    }
}

.replay-picker__value {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.vueflow {
    height: 100%;
    width: 100%;
    position: relative;

    /* Anchor the state icon (playground-button) to the node header area, not */
    /* the full VueFlow node element, so it doesn't overlap plugin UI details. */
    & :deep(.main-content) {
        position: relative;
    }
}

</style>

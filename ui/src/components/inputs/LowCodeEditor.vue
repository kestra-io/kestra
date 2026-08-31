<template>
    <div ref="vueFlow" class="vueflow">
        <slot name="top-bar" />
        <Topology
            v-if="manifestReady"
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
            :subflowsExecutions="executionsStore.subflowsExecutions"
            :playgroundEnabled="playgroundStore.enabled"
            :playgroundReadyToStart="playgroundStore.readyToStart"
            :replayEnabled="replayEnabled"
            :getNodeDimensions="getNodeDimensions"
            :customActions="customActions"
            :showDetailsToggle="props.showDetailsToggle && hasExtraDetails"
            :taskDetailsVersion="taskDetailsVersion"
            @toggle-orientation="toggleOrientation"
            @edit="onEditTask"
            @delete="onDelete"
            @open-link="openFlow"
            @show-logs="showLogs"
            @show-outputs="showOutputs"
            @replay-task="onReplayTask"
            @show-description="showDescription"
            @show-condition="showCondition"
            @show-custom-action="showCustomAction"
            @on-add-flowable-error="onAddFlowableError"
            @add-task="onCreateNewTask"
            @expand-subflow="expandSubflow"
            @run-task="playgroundStore.runUntilTask($event.task.id)"
        >
            <template #taskDetails="taskProps">
                <slot name="taskDetails" v-bind="taskProps">
                    <TopologyDetailsRemote
                        :taskType="taskProps.data.node?.task?.taskRunner?.type ?? taskProps.data.node?.task?.type"
                        :task="taskWithSource(taskProps.data.node?.task)"
                        :execution="exec"
                        :namespace="props.namespace"
                        :flowId="props.flowId"
                        :tenant="tenant"
                        :source="flowSource"
                        :progress="taskProgress(taskProps.data.node?.task?.id)"
                        :fetchOutputs="fetchTaskOutputs(taskProps.data.node?.task?.id)"
                        :fetchMetrics="fetchTaskMetrics(taskProps.data.node?.task?.id)"
                    />
                </slot>
            </template>
        </Topology>

        <KsDialog
            v-if="isTaskModalOpen && taskModalCtx"
            v-model="isTaskModalOpen"
            :title="taskModalCtx.title ?? taskModalCtx.task?.id ?? 'Task details'"
            :destroyOnClose="true"
            :appendToBody="true"
            scrollable
        >
            <TopologyTaskModalRemote v-bind="(taskModalCtx as any)" />
        </KsDialog>

        <KsDrawer v-if="isDrawerOpen && selectedTask" v-model="isDrawerOpen">
            <template #header>
                <code>{{ selectedTask.id }}</code>
            </template>
            <KsTabs v-if="isInspectOpen" v-model="inspectTab" type="box" class="inspect-tabs">
                <KsTabPane :label="$t('logs')" name="logs">
                    <div class="tab-body">
                        <Collapse>
                            <KsFormItem>
                                <SearchField
                                    :router="false"
                                    @search="onSearch"
                                    class="me-2"
                                />
                            </KsFormItem>
                            <KsFormItem>
                                <LogLevelSelector
                                    :value="logLevel"
                                    @update:model-value="onLevelChange"
                                />
                            </KsFormItem>
                        </Collapse>
                        <TaskRunDetails
                            v-for="taskRun in selectedTask.taskRuns"
                            :key="taskRun.id"
                            :targetExecutionId="selectedTask.execution?.id"
                            :taskRunId="taskRun.id"
                            :filter="logFilter"
                            :excludeMetas="[
                                'namespace',
                                'flowId',
                                'taskId',
                                'executionId',
                            ]"
                            :level="logLevel"
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
                            <div v-if="selectedTask.taskRuns.length > 1" class="taskrun-card__header">
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
                            <div v-if="selectedTask.taskRuns.length > 1" class="taskrun-card__header">
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
                    class="mt-3"
                />
            </div>
            <div v-if="isShowCustomActionOpen && customActionMeta">
                <KsEditor
                    :readOnly="true"
                    :inline="true"
                    :options="{fullHeight: false}"
                    :navbar="false"
                    :modelValue="selectedTask[customActionMeta.taskProp]"
                    :lang="customActionMeta.lang"
                    class="mt-3"
                />
                <TaskDrawerRemote
                    :taskType="selectedTask.type"
                    :task="taskWithSource(selectedTask)"
                    :execution="exec"
                    :namespace="props.namespace"
                    :flowId="props.flowId"
                    :tenant="tenant"
                    :source="flowSource"
                    :progress="taskProgress(selectedTask?.id)"
                    :fetchOutputs="fetchTaskOutputs(selectedTask?.id)"
                    :fetchMetrics="fetchTaskMetrics(selectedTask?.id)"
                    displayMode="full"
                    class="mt-3"
                />
            </div>
        </KsDrawer>

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
    import {nextTick, onMounted, ref, inject, provide, watch, computed} from "vue"

    import {useI18n} from "vue-i18n"
    import {useStorage} from "@vueuse/core"
    import {useRoute, useRouter} from "vue-router"
    import {useVueFlow} from "@vue-flow/core"

    import SearchField from "../layout/SearchField.vue"
    import LogLevelSelector from "../logs/LogLevelSelector.vue"
    import TaskRunDetails from "../logs/TaskRunDetails.vue"
    import Collapse from "../layout/Collapse.vue"
    import Vars from "../executions/Vars.vue"
    import MetricsTable from "../executions/MetricsTable.vue"
    import Restart from "../executions/overview/components/actions/Restart.vue"
    import PlayBoxMultiple from "vue-material-design-icons/PlayBoxMultiple.vue"

    import {Topology} from "@kestra-io/topology"
    import {SECTIONS, State, KsMarkdown, KsEditor, KsDialog, vKsLoading} from "@kestra-io/design-system"
    import {Execution} from "@kestra-io/kestra-sdk"
    import * as MetricsAPI from "@kestra-io/kestra-sdk/metrics"
    import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
    import {useEditorBindings} from "../../composables/useEditorBindings"
    import {loadTaskRunOutputs} from "../../composables/useTaskRunOutputs"

    import {TOPOLOGY_CLICK_INJECTION_KEY} from "../no-code/injectionKeys"
    import {useAuthStore} from "override/stores/auth"
    import action from "../../models/action"
    import resource from "../../models/resource"
    import {useCoreStore} from "../../stores/core"
    import {usePluginsStore} from "../../stores/plugins"
    import {useExecutionsStore} from "../../stores/executions"
    import {usePlaygroundStore} from "../../stores/playground"
    import {useFlowStore} from "../../stores/flow"
    import {useToast} from "../../utils/toast"
    import {useFederatedModule} from "../../remoteComponents/useFederatedModule"
    import {openFlowInNewTab} from "../../utils/openFlow"

    const router = useRouter()
    const route = useRoute()

    const vueflowId = ref(Math.random().toString())
    const {fitView, setMinZoom} = useVueFlow(vueflowId.value)

    const topologyClick = inject(TOPOLOGY_CLICK_INJECTION_KEY, ref())

    const executionsStore = useExecutionsStore()
    const playgroundStore = usePlaygroundStore()
    const flowStore = useFlowStore()

    const exec = computed(() => executionsStore.execution as any as Execution)

    const tenant = computed(() => route.params.tenant as string | undefined)

    const flowSource = computed(() => flowStore.flowYaml || props.source)

    const effectiveFlowGraph = computed(() =>
        playgroundStore.enabled ? (executionsStore.flowGraph ?? props.flowGraph) : props.flowGraph,
    )

    const collectTasksById = (node: unknown, into: Record<string, any>) => {
        if (Array.isArray(node)) {
            node.forEach((item) => collectTasksById(item, into))
            return
        }
        if (!node || typeof node !== "object") return
        const candidate = node as Record<string, any>
        if (typeof candidate.id === "string" && typeof candidate.type === "string" && !(candidate.id in into)) {
            into[candidate.id] = candidate
        }
        Object.values(candidate).forEach((value) => collectTasksById(value, into))
    }

    // Only the root task collections: an `inputs`, `outputs`, `sla` or `triggers` entry carries an
    // `id` and a `type` too, and walking from the document root would let one shadow a task that
    // shares its id.
    const TASK_SECTIONS = ["tasks", "errors", "finally", "afterExecution"]

    const indexTasks = (source: string | undefined): Record<string, any> => {
        const parsed = YAML_UTILS.parse(source, false)
        const result: Record<string, any> = {}
        TASK_SECTIONS.forEach((section) => collectTasksById(parsed?.[section], result))
        return result
    }

    // The same document the `source` prop carries, so an artifact's `task` and `source` never
    // disagree.
    const sourceTaskById = computed(() => indexTasks(flowSource.value))

    const runnersOf = (byId: Record<string, any>): Record<string, any> =>
        Object.fromEntries(
            Object.entries(byId)
                .filter(([, task]) => task?.taskRunner?.type)
                .map(([id, task]) => [id, task.taskRunner]),
        )

    // Runner-specific fallback, needed by the graph augmentation alone: in execution view flowYaml
    // can be a stale draft whose taskRunner is gone while props.source still has it.
    const taskRunnerById = computed((): Record<string, any> => {
        const fromFlowSource = runnersOf(sourceTaskById.value)
        if (Object.keys(fromFlowSource).length || flowSource.value === props.source) return fromFlowSource
        return runnersOf(indexTasks(props.source))
    })

    // forExecution() strips taskRunner from graph nodes. Re-inject the task's own runner, whole,
    // from the source index so topology-details and the burger-menu "Show Details" item work in
    // execution view too. Same object identity back when there is nothing to inject: the topology
    // regenerates its whole graph on any change of this prop.
    const augmentedFlowGraph = computed(() => {
        const graph = effectiveFlowGraph.value
        if (!graph) return graph
        const byId = taskRunnerById.value
        let injected = false
        const nodes = (graph.nodes ?? []).map((n: any) => {
            const taskRunner = n.task?.id ? byId[n.task.id] : undefined
            if (!taskRunner?.type || n.task?.taskRunner?.type) return n
            injected = true
            return {...n, task: {...n.task, taskRunner}}
        })
        return injected ? {...graph, nodes} : graph
    })

    // A graph node's task comes from forExecution(), so source fills back in what it drops without
    // overwriting what the executed revision carried — the source is the flow's latest revision, or
    // an unsaved draft. The exception is the two keys forExecution() REDUCES rather than drops:
    // `tasks` is the children flattened to id and type, and `taskRunner` is re-injected into the
    // node, so the node's copy of either is derived rather than executed and source wins — but only
    // where source has them, otherwise the node's reduced copy is all there is.
    const taskWithSource = (task: Record<string, any> | undefined) => {
        const fromSource = task?.id ? sourceTaskById.value[task.id] : undefined
        if (!task || !fromSource) return task
        const merged = {...fromSource, ...task}
        if (fromSource.tasks !== undefined) merged.tasks = fromSource.tasks
        if (fromSource.taskRunner !== undefined) merged.taskRunner = fromSource.taskRunner
        return merged
    }

    const {RemoteComponent: TopologyDetailsRemote, taskAdditionalInfoRemote, manifestReady, resolveRemoteComponent} = useFederatedModule("topology-details")
    const {RemoteComponent: TaskDrawerRemote, resolveRemoteComponent: resolveDrawerComponent} = useFederatedModule("topology-task-drawer")
    const {RemoteComponent: TopologyTaskModalRemote, resolveRemoteComponent: resolveTaskModalComponent} = useFederatedModule("topology-task-modal")


    const customActions = computed(() => {
        const result: Record<string, { label: string; taskProp: string; lang: string }> = {}
        for (const [type, info] of Object.entries(taskAdditionalInfoRemote.value)) {
            const ca = (info as any)?.customAction
            if (ca?.label) {
                result[type] = ca
            }
        }
        return result
    })

    const hasExtraDetails = computed(() => {
        const types = taskAdditionalInfoRemote.value
        return (augmentedFlowGraph.value?.nodes ?? []).some((n: any) =>
            (n.task?.type && types[n.task.type]) ||
            (n.task?.taskRunner?.type && types[n.task.taskRunner.type]),
        )
    })

    // progressEvents are never reset across execution navigations (taskRunId is globally
    // unique so old entries are harmless in isolation) — but filtering on taskId alone lets a
    // PREVIOUS taskRun's entries leak into a fresh run of the same task, or into a pre-execution
    // view with no run at all. Resolve this task's CURRENT taskRun from the execution and filter
    // on that instead: no current taskRun means nothing to show.
    const currentTaskRunId = (taskId: string | undefined): string | undefined => {
        const list = exec.value?.taskRunList as any[] | undefined
        const filtered = list?.filter((r: any) => r.taskId === taskId) ?? []
        return filtered[filtered.length - 1]?.id
    }

    const taskProgress = (taskId: string | undefined) => {
        const taskRunId = currentTaskRunId(taskId)
        if (!taskRunId) return []
        return executionsStore.progressEvents.filter((p) => p.taskRunId === taskRunId)
    }

    // Both fetchers resolve the execution when CALLED, not when bound: an artifact is handed its
    // props once, when the graph is generated, and the execution (or the task run it should read)
    // may only come into existence later (a playground run, a replay).
    const fetchTaskOutputs = (taskId: string | undefined) => ({taskRunId}: {taskRunId?: string} = {}) => {
        const executionId = exec.value?.id
        const runId = taskRunId ?? currentTaskRunId(taskId)
        if (!executionId || !runId) return Promise.resolve({})
        return loadTaskRunOutputs(executionId, runId)
    }

    const fetchTaskMetrics = (taskId: string | undefined) => ({page, size, sort, taskRunId}: {page?: number, size?: number, sort?: string, taskRunId?: string} = {}) => {
        const executionId = exec.value?.id
        if (!executionId || !taskId) return Promise.resolve({results: [], total: 0})
        return MetricsAPI.searchByExecution({
            executionId,
            taskId,
            taskRunId,
            page,
            size,
            sort: sort ? [sort] : undefined,
        })
    }

    // Topology nodes only re-evaluate their taskDetails slot (where taskProgress is read) when the
    // graph is regenerated — bump this so a live progress update (which isn't part of `execution`
    // or `flowGraph`) still reaches an already-rendered node.
    const taskDetailsVersion = ref(0)
    watch(() => executionsStore.progressEvents, () => {
        taskDetailsVersion.value++
    })

    const isTaskModalOpen = ref(false)
    const taskModalCtx = ref<Record<string, any> | null>(null)

    provide("kestra:openTaskModal", (ctx: Record<string, any>) => {
        taskModalCtx.value = ctx
        isTaskModalOpen.value = true
    })

    function getNodeDimensions(node: any, getNodeWidth: (node: any) => number, getNodeHeight: (node: any) => number) {
        const taskType = node?.task?.type
        const runnerType = node?.task?.taskRunner?.type
        const addInfo = taskAdditionalInfoRemote.value[taskType] ?? taskAdditionalInfoRemote.value[runnerType]
        const hasExecution = !!executionsStore.execution?.id
        const height = hasExecution
            ? (addInfo?.heightWithExecution ?? addInfo?.height ?? getNodeHeight(node))
            : (addInfo?.height ?? getNodeHeight(node))
        return {
            width: getNodeWidth(node),
            height,
        }
    };

    const resolveTaskTopologyDetails = async (tasks: any[] = []) => {
        const taskTypes = new Set<string>()
        const runnerTypes = new Set<string>()
        tasks.forEach((task: any) => {
            if (!task?.type) {
                return
            }
            taskTypes.add(`${task.type}:${task.version ?? "null"}`)
            if (task?.taskRunner?.type) {
                runnerTypes.add(`${task.taskRunner.type}:${task.taskRunner.version ?? "null"}`)
            }
        })

        const taskTypesReParsed: {cls: string, version: string | undefined}[] = []
        const runnerTypesReParsed: {cls: string, version: string | undefined}[] = []

        for (const tt of taskTypes) {
            const [cls, version] = tt.split(":")
            taskTypesReParsed.push({cls, version: version === "null" ? undefined : version})
        }
        for (const tt of runnerTypes) {
            const [cls, version] = tt.split(":")
            runnerTypesReParsed.push({cls, version: version === "null" ? undefined : version})
        }

        await Promise.all([
            resolveRemoteComponent(taskTypesReParsed),
            resolveDrawerComponent(taskTypesReParsed),
            resolveTaskModalComponent(taskTypesReParsed),
            ...(runnerTypesReParsed.length ? [
                resolveTaskModalComponent(runnerTypesReParsed),
                resolveRemoteComponent(runnerTypesReParsed),
            ] : []),
        ])
    }

    watch(
        () => flowStore.flowParsed?.tasks,
        async (tasks) => {
            await resolveTaskTopologyDetails(tasks ?? [])
        },
        {immediate: true},
    )

    const editorBindings = useEditorBindings()

    const props = withDefaults(
        defineProps<{
            flowGraph: Record<string, any>;
            flowId?: string;
            namespace?: string;
            execution?: Record<string, any>;
            isReadOnly?: boolean;
            source?: string;
            isAllowedEdit?: boolean;
            horizontalDefault?: boolean;
            toggleOrientationButton?: boolean;
            showDetailsToggle?: boolean;
            expandedSubflows?: string[];
        }>(),
        {
            flowId: undefined,
            namespace: undefined,
            execution: undefined,
            isReadOnly: false,
            source: "",
            isAllowedEdit: false,
            horizontalDefault: undefined,
            toggleOrientationButton: true,
            showDetailsToggle: true,
            expandedSubflows: () => [],
        })

    watch(
        () => props.flowGraph,
        async (flowGraph) => {
            if (flowStore.flowParsed?.tasks?.length) return
            // props.source has taskRunner intact; graph nodes may have it stripped (forExecution)
            const sourceParsed = props.source ? YAML_UTILS.parse(props.source) : null
            const tasks = sourceParsed?.tasks?.length
                ? sourceParsed.tasks
                : (flowGraph?.nodes ?? [])
                    .filter((n: any) => n.task?.type)
                    .map((n: any) => ({type: n.task.type, version: n.task.version, taskRunner: n.task.taskRunner}))
            await resolveTaskTopologyDetails(tasks)
        },
        {immediate: true},
    )

    // When props.source has runner types that flowParsed lacks (e.g. stale/absent flowYaml
    // in execution view), re-resolve so the pluginUiManifest call includes runner types.
    watch(
        () => props.source,
        async (source) => {
            if (!source) return
            const parsed = YAML_UTILS.parse(source)
            const sourceHasRunners = (parsed?.tasks ?? []).some((t: any) => t?.taskRunner?.type)
            const flowParsedHasRunners = (flowStore.flowParsed?.tasks ?? []).some((t: any) => t?.taskRunner?.type)
            if (sourceHasRunners && !flowParsedHasRunners) {
                await resolveTaskTopologyDetails(parsed.tasks)
            }
        },
        {immediate: true},
    )

    const emit = defineEmits([
        "follow",
        "on-edit",
        "loading",
        "expand-subflow",
    ])

    const coreStore = useCoreStore()
    const toast = useToast()
    const {t} = useI18n()

    const pluginsStore = usePluginsStore()

    const isHorizontalLS = useStorage("topology-orientation", props.horizontalDefault)
    const isHorizontal = ref(props.horizontalDefault ?? (isHorizontalLS.value?.toString() === "true"))

    watch(() => props.horizontalDefault, (value) => {
        if (value !== undefined && value !== isHorizontal.value) {
            isHorizontal.value = value
            fitViewOrientation()
        }
    })
    const vueFlow = ref<HTMLDivElement>()
    const timer = ref<ReturnType<typeof setTimeout>>()
    const taskEditData = ref()
    const taskEditDomElement = ref()
    const logFilter = ref("")
    const logLevel = ref(localStorage.getItem("defaultLogLevel") || "INFO")
    const isDrawerOpen = ref(false)
    const isShowDescriptionOpen = ref(false)
    const isShowConditionOpen = ref(false)
    const isInspectOpen = ref(false)
    const inspectTab = ref<"logs" | "outputs" | "metrics">("logs")
    const isReplayPickerOpen = ref(false)
    const selectedTask = ref()
    const taskRunOutputsById = ref<Record<string, Record<string, unknown>>>({})
    const loadingOutputsTaskRunIds = ref<Set<string>>(new Set())
    const replayExecution = ref()
    const replayTaskRun = ref()
    const replayRef = ref<InstanceType<typeof Restart>>()

    const authStore = useAuthStore()

    const replayEnabled = computed(() => {
        const currentExecution = executionsStore.execution as any
        if (!currentExecution?.state || State.isRunning(currentExecution.state.current)) {
            return false
        }
        return authStore.user?.isAllowed(resource.EXECUTION, action.REPLAY, currentExecution.namespace) === true
    })

    const replayAttemptIndex = computed(() =>
        replayTaskRun.value?.attempts ? replayTaskRun.value.attempts.length - 1 : undefined,
    )

    onMounted(() => {
        // Regenerate graph on window resize
        observeWidth()
        pluginsStore.fetchIcons()
        setMinZoom(0.1)
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
        isShowCustomActionOpen.value = false
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

    const observeWidth = () => {
        if(vueFlow.value){
            const resizeObserver = new ResizeObserver(function () {
                clearTimeout(timer.value)
                timer.value = setTimeout(() => {
                    nextTick(() => {
                        fitView()
                    })
                }, 50) as any
            })
            resizeObserver.observe(vueFlow.value)
        }
    }

    const onDelete = (event: any) => {
        const flowParsed = YAML_UTILS.parse(props.source)
        toast.confirm(
            t("delete task confirm", {taskId: event.id}),
            async () => {
                const section = event.section ? event.section.toLowerCase() : SECTIONS.TASKS.toLowerCase()
                if (
                    section === SECTIONS.TASKS.toLowerCase() &&
                    flowParsed.tasks.length === 1 &&
                    flowParsed.tasks.map((e: any) => e.id).includes(event.id)
                ) {
                    coreStore.message = {
                        variant: "error",
                        title: t("can not delete"),
                        content: t("can not have less than 1 task"),
                    }
                    return
                }
                const updatedYmlSource = YAML_UTILS.deleteBlock({
                    source: props.source ?? "",
                    section,
                    key: event.id,
                })
                emit(
                    "on-edit",
                    updatedYmlSource,
                    true,
                )
            },
        )
    }

    const onCreateNewTask = (event: [string, "before" | "after"]) => {
        topologyClick.value = {
            action: "create",
            params: {
                section: SECTIONS.TASKS.toLowerCase() as any,
                position: event[1],
                id: event[0],
            },
        }
    }

    const onEditTask = (event: {
        task: Record<string, any>;
        section?: string;
    }) => {
        topologyClick.value = {
            action: "edit",
            params: {
                section: (event.section ?? SECTIONS.TASKS).toLowerCase() as any,
                id: event.task.id,
            },
        }
    }

    const onAddFlowableError = (event: any) => {
        taskEditData.value = {
            action: "add_flowable_error",
            taskId: event.task.id,
        }
        taskEditDomElement.value.$refs.taskEdit.click()
    }

    const fitViewOrientation = () => {
        if(vueFlow.value){
            const resizeObserver = new ResizeObserver(() => {
                clearTimeout(timer.value)
                nextTick(() => {
                    fitView()
                })
            })
            resizeObserver.observe(vueFlow.value)
        }
    }

    const toggleOrientation = () => {
        isHorizontal.value = !isHorizontal.value
        isHorizontalLS.value = isHorizontal.value
        fitViewOrientation()
    }

    const openFlow = (data: any) => {
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

    const openInspect = (event: unknown, tab: "logs" | "outputs" | "metrics") => {
        resetDrawerSections()
        selectedTask.value = event
        inspectTab.value = tab
        isInspectOpen.value = true
        isDrawerOpen.value = true
    }

    const showLogs = (event: string) => openInspect(event, "logs")

    const showOutputs = (event: unknown) => openInspect(event, "outputs")

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

    const openReplayDialog = (taskExecution: unknown, taskRun: unknown) => {
        replayExecution.value = taskExecution
        replayTaskRun.value = taskRun
        isDrawerOpen.value = false
        nextTick(() => replayRef.value?.open())
    }

    const onReplayTask = (event: {execution: unknown; taskRuns: unknown[]}) => {
        if (event.taskRuns.length === 1) {
            openReplayDialog(event.execution, event.taskRuns[0])
            return
        }
        resetDrawerSections()
        selectedTask.value = event
        isReplayPickerOpen.value = true
        isDrawerOpen.value = true
    }

    const onSearch = (search: string) => {
        logFilter.value = search
    }

    const onLevelChange = (level: string) => {
        logLevel.value = level
    }

    const showDescription = (event: string) => {
        resetDrawerSections()
        selectedTask.value = event
        isShowDescriptionOpen.value = true
        isDrawerOpen.value = true
    }

    const showCondition = (event: {task: string}) => {
        resetDrawerSections()
        selectedTask.value = event.task
        isShowConditionOpen.value = true
        isDrawerOpen.value = true
    }

    const customActionMeta = ref<{ label: string; taskProp: string; lang: string }>()
    const isShowCustomActionOpen = ref(false)

    const showCustomAction = (event: { task: any; customAction: { label: string; taskProp: string; lang: string } }) => {
        const fullTask = taskWithSource(event.task)
        if (!event.customAction.taskProp) {
            const runnerType = fullTask?.taskRunner?.type as string | undefined
            taskModalCtx.value = {
                taskType: runnerType ?? fullTask?.type,
                title: event.customAction.label,
                task: fullTask,
                execution: exec.value,
                namespace: props.namespace,
                flowId: props.flowId,
                tenant: tenant.value,
                source: flowSource.value,
                progress: taskProgress(fullTask?.id),
                fetchOutputs: fetchTaskOutputs(fullTask?.id),
                fetchMetrics: fetchTaskMetrics(fullTask?.id),
            }
            isTaskModalOpen.value = true
            return
        }
        resetDrawerSections()
        selectedTask.value = fullTask
        customActionMeta.value = event.customAction
        isShowCustomActionOpen.value = true
        isDrawerOpen.value = true
    }

    const expandSubflow = (event: any) => {
        emit("expand-subflow", event)
    }
</script>

<style scoped lang="scss">
.tab-body {
    padding-block: var(--ks-spacing-3) var(--ks-spacing-6);
}

.outputs-view {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-5);
}

.taskrun-card {
    &__header {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        margin-bottom: var(--ks-spacing-2);
    }

    &__value {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    &__empty {
        display: block;
        padding: var(--ks-spacing-3);
        border: 1px dashed var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
    }
}

.replay-picker {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-2);
    margin-top: var(--ks-spacing-4);

    &__hint {
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
    }

    &__item {
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

    &__value {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }
}

.vueflow {
    height: 100%;
    width: 100%;
    position: relative;

    // Anchor the state icon (playground-button) to the node header area, not
    // the full VueFlow node element, so it doesn't overlap plugin UI details.
    :deep(.main-content) {
        position: relative;
    }

    // Hover: the topology handler adds an inline `outline` to linked nodes,
    // but outline renders outside the existing state border creating two rings.
    // Override: suppress the outline and shift the border-color instead so the
    // hover highlight cleanly replaces the success/failure color.
    :deep(.vue-flow__node.rounded-3) {
        outline: none !important;

        .node-wrapper {
            border-color: var(--bs-gray-900) !important;
        }
    }
}

</style>

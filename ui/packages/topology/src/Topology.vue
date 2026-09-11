<template>
    <VueFlow
        :id="id"
        :defaultMarkerColor="cssVariable('--ks-topology-dash')"
        fitViewOnInit
        :nodesDraggable="false"
        :nodesConnectable="false"
        :elevateNodesOnSelect="false"
        :elevateEdgesOnSelect="false"
    >
        <Background :patternColor="cssVariable('--ks-topology-bg')" />

        <Panel v-if="showDetailsToggle" position="top-right">
            <KsSwitch v-model="showExtraDetails" :activeText="$t('show more details')" size="small"/>
        </Panel>

        <template #node-cluster="clusterProps">
            <ClusterNode
                v-bind="clusterProps"
                @collapse="collapseCluster($event, true)"
            />
        </template>

        <template #node-dot="dotProps">
            <DotNode
                v-bind="dotProps as any"
            />
        </template>

        <template #node-task="taskProps">
            <TaskNode
                v-bind="taskProps"
                :icons="icons"
                :loadIcon="loadIcon"
                :playgroundEnabled="playgroundEnabled"
                :playgroundReadyToStart="playgroundReadyToStart"
                :replayEnabled="replayEnabled"
                :customActions="customActions"
                :showDetails="showDetails"
                @edit="emit(EVENTS.EDIT, $event)"
                @delete="emit(EVENTS.DELETE, $event)"
                @duplicate="emit(EVENTS.DUPLICATE, $event)"
                @run-task="emit(EVENTS.RUN_TASK, $event)"
                @expand="expand($event)"
                @open-link="emit(EVENTS.OPEN_LINK, $event)"
                @show-logs="emit(EVENTS.SHOW_LOGS, $event)"
                @show-outputs="emit(EVENTS.SHOW_OUTPUTS, $event)"
                @replay-task="emit(EVENTS.REPLAY_TASK, $event)"
                @show-description="emit(EVENTS.SHOW_DESCRIPTION, $event)"
                @show-condition="emit(EVENTS.SHOW_CONDITION, $event)"
                @show-custom-action="emit(EVENTS.SHOW_CUSTOM_ACTION, $event)"
                @show-details="emit(EVENTS.SHOW_DETAILS, $event)"
                @mouseover="onMouseOver($event)"
                @mouseleave="onMouseLeave()"
                @add-error="emit('on-add-flowable-error', $event)"
                @taskDragStart="onTaskDragStart"
                @taskDragEnd="onTaskDragEnd"
                :dragging="draggingNodeId === taskProps.id"
                :enableSubflowInteraction="enableSubflowInteraction"
            >
                <template #details>
                    <slot name="taskDetails" v-bind="taskProps" />
                </template>
                <template #taskActions="taskActionProps">
                    <slot name="taskActions" v-bind="{...taskProps, ...taskActionProps}" />
                </template>
            </TaskNode>
        </template>

        <template #node-custom="taskProps">
            <BasicNode
                v-bind="taskProps"
                :icons="icons"
                :loadIcon="loadIcon"
            />
        </template>

        <template #node-trigger="triggerProps">
            <TriggerNode
                v-bind="triggerProps as any"
                :icons="icons"
                :loadIcon="loadIcon"
                :isReadOnly="isReadOnly"
                :isAllowedEdit="isAllowedEdit"
                @delete="emit(EVENTS.DELETE, $event)"
                @edit="emit(EVENTS.EDIT, $event)"
                @show-description="emit(EVENTS.SHOW_DESCRIPTION, $event)"
            />
        </template>

        <template #node-collapsedcluster="CollapsedProps">
            <CollapsedClusterNode
                v-bind="CollapsedProps as any"
                @expand="expand($event)"
            />
        </template>

        <template #edge-edge="EdgeProps">
            <EdgeNode
                v-bind="EdgeProps"
                :yamlSource="source"
                @add-task="emit(EVENTS.ADD_TASK, $event)"
                @drag-over-edge="dropEdgeId = $event"
                @drop-task="onDropTask"
                :isReadOnly="isReadOnly"
                :isAllowedEdit="isAllowedEdit"
            />
        </template>

        <Controls v-if="controlsShown" :showZoom="false" :showInteractive="false" :showFitView="false">
            <KsTooltip :content="$t('topology-graph.zoom-in')" placement="right">
                <ControlButton @click.stop="zoomIn()">
                    <Plus />
                </ControlButton>
            </KsTooltip>
            <KsTooltip :content="$t('topology-graph.zoom-out')" placement="right">
                <ControlButton @click.stop="zoomOut()">
                    <Minus />
                </ControlButton>
            </KsTooltip>
            <KsTooltip :content="$t('topology-graph.zoom-fit')" placement="right">
                <ControlButton @click.stop="fitView()">
                    <Fullscreen />
                </ControlButton>
            </KsTooltip>
            <KsTooltip v-if="toggleOrientationButton" :content="$t('topology-graph.graph-orientation')" placement="right">
                <ControlButton @click.stop="emit('toggle-orientation', $event)">
                    <component :is="isHorizontal ? AlignHorizontalCenter : AlignVerticalCenter" />
                </ControlButton>
            </KsTooltip>
            <KsTooltip :content="$t('download')" placement="right">
                <ControlButton @click.stop="toggleDropdown">
                    <Download />
                </ControlButton>
            </KsTooltip>
            <KsTooltip v-if="collapsed.size > 0" :content="$t('expand all')" placement="right">
                <ControlButton @click.stop="uncollapseAll()">
                    <ArrowExpandAll />
                </ControlButton>
            </KsTooltip>
            <ul v-if="isDropdownOpen" class="exporting">
                <li @click="exportAsImage('jpeg')" class="item">
                    Export as .JPEG
                </li>
                <li @click="exportAsImage('png')" class="item">
                    Export as .PNG
                </li>
            </ul>
        </Controls>
    </VueFlow>

    <div
        v-if="dragGhost"
        class="drag-ghost"
        :style="{transform: `translate(${dragGhost.x}px, ${dragGhost.y}px)`}"
        aria-hidden="true"
    >
        <component
            :is="taskIconComponent"
            class="drag-ghost-icon"
            :cls="dragGhost.cls"
            variable="--ks-topology-icon-color"
            :icons="icons"
            :loadIcon="loadIcon"
            onlyIcon
        />
        <span class="drag-ghost-label">{{ dragGhost.label }}</span>
    </div>
</template>

<script lang="ts" setup>
    import {computed, nextTick, onMounted, onUnmounted, provide, ref, watch} from "vue"
    import {useVueFlow, VueFlow, Panel} from "@vue-flow/core"
    import {ControlButton, Controls} from "@vue-flow/controls"
    import {Background} from "@vue-flow/background"
    import ClusterNode from "./nodes/ClusterNode.vue"
    import DotNode from "./nodes/DotNode.vue"
    import EdgeNode from "./nodes/EdgeNode.vue"
    import TaskNode from "./nodes/TaskNode.vue"
    import TriggerNode from "./nodes/TriggerNode.vue"
    import CollapsedClusterNode from "./nodes/CollapsedClusterNode.vue"
    import Plus from "vue-material-design-icons/Plus.vue"
    import Minus from "vue-material-design-icons/Minus.vue"
    import Fullscreen from "vue-material-design-icons/Fullscreen.vue"
    import AlignHorizontalCenter from "vue-material-design-icons/AlignHorizontalCenter.vue"
    import AlignVerticalCenter from "vue-material-design-icons/AlignVerticalCenter.vue"
    import Download from "vue-material-design-icons/Download.vue"
    import ArrowExpandAll from "vue-material-design-icons/ArrowExpandAll.vue"
    import {cssVar as cssVariable, State, KsSwitch, KsTooltip, useTaskIcon} from "@kestra-io/design-system"
    import {CLUSTER_PREFIX} from "./utils/constants"
    import {type CustomActionConfig, type ShowDetailsConfig, EVENTS, NODE_SIZES} from "./utils/constants"
    import * as VueFlowUtils from "./utils/vueFlowUtils"
    import {afterLastDot} from "./utils/utils"
    import {useScreenshot} from "./composables/useScreenshot"
    import {EXECUTION_INJECTION_KEY, SUBFLOWS_EXECUTIONS_INJECTION_KEY, SHOW_EXTRA_DETAILS_INJECTION_KEY, VALIDATION_ISSUES_INJECTION_KEY, FOCUSED_TASK_INJECTION_KEY, DROP_EDGE_INJECTION_KEY, DRAGGING_NODE_INJECTION_KEY} from "./injectionKeys"
    import BasicNode from "./nodes/BasicNode.vue"

    const props = withDefaults(defineProps<{
        id: string;
        isHorizontal?: boolean;
        isReadOnly?: boolean;
        isAllowedEdit?: boolean;
        source: string;
        toggleOrientationButton?: boolean;
        flowGraph: VueFlowUtils.FlowGraph;
        flowId?: string;
        namespace?: string;
        expandedSubflows?: string[];
        icons?: Record<string, any>;
        // Per-class resolver for icons absent from `icons`, which only indexes the plugins
        // registered on this instance (kestra-io/kestra#18129).
        loadIcon?: (cls: string) => Promise<any>;
        enableSubflowInteraction?: boolean;
        execution?: any;
        subflowsExecutions?: Record<string, VueFlowUtils.GraphExecution>;
        playgroundEnabled?: boolean;
        playgroundReadyToStart?: boolean;
        replayEnabled?: boolean;
        getNodeDimensions?: (node: any, getNodeWidth: (node: any) => number, getNodeHeight: (node: any) => number) => { width: number, height: number };
        customActions?: Record<string, CustomActionConfig>;
        showDetails?: Record<string, ShowDetailsConfig>;
        showDetailsToggle?: boolean;
        // Bump this from the caller whenever data rendered *inside* the taskDetails slot (e.g.
        // live metrics or progress) changes but isn't itself part of `execution`/`flowGraph` — the
        // slot content is only re-evaluated when a node's graph data is regenerated.
        taskDetailsVersion?: number;
        validationIssuesByTask?: Map<string, string[]>;
        focusedTaskId?: string;
    }>(), {
        isHorizontal: true,
        isReadOnly: true,
        isAllowedEdit: false,
        toggleOrientationButton: false,
        flowId: undefined,
        namespace: undefined,
        expandedSubflows: () => [],
        icons: () => ({}),
        loadIcon: undefined,
        execution: undefined,
        enableSubflowInteraction: true,
        playgroundEnabled: false,
        playgroundReadyToStart: false,
        replayEnabled: false,
        subflowsExecutions: () => ({}),
        getNodeDimensions: undefined,
        customActions: () => ({}),
        showDetails: () => ({}),
        showDetailsToggle: true,
        taskDetailsVersion: undefined,
        validationIssuesByTask: undefined,
        focusedTaskId: undefined,
    })

    const isRunning = computed(() => State.isRunning(props.execution?.state?.current) === true)

    const showExtraDetails = ref(false)
    const {getNodes, getEdges, getElements, onNodesInitialized, fitView, zoomIn, zoomOut, setElements, removeEdges, removeNodes, removeSelectedElements, vueFlowRef} = useVueFlow(props.id)
    const edgeReplacer = ref({})
    const hiddenNodes = ref<string[]>([])
    const collapsed = ref(new Set<string>())
    const clusterToNode = ref([])
    const {capture} = useScreenshot()

    const effectiveGetNodeDimensions = computed(() => {
        return (node: any, getNodeWidth: (node: any) => number, getNodeHeight: (node: any) => number) => {
            const baseHeight = getNodeHeight(node)
            const dimensions = props.getNodeDimensions
                ? props.getNodeDimensions(node, getNodeWidth, getNodeHeight)
                : {width: getNodeWidth(node), height: baseHeight}

            if (props.execution && (VueFlowUtils.isTaskNode(node) || VueFlowUtils.isTriggerNode(node) || VueFlowUtils.isCustomNode(node))) {
                dimensions.width = NODE_SIZES.TASK_WIDTH_EXECUTION
            }

            if (VueFlowUtils.isTaskNode(node) && !showExtraDetails.value) {
                return {...dimensions, height: baseHeight}
            }

            return dimensions
        }
    })

    provide(EXECUTION_INJECTION_KEY, computed(() => props.execution))
    provide(SUBFLOWS_EXECUTIONS_INJECTION_KEY, computed(() => props.subflowsExecutions))
    provide(SHOW_EXTRA_DETAILS_INJECTION_KEY, showExtraDetails)
    provide(VALIDATION_ISSUES_INJECTION_KEY, computed(() => props.validationIssuesByTask ?? new Map()))
    provide(FOCUSED_TASK_INJECTION_KEY, computed(() => props.focusedTaskId))

    const dropEdgeId = ref<string | undefined>(undefined)
    const draggingNodeId = ref<string | undefined>(undefined)

    provide(DROP_EDGE_INJECTION_KEY, computed(() => dropEdgeId.value))
    provide(DRAGGING_NODE_INJECTION_KEY, computed(() => Boolean(draggingNodeId.value)))

    const taskIconComponent = useTaskIcon()
    const dragGhost = ref<{label: string; cls?: string; x: number; y: number} | undefined>(undefined)

    function onTaskDragStart({nodeId, label, cls}: {nodeId: string; label: string; cls?: string}) {
        draggingNodeId.value = nodeId
        dragGhost.value = {label, cls, x: -9999, y: -9999}
        window.addEventListener("dragover", onGhostMove)
    }

    function onGhostMove(event: DragEvent) {
        if (!dragGhost.value) return
        // A drag leaving the window reports 0,0; keeping the last real point avoids a jump home.
        if (!event.clientX && !event.clientY) return
        dragGhost.value = {...dragGhost.value, x: event.clientX, y: event.clientY}
    }

    function onTaskDragEnd() {
        dragGhost.value = undefined
        window.removeEventListener("dragover", onGhostMove)
        dropEdgeId.value = undefined
        // Cleared a tick late so the click that ends the drag does not also open the task.
        setTimeout(() => (draggingNodeId.value = undefined), 0)
    }

    function onDropTask({taskId, target}: {taskId: string; target: {refId: string}}) {
        const id = afterLastDot(taskId)
        onTaskDragEnd()
        if (!id || id === target.refId) return
        emit(EVENTS.MOVE_TASK, {taskId: id, target})
    }

    // A drag that ends outside any edge fires no drop, so the flags are cleared on the window too.
    onMounted(() => {
        window.addEventListener("dragend", onTaskDragEnd)
        window.addEventListener("blur", onTaskDragEnd)
    })

    onUnmounted(() => {
        window.removeEventListener("dragend", onTaskDragEnd)
        window.removeEventListener("blur", onTaskDragEnd)
    })

    const emit = defineEmits(
        [
            EVENTS.EDIT,
            EVENTS.DELETE,
            EVENTS.DUPLICATE,
            EVENTS.RUN_TASK,
            EVENTS.OPEN_LINK,
            EVENTS.SHOW_LOGS,
            EVENTS.SHOW_OUTPUTS,
            EVENTS.REPLAY_TASK,
            EVENTS.SHOW_DESCRIPTION,
            "on-add-flowable-error",
            EVENTS.ADD_TASK,
            "toggle-orientation",
            "loading",
            "expand-subflow",
            EVENTS.SHOW_CONDITION,
            EVENTS.SHOW_CUSTOM_ACTION,
            EVENTS.SHOW_DETAILS,
            EVENTS.MOVE_TASK,
        ],
    )

    onMounted(() => {
        generateGraph()
    })

    watch(() => props.flowGraph, () => {
        generateGraph()
    })

    watch(() => props.isHorizontal, () => {
        generateGraph()
    })

    watch(showExtraDetails, () => {
        generateGraph()
    })

    watch(isRunning, () => {
        generateGraph()
    })

    const refitOnNodesInitialized = ref(false)
    onNodesInitialized(() => {
        if (refitOnNodesInitialized.value) {
            refitOnNodesInitialized.value = false
            fitView()
        }
    })

    watch(() => props.taskDetailsVersion, () => {
        generateGraph()
    })

    const generateGraph = () => {
        removeEdges(getEdges.value)
        removeNodes(getNodes.value)
        removeSelectedElements(getElements.value)

        nextTick(() => {
            emit("loading", true)

            const oldCollapsed = collapsed.value
            collapsed.value = new Set<string>()
            hiddenNodes.value = []
            edgeReplacer.value = {}
            clusterToNode.value = []
            oldCollapsed.forEach(n => collapseCluster(CLUSTER_PREFIX + n, false))

            const elements = VueFlowUtils.generateGraph(
                props.id,
                props.flowId,
                props.namespace,
                props.flowGraph,
                props.source,
                hiddenNodes.value,
                props.isHorizontal,
                edgeReplacer.value,
                collapsed.value,
                clusterToNode.value,
                props.isReadOnly,
                props.isAllowedEdit,
                props.enableSubflowInteraction,
                effectiveGetNodeDimensions.value,
                isRunning.value,
            )

            if (elements) {
                setElements(elements)
                refitOnNodesInitialized.value = true
                emit("loading", false)
            }
        })
    }

    const HOVERED_NODE_CLASS = "topology-node-hovered"

    function setNodeInteractionClass(node: any, cls: string, add: boolean) {
        const classes = (node.class || "").split(" ").filter(Boolean)
        if (add) {
            if (!classes.includes(cls)) classes.push(cls)
        } else {
            const idx = classes.indexOf(cls)
            if (idx > -1) classes.splice(idx, 1)
        }
        node.class = classes.join(" ")
    }

    const onMouseOver = (node: any) => {
        VueFlowUtils.linkedElements(props.id, node.uid).forEach((n) => {
            if (n?.type === "task") {
                setNodeInteractionClass(n, HOVERED_NODE_CLASS, true)
            }
        })
    }

    const onMouseLeave = () => {
        resetNodesStyle()
    }

    const resetNodesStyle = () => {
        getNodes.value.filter(n => n.type === "task" || n.type === "trigger")
            .forEach(n => {
                n.style = {...n.style, opacity: "1"}
                setNodeInteractionClass(n, HOVERED_NODE_CLASS, false)
            })
    }

    const collapseCluster = (clusterUid: string, regenerate: boolean) => {
        const cluster: any = props.flowGraph.clusters.find(c => c.cluster.uid.endsWith(clusterUid))
        if (!cluster) return
        const nodeId = clusterUid.replace(CLUSTER_PREFIX, "")
        collapsed.value.add(nodeId)

        hiddenNodes.value = hiddenNodes.value.concat(cluster.nodes)
        hiddenNodes.value = hiddenNodes.value.concat([cluster.cluster.uid] as string[])
        edgeReplacer.value = {
            ...edgeReplacer.value,
            [cluster.cluster.uid]: nodeId,
            [cluster.start]: nodeId,
            [cluster.end]: nodeId,
        }

        for (let child of cluster.nodes) {
            if (props.flowGraph.clusters.map(c => c.cluster.uid).includes(child)) {
                collapseCluster(child, false)
            }
        }

        if (regenerate) {
            generateGraph()
        }
    }

    const expand = (expandData: any) => {
        const taskTypesWithSubflows = [
            "io.kestra.core.tasks.flows.Flow", "io.kestra.core.tasks.flows.Subflow", "io.kestra.plugin.core.flow.Subflow",
            "io.kestra.core.tasks.flows.ForEachItem$ForEachItemExecutable", "io.kestra.plugin.core.flow.ForEachItem$ForEachItemExecutable",
        ]
        if (taskTypesWithSubflows.includes(expandData.type) && !props.expandedSubflows.includes(expandData.id)) {
            emit("expand-subflow", [...props.expandedSubflows, expandData.id])
            return
        }
        edgeReplacer.value = {}
        hiddenNodes.value = []
        clusterToNode.value = []
        collapsed.value.delete(expandData.id)

        collapsed.value.forEach(n => collapseCluster(n, false))

        generateGraph()
    }


    const uncollapseAll = () => {
        collapsed.value = new Set()
        hiddenNodes.value = []
        edgeReplacer.value = {}
        clusterToNode.value = []
        generateGraph()
    }

    const controlsShown = ref(true)
    const isDropdownOpen = ref(false)
    const toggleDropdown = () => isDropdownOpen.value = !isDropdownOpen.value
    function exportAsImage(type: "jpeg" | "png") {
        if (!vueFlowRef.value) {
            console.warn("Flow not found")
            return
        }

        controlsShown.value = false
        capture(vueFlowRef.value, {type, shouldDownload: true})
            .then(() => controlsShown.value = true)
            .finally(() => isDropdownOpen.value = false)
    }
</script>

<style scoped lang="scss">
    :deep(.unused-path) {
        opacity: 0.3;
    }

    /* vue-flow flags its own node wrapper, which is the only element that knows a node can be
       picked up and when it is being dragged. The pane sets `grab` for panning and every node
       inherits it, so a node that cannot be moved has to opt back out. */
    :deep(.vue-flow__node.draggable) {
        cursor: grab;
    }

    :deep(.vue-flow__node:not(.draggable)) {
        cursor: default;
    }

    :deep(.vue-flow__node.dragging) {
        cursor: grabbing;
        /* vue-flow writes `z-index: 1` inline on every node, so the card being dragged slides
           *under* the ones that come later in the DOM; only `!important` outranks that. It stays
           below the drop marker on purpose. */
        z-index: 5 !important;
    }

    :deep(.vue-flow__node .node-wrapper) {
        transition: transform 0.15s ease, box-shadow 0.15s ease;
    }

    :deep(.vue-flow__node.dragging .node-wrapper) {
        transform: scale(1.04);
        box-shadow: 0 0.5rem 1rem var(--ks-shadow-elevated);
    }

    @media (prefers-reduced-motion: reduce) {
        :deep(.vue-flow__node .node-wrapper) {
            transition: none;
        }

        :deep(.vue-flow__node.dragging .node-wrapper) {
            transform: none;
        }
    }

    .drag-ghost {
        position: fixed;
        top: 0;
        left: 0;
        z-index: 20;
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        margin: var(--ks-spacing-2) 0 0 var(--ks-spacing-2);
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        max-width: 16rem;
        background: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-strong);
        border-radius: var(--ks-radius-base);
        box-shadow: 0 0.5rem 1rem var(--ks-shadow-elevated);
        pointer-events: none;
        rotate: -2deg;
    }

    .drag-ghost-icon {
        flex-shrink: 0;
        width: var(--ks-icon-size-lg);
        height: var(--ks-icon-size-lg);
    }

    .drag-ghost-label {
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-primary);
    }

    .exporting {
        position: absolute;
        bottom: 0px;
        left: 40px;
        padding: 0;
        margin: 0;
        z-index: 1000;
        list-style-type: none;
        background: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-default);
        box-shadow: 0 12px 12px rgba(130, 103, 158, 0.1019607843);
        border-radius: 5px;
        text-align:left;

        & .item {
            padding: 5px 8px;
            cursor: pointer;
            color: var(--ks-text-primary);
            font-size: var(--ks-font-size-sm);
            width: 110px;

            &:first-child{
                border-bottom: 1px solid var(--ks-border-default);
            }

            &:hover {
                background: var(--ks-btn-secondary-bg-hover);
            }
        }
    }
</style>

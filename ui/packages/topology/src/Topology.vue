<template>
    <VueFlow
        :id="id"
        :defaultMarkerColor="cssVariable('--ks-topology-edge-color')"
        fitViewOnInit
        :nodesDraggable="false"
        :nodesConnectable="false"
        :elevateNodesOnSelect="false"
        :elevateEdgesOnSelect="false"
    >
        <Background :patternColor="cssVariable('--ks-topology-dot-color')" />

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
                :iconComponent="iconComponent"
                :playgroundEnabled="playgroundEnabled"
                :playgroundReadyToStart="playgroundReadyToStart"
                :customActions="customActions"
                :showDetails="showDetails"
                @edit="emit(EVENTS.EDIT, $event)"
                @delete="emit(EVENTS.DELETE, $event)"
                @run-task="emit(EVENTS.RUN_TASK, $event)"
                @expand="expand($event)"
                @open-link="emit(EVENTS.OPEN_LINK, $event)"
                @show-logs="emit(EVENTS.SHOW_LOGS, $event)"
                @show-description="emit(EVENTS.SHOW_DESCRIPTION, $event)"
                @show-condition="emit(EVENTS.SHOW_CONDITION, $event)"
                @show-custom-action="emit(EVENTS.SHOW_CUSTOM_ACTION, $event)"
                @show-details="emit(EVENTS.SHOW_DETAILS, $event)"
                @mouseover="onMouseOver($event)"
                @mouseleave="onMouseLeave()"
                @add-error="emit('on-add-flowable-error', $event)"
                :enableSubflowInteraction="enableSubflowInteraction"
            >
                <template #details>
                    <slot name="taskDetails" v-bind="taskProps" />
                </template>
            </TaskNode>
        </template>

        <template #node-custom="taskProps">
            <BasicNode
                v-bind="taskProps"
                :icons="icons"
                :iconComponent="iconComponent"
            />
        </template>

        <template #node-trigger="triggerProps">
            <TriggerNode
                v-bind="triggerProps as any"
                :icons="icons"
                :iconComponent="iconComponent"
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
                :isReadOnly="isReadOnly"
                :isAllowedEdit="isAllowedEdit"
            />
        </template>

        <Controls v-if="controlsShown" :showInteractive="false" :showFitView="false">
            <ControlButton @click.stop="showExtraDetails = !showExtraDetails" :class="{'active': showExtraDetails}">
                <Information />
            </ControlButton>
            <ControlButton @click.stop="fitView()">
                <svg viewBox="0 0 32 32" style="width:12px;height:12px"><path d="M3.692 4.63c0-.53.4-.938.939-.938h5.215V0H4.708C2.13 0 0 2.054 0 4.63v5.216h3.692V4.631zM27.354 0h-5.2v3.692h5.17c.53 0 .984.4.984.939v5.215H32V4.631A4.624 4.624 0 0 0 27.354 0zm.954 24.83c0 .532-.4.94-.939.94h-5.215v3.768h5.215c2.577 0 4.631-2.13 4.631-4.707v-5.139h-3.692v5.139zm-23.677.94a.919.919 0 0 1-.939-.94v-5.138H0v5.139c0 2.577 2.13 4.707 4.708 4.707h5.138V25.77H4.631z" fill="currentColor" /></svg>
            </ControlButton>
            <ControlButton @click.stop="uncollapseAll()" v-if="collapsed.size > 0">
                <ArrowExpandAll />
            </ControlButton>
            <ControlButton @click.stop="emit('toggle-orientation', $event)" v-if="toggleOrientationButton">
                <component :is="isHorizontal ? SplitCellsHorizontal : SplitCellsVertical" />
            </ControlButton>
            <ControlButton @click.stop="toggleDropdown">
                <Download />
            </ControlButton>
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
</template>

<script lang="ts" setup>
    import {computed, nextTick, onMounted, provide, ref, watch} from "vue"
    import {useVueFlow, VueFlow} from "@vue-flow/core"
    import type {XYPosition} from "@vue-flow/core"
    import {ControlButton, Controls} from "@vue-flow/controls"
    import {Background} from "@vue-flow/background"
    import ClusterNode from "./nodes/ClusterNode.vue"
    import DotNode from "./nodes/DotNode.vue"
    import EdgeNode from "./nodes/EdgeNode.vue"
    import TaskNode from "./nodes/TaskNode.vue"
    import TriggerNode from "./nodes/TriggerNode.vue"
    import CollapsedClusterNode from "./nodes/CollapsedClusterNode.vue"
    import SplitCellsVertical from "./assets/icons/SplitCellsVertical.vue"
    import SplitCellsHorizontal from "./assets/icons/SplitCellsHorizontal.vue"
    import Download from "vue-material-design-icons/Download.vue"
    import Information from "vue-material-design-icons/Information.vue"
    import ArrowExpandAll from "vue-material-design-icons/ArrowExpandAll.vue"
    import {cssVar as cssVariable} from "@kestra-io/design-system"
    import {CLUSTER_PREFIX} from "./utils/constants"
    import * as flowYamlUtils from "./utils/flowYamlUtils"
    import {type CustomActionConfig, type ShowDetailsConfig, EVENTS, NODE_SIZES} from "./utils/constants"
    import * as Utils from "./utils/utils"
    import * as VueFlowUtils from "./utils/vueFlowUtils"
    import {useScreenshot} from "./composables/useScreenshot"
    import {EXECUTION_INJECTION_KEY, SUBFLOWS_EXECUTIONS_INJECTION_KEY, SHOW_EXTRA_DETAILS_INJECTION_KEY} from "./injectionKeys"
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
        iconComponent?: any;
        enableSubflowInteraction?: boolean;
        execution?: any;
        subflowsExecutions?: Record<string, any[]>;
        playgroundEnabled?: boolean;
        playgroundReadyToStart?: boolean;
        getNodeDimensions?: (node: any, getNodeWidth: (node: any) => number, getNodeHeight: (node: any) => number) => { width: number, height: number };
        customActions?: Record<string, CustomActionConfig>;
        showDetails?: Record<string, ShowDetailsConfig>;
        animated?: boolean;
    }>(), {
        isHorizontal: true,
        isReadOnly: true,
        isAllowedEdit: false,
        toggleOrientationButton: false,
        flowId: undefined,
        namespace: undefined,
        expandedSubflows: () => [],
        icons: () => ({}),
        iconComponent: undefined,
        execution: undefined,
        enableSubflowInteraction: true,
        playgroundEnabled: false,
        playgroundReadyToStart: false,
        subflowsExecutions: () => ({}),
        getNodeDimensions: undefined,
        customActions: () => ({}),
        showDetails: () => ({}),
        animated: true,
    })

    const dragging = ref(false)
    const showExtraDetails = ref(false)
    const lastPosition = ref<XYPosition | null>()
    const {getNodes, getEdges, getElements, onNodeDrag, onNodeDragStart, onNodeDragStop, fitView, setElements, removeEdges, removeNodes, removeSelectedElements, vueFlowRef} = useVueFlow(props.id)
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

            if (!showExtraDetails.value && VueFlowUtils.isTaskNode(node)) {
                return {...dimensions, height: baseHeight}
            }

            if (showExtraDetails.value && VueFlowUtils.isTaskNode(node)) {
                const taskType = node?.task?.type as string | undefined
                const hasDetailsAction = Boolean(
                    (taskType && props.customActions?.[taskType]) ||
                        (taskType && props.showDetails?.[taskType]),
                )
                if (hasDetailsAction) {
                    return {...dimensions, height: Math.max(dimensions.height, NODE_SIZES.TASK_EXPANDED_FALLBACK_HEIGHT)}
                }
            }

            return dimensions
        }
    })

    provide(EXECUTION_INJECTION_KEY, computed(() => props.execution))
    provide(SUBFLOWS_EXECUTIONS_INJECTION_KEY, computed(() => props.subflowsExecutions))
    provide(SHOW_EXTRA_DETAILS_INJECTION_KEY, showExtraDetails)


    const emit = defineEmits(
        [
            EVENTS.EDIT,
            EVENTS.DELETE,
            EVENTS.RUN_TASK,
            EVENTS.OPEN_LINK,
            EVENTS.SHOW_LOGS,
            EVENTS.SHOW_DESCRIPTION,
            "on-add-flowable-error",
            EVENTS.ADD_TASK,
            "toggle-orientation",
            "loading",
            "swapped-task",
            "message",
            "expand-subflow",
            EVENTS.SHOW_CONDITION,
            EVENTS.SHOW_CUSTOM_ACTION,
            EVENTS.SHOW_DETAILS,
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
            oldCollapsed.forEach(n => collapseCluster(CLUSTER_PREFIX + n, false, false))

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
                props.animated,
            )

            if (elements) {
                setElements(elements)
                fitView()
                emit("loading", false)
            }
        })
    }

    const HOVERED_NODE_CLASS = "topology-node-hovered"
    const DROP_TARGET_CLASS = "topology-node-drop-target"

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
        if (!dragging.value) {
            VueFlowUtils.linkedElements(props.id, node.uid).forEach((n) => {
                if (n?.type === "task") {
                    setNodeInteractionClass(n, HOVERED_NODE_CLASS, true)
                }
            })
        }
    }

    const onMouseLeave = () => {
        resetNodesStyle()
    }

    const resetNodesStyle = () => {
        getNodes.value.filter(n => n.type === "task" || n.type === "trigger")
            .forEach(n => {
                n.style = {...n.style, opacity: "1"}
                setNodeInteractionClass(n, HOVERED_NODE_CLASS, false)
                setNodeInteractionClass(n, DROP_TARGET_CLASS, false)
            })
    }

    onNodeDragStart((e) => {
        dragging.value = true
        resetNodesStyle()
        e.node.style = {...e.node.style, zIndex: 1976}
        lastPosition.value = e.node.position
    })

    onNodeDragStop((e: any) => {
        dragging.value = false
        if (e.intersections && checkIntersections(e.intersections, e.node) === null) {
            const taskNode1 = e.node
            const taskNode2 = e.intersections.find((n: any) => n.type === "task")
            if (taskNode2) {
                try {
                    if (props.source) {
                        emit("swapped-task", {
                            newSource: flowYamlUtils.swapBlocks({
                                source: props.source,
                                section: "tasks",
                                key1: Utils.afterLastDot(taskNode1.id) ?? "",
                                key2: Utils.afterLastDot(taskNode2.id) ?? "",
                            }),
                            swappedTasks: [taskNode1.id, taskNode2.id],
                        })
                    }
                } catch (err: any) {
                    emit("message", {
                        variant: "error",
                        title: "cannot swap tasks",
                        message: `${err.message}, ${err.messageOptions}`,
                    })
                    taskNode1.position = lastPosition.value
                }
            } else {
                taskNode1.position = lastPosition.value
            }
        } else {
            e.node.position = lastPosition.value
        }
        resetNodesStyle()
        e.node.style = {...e.node.style, zIndex: 5}
        lastPosition.value = null
    })

    const subflowPrefixes = computed(() => {
        if (!props.flowGraph?.clusters) {
            return []
        }

        return props.flowGraph.clusters.filter(cluster => cluster.cluster.type.endsWith("SubflowGraphCluster"))
            .map(cluster => cluster.cluster.taskNode.uid + ".")
    })

    onNodeDrag((e: any) => {
        resetNodesStyle()
        getNodes.value.filter(n => n.id !== e.node.id).forEach(n => {
            if (n.type === "trigger" || (n.type === "task" && (n.id.startsWith(e.node.id + ".") || e.node.id.startsWith(n.id + "."))) || subflowPrefixes?.value?.some(subflowPrefix => n.id.startsWith(subflowPrefix))) {
                n.style = {...n.style, opacity: "0.5"}
            } else {
                n.style = {...n.style, opacity: "1"}
            }
        })
        if (e.intersections && !checkIntersections(e.intersections, e.node) && e.intersections.filter((n: any) => n.type === "task").length === 1) {
            e.intersections.forEach((n: any) => {
                if (n.type === "task") {
                    setNodeInteractionClass(n, DROP_TARGET_CLASS, true)
                }
            })
            setNodeInteractionClass(e.node, DROP_TARGET_CLASS, true)
        }
    })

    const checkIntersections = (intersections: any, node: any) => {
        const tasksMeet = intersections.filter((n: any) => n.type === "task").map((n: any) => Utils.afterLastDot(n.id))
        if (tasksMeet.length > 1) {
            return "toomuchtaskerror"
        }
        try {
            if (tasksMeet.length === 1 && props.source
                && flowYamlUtils.isParentChildrenRelation({
                    source: props.source,
                    sections: ["tasks", "triggers"],
                    key1: Utils.afterLastDot(tasksMeet[0]) ?? "",
                    key2: Utils.afterLastDot(node.id) ?? "",
                    keyName: "id",
                })
            ) {
                return "parentchildrenerror"
            }
        } catch {
            return "parentchildrenerror"
        }
        if (intersections.filter((n: any) => n.type === "trigger").length > 0) {
            return "triggererror"
        }
        return null
    }

    const collapseCluster = (clusterUid: string, regenerate: boolean, recursive = false) => {
        const cluster: any = props.flowGraph.clusters.find(c => c.cluster.uid.endsWith(clusterUid))
        const nodeId = clusterUid.replace(CLUSTER_PREFIX, "")
        collapsed.value.add(nodeId)

        hiddenNodes.value = hiddenNodes.value.concat(cluster.nodes.filter((e: any) => e !== nodeId || recursive))
        hiddenNodes.value = hiddenNodes.value.concat([cluster.cluster.uid] as string[])
        edgeReplacer.value = {
            ...edgeReplacer.value,
            [cluster.cluster.uid]: nodeId,
            [cluster.start]: nodeId,
            [cluster.end]: nodeId,
        }

        for (let child of cluster.nodes) {
            if (props.flowGraph.clusters.map(c => c.cluster.uid).includes(child)) {
                collapseCluster(child, false, true)
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

        collapsed.value.forEach(n => collapseCluster(n, false, false))

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
    .material-design-icon.download-icon,
    .material-design-icon.information-icon,
    .material-design-icon.arrow-expand-all-icon {
        max-width: 12px;
        display: flex;
        align-items: center;
        justify-content: center;

        svg {
            width: 12px;
            height: 12px;
        }
    }

    :deep(.unused-path) {
        opacity: 0.3;
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
        border: 1px solid var(--ks-border-primary);
        box-shadow: 0 12px 12px rgba(130, 103, 158, 0.1019607843);
        border-radius: 5px;
        text-align:left;

        & .item {
            padding: 5px 8px;
            cursor: pointer;
            color: var(--ks-text-primary);
            font-size: 12px;
            width: 110px;

            &:first-child{
                border-bottom: 1px solid var(--ks-border-primary);
            }

            &:hover {
                background: var(--ks-button-background-secondary-hover);;
            }
        }
    }
</style>

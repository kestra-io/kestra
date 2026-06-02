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

        <Controls v-if="controlsShown" :showZoom="false" :showInteractive="false" :showFitView="false">
            <ControlButton @click.stop="zoomIn()">
                <Plus />
            </ControlButton>
            <ControlButton @click.stop="zoomOut()">
                <Minus />
            </ControlButton>
            <ControlButton @click.stop="fitView()">
                <Fullscreen />
            </ControlButton>
            <ControlButton @click.stop="emit('toggle-orientation', $event)" v-if="toggleOrientationButton">
                <component :is="isHorizontal ? AlignHorizontalCenter : AlignVerticalCenter" />
            </ControlButton>
            <ControlButton @click.stop="showExtraDetails = !showExtraDetails" :class="{'active': showExtraDetails}">
                <InformationSlabCircleOutline />
            </ControlButton>
            <ControlButton @click.stop="toggleDropdown">
                <Download />
            </ControlButton>
            <ControlButton @click.stop="uncollapseAll()" v-if="collapsed.size > 0">
                <ArrowExpandAll />
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
    import Plus from "vue-material-design-icons/Plus.vue"
    import Minus from "vue-material-design-icons/Minus.vue"
    import Fullscreen from "vue-material-design-icons/Fullscreen.vue"
    import AlignHorizontalCenter from "vue-material-design-icons/AlignHorizontalCenter.vue"
    import AlignVerticalCenter from "vue-material-design-icons/AlignVerticalCenter.vue"
    import Download from "vue-material-design-icons/Download.vue"
    import InformationSlabCircleOutline from "vue-material-design-icons/InformationSlabCircleOutline.vue"
    import ArrowExpandAll from "vue-material-design-icons/ArrowExpandAll.vue"
    import {cssVar as cssVariable, State} from "@kestra-io/design-system"
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
    })

    const isRunning = computed(() => State.isRunning(props.execution?.state?.current) === true)

    const dragging = ref(false)
    const showExtraDetails = ref(false)
    const lastPosition = ref<XYPosition | null>()
    const {getNodes, getEdges, getElements, onNodeDrag, onNodeDragStart, onNodeDragStop, fitView, zoomIn, zoomOut, setElements, removeEdges, removeNodes, removeSelectedElements, vueFlowRef} = useVueFlow(props.id)
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

    watch(isRunning, () => {
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
                isRunning.value,
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

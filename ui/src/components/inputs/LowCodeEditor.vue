<script setup>
    // Core
    import {getCurrentInstance, nextTick, onMounted, ref, watch} from "vue";
    import {useStore} from "vuex";
    import {MarkerType, Position, useVueFlow, VueFlow} from "@vue-flow/core";

    // Nodes
    import Cluster from "../graph/nodes/Cluster.vue";
    import Dot from "../graph/nodes/Dot.vue"
    import Task from "../graph/nodes/Task.vue";
    import Trigger from "../graph/nodes/Trigger.vue";
    import Edge from "../graph/nodes/Edge.vue";

    // Topology Control
    import {Controls, ControlButton} from "@vue-flow/controls"
    import SplitCellsHorizontal from "../../assets/icons/SplitCellsHorizontal.vue"
    import SplitCellsVertical from "../../assets/icons/SplitCellsVertical.vue"

    // Utils
    import YamlUtils from "../../utils/yamlUtils";
    import {SECTIONS} from "../../utils/constants";
    import {linkedElements} from "../../utils/vueFlow";
    import {cssVariable} from "../../utils/global";
    import dagre from "dagre";
    import Utils from "../../utils/utils";

    // Vue flow methods to interact with Graph
    const {
        id,
        getNodes,
        removeNodes,
        getEdges,
        removeEdges,
        fitView,
        getElements,
        removeSelectedElements,
        onNodeDragStart,
        onNodeDragStop,
        onNodeDrag
    } = useVueFlow({id: Math.random().toString()});

    // props
    const props = defineProps({
        flowGraph: {
            type: Object,
            required: true
        },
        flowId: {
            type: String,
            required: false,
            default: undefined
        },
        namespace: {
            type: String,
            required: false,
            default: undefined
        },
        execution: {
            type: Object,
            default: undefined
        },
        isReadOnly: {
            type: Boolean,
            default: false
        },
        source: {
            type: String,
            default: undefined
        },
        isAllowedEdit: {
            type: Boolean,
            default: false
        },
        viewType: {
            type: String,
            default: undefined
        }
    })

    const emit = defineEmits(["follow", "on-edit", "loading"])

    // Vue instance variables
    const store = useStore();
    const toast = getCurrentInstance().appContext.config.globalProperties.$toast();
    const t = getCurrentInstance().appContext.config.globalProperties.$t;

    // Init variables functions
    const isHorizontalDefault = () => {
        return props.viewType === "source-topology" ? false :
            (props.viewType?.indexOf("blueprint") !== -1 ? true : localStorage.getItem("topology-orientation") === "1")
    }


    // Components variables
    const dragging = ref(false);
    const isHorizontal = ref(isHorizontalDefault());
    const elements = ref([])
    const lastPosition = ref(null)
    const vueFlow = ref(null);
    const timer = ref(null);

    // Init components
    onMounted( async() => {
        // Regenerate graph on window resize
        observeWidth();
    })

    watch(() => props.flowGraph, () => {
        generateGraph();
    })

    // Event listeners & Watchers
    const observeWidth = () => {
        const resizeObserver = new ResizeObserver(function () {
            clearTimeout(timer.value);
            timer.value = setTimeout(() => {
                generateGraph();
                nextTick(() => {
                    fitView()
                })
            }, 50);
        });
        resizeObserver.observe(vueFlow.value);
    }


    const forwardEvent = (type, event) => {
        emit(type, event);
    };

    const onDelete = (event) => {
        const flowParsed = YamlUtils.parse(props.source);
        toast.confirm(
            t("delete task confirm", {taskId: flowParsed.id}),
            () => {

                const section = event.section ? event.section : SECTIONS.TASKS;
                if (section === SECTIONS.TASKS && flowParsed.tasks.length === 1 && flowParsed.tasks.map(e => e.id).includes(event.id)) {
                    store.dispatch("core/showMessage", {
                        variant: "error",
                        title: t("can not delete"),
                        message: t("can not have less than 1 task")
                    });
                    return;
                }
                emit("on-edit",YamlUtils.deleteTask(props.source, event.id, section))
            },
            () => {
            }
        )
    }

    // Source edit functions
    const onCreateNewTask = (event) => {
        const source = props.source;
        emit("on-edit",YamlUtils.insertTask(source, event.taskId, event.taskYaml, event.insertPosition))
    }

    const onAddFlowableError = (event) => {
        const source = props.source;
        emit("on-edit",YamlUtils.insertErrorInFlowable(source, event.error, event.taskId))
    }

    // Flow check functions
    const flowHaveTasks = (source) => {
        const flow = source ? source : props.source
        return flow ? YamlUtils.flowHaveTasks(flow) : false;
    }

    const flowables = () => {
        return props.flowGraph && props.flowGraph.flowables ? props.flowGraph.flowables : [];
    }

    // Graph interactions functions
    const onMouseOver = (node) => {
        if (!dragging.value) {
            linkedElements(id, node.uid).forEach((n) => {
                if (n.type === "task") {
                    n.style = {...n.style, outline: "0.5px solid " + cssVariable("--bs-yellow")}
                }
            });
        }

    }

    const onMouseLeave = () => {
        resetNodesStyle();
    }

    const resetNodesStyle = () => {
        getNodes.value.filter(n => n.type === "task" || n.type === " trigger")
            .forEach(n => {
                n.style = {...n.style, opacity: "1", outline: "none"}
            })
    }

    onNodeDragStart((e) => {
        dragging.value = true;
        resetNodesStyle();
        e.node.style = {...e.node.style, zIndex: 1976}
        lastPosition.value = e.node.position;
    })

    onNodeDragStop((e) => {
        dragging.value = false;
        if (checkIntersections(e.intersections, e.node) === null) {
            const taskNode1 = e.node;
            // check multiple intersection with task
            const taskNode2 = e.intersections.find(n => n.type === "task");
            if (taskNode2) {
                try {
                    emit("on-edit", YamlUtils.swapTasks(props.source, taskNode1.id, taskNode2.id))
                } catch (e) {
                    store.dispatch("core/showMessage", {
                        variant: "error",
                        title: t("cannot swap tasks"),
                        message: t(e.message, e.messageOptions)
                    });
                    taskNode1.position = lastPosition.value;
                }
            } else {
                taskNode1.position = lastPosition.value;
            }
        } else {
            e.node.position = lastPosition.value;
        }
        resetNodesStyle();
        e.node.style = {...e.node.style, zIndex: 1}
        lastPosition.value = null;
    })

    onNodeDrag((e) => {
        resetNodesStyle();
        getNodes.value.filter(n => n.id !== e.node.id).forEach(n => {
            if (n.type === "trigger" || (n.type === "task" && YamlUtils.isParentChildrenRelation(props.source, n.id, e.node.id))) {
                n.style = {...n.style, opacity: "0.5"}
            } else {
                n.style = {...n.style, opacity: "1"}
            }
        })
        if (!checkIntersections(e.intersections, e.node) && e.intersections.filter(n => n.type === "task").length === 1) {
            e.intersections.forEach(n => {
                if (n.type === "task") {
                    n.style = {...n.style, outline: "0.5px solid " + cssVariable("--bs-primary")}
                }
            })
            e.node.style = {...e.node.style, outline: "0.5px solid " + cssVariable("--bs-primary")}
        }
    })

    const checkIntersections = (intersections, node) => {
        const tasksMeet = intersections.filter(n => n.type === "task").map(n => n.id);
        if (tasksMeet.length > 1) {
            return "toomuchtaskerror";
        }
        if (tasksMeet.length === 1 && YamlUtils.isParentChildrenRelation(props.source, tasksMeet[0], node.id)) {
            return "parentchildrenerror";
        }
        if (intersections.filter(n => n.type === "trigger").length > 0) {
            return "triggererror";
        }
        return null;
    }

    const toggleOrientation = () => {
        localStorage.setItem(
            "topology-orientation",
            localStorage.getItem("topology-orientation") !== "0" ? "0" : "1"
        );
        isHorizontal.value = localStorage.getItem("topology-orientation") === "1";
        generateGraph();
        fitView();
    };

    // Graph generation functions
    const generateDagreGraph = () => {
        const dagreGraph = new dagre.graphlib.Graph({compound: true})
        dagreGraph.setDefaultEdgeLabel(() => ({}))
        dagreGraph.setGraph({rankdir: isHorizontal.value ? "LR" : "TB"})

        for (const node of props.flowGraph.nodes) {
            dagreGraph.setNode(node.uid, {
                width: getNodeWidth(node),
                height: getNodeHeight(node)
            })
        }

        for (const edge of props.flowGraph.edges) {
            dagreGraph.setEdge(edge.source, edge.target)
        }

        for (let cluster of (props.flowGraph.clusters || [])) {
            dagreGraph.setNode(cluster.cluster.uid, {clusterLabelPos: "top"});

            if (cluster.parents) {
                dagreGraph.setParent(cluster.cluster.uid, cluster.parents[cluster.parents.length - 1]);
            }

            for (let node of (cluster.nodes || [])) {
                dagreGraph.setParent(node, cluster.cluster.uid)
            }
        }
        dagre.layout(dagreGraph)
        return dagreGraph;
    }

    const getNodePosition = (n, parent) => {
        const position = {x: n.x - n.width / 2, y: n.y - n.height / 2};

        // bug with parent node,
        if (parent) {
            const parentPosition = getNodePosition(parent);
            position.x = position.x - parentPosition.x;
            position.y = position.y - parentPosition.y;
        }
        return position;
    };

    const isTaskNode = (node) => {
        return node.task !== undefined && (node.type === "io.kestra.core.models.hierarchies.GraphTask" || node.type === "io.kestra.core.models.hierarchies.GraphClusterRoot")
    };

    const isTriggerNode = (node) => {
        return node.trigger !== undefined && (node.type === "io.kestra.core.models.hierarchies.GraphTrigger");
    }

    const getNodeWidth = (node) => {
        return isTaskNode(node) || isTriggerNode(node) ? 202 : 5;
    };

    const getNodeHeight = (node) => {
        return isTaskNode(node) || isTriggerNode(node) ? 55 : (isHorizontal.value ? 55 : 5);
    };

    const complexEdgeHaveAdd = (edge) => {
        // Check if edge is an ending flowable
        // If true, enable add button to add a task
        // under the flowable task
        const isEndtoEndEdge = edge.source.includes("_end") && edge.target.includes("_end")
        if (isEndtoEndEdge) {
            // Cluster uid contains the flowable task id
            // So we look for the cluster having this end edge
            // to return his flowable id
            return [getClusterTaskIdWithEndNodeUid(edge.source), "after"];
        }
        if (isLinkToFirstFlowableTask(edge)) {
            return [getFirstTaskId(), "before"];
        }

        return undefined;
    }

    const getClusterTaskIdWithEndNodeUid = (nodeUid) => {
        const cluster = props.flowGraph.clusters.find(cluster => cluster.end === nodeUid);
        if (cluster) {
            return Utils.splitFirst(cluster.cluster.uid, "cluster_");
        }

        return undefined;
    }

    const isLinkToFirstFlowableTask = (edge) => {
        const firstTaskId = getFirstTaskId();

        return flowables().includes(firstTaskId) && edge.target === firstTaskId;
    }

    const getFirstTaskId = () => {
        return YamlUtils.getFirstTask(props.source);
    }

    const getNextTaskId = (target) => {
        while (YamlUtils.extractTask(props.source, target) === undefined) {
            const edge = props.flowGraph.edges.find(e => e.source === target)
            if (!edge) {
                return null
            }
            target = edge.target
        }
        return target
    }

    const cleanGraph = () => {
        removeEdges(getEdges.value)
        removeNodes(getNodes.value)
        removeSelectedElements(getElements.value)
        elements.value = []
    }

    const generateGraph = () => {
        cleanGraph();

        nextTick(() => {
            emit("loading", true);
            try {
                if (!props.flowGraph || !flowHaveTasks()) {
                    elements.value.push({
                        id: "start",
                        label: "",
                        type: "dot",
                        position: {x: 0, y: 0},
                        style: {
                            width: "5px",
                            height: "5px"
                        },
                        sourcePosition: isHorizontal.value ? Position.Right : Position.Bottom,
                        targetPosition: isHorizontal.value ? Position.Left : Position.Top,
                        parentNode: undefined,
                        draggable: false,
                    })
                    elements.value.push({
                        id: "end",
                        label: "",
                        type: "dot",
                        position: isHorizontal.value ? {x: 50, y: 0} : {x: 0, y: 50},
                        style: {
                            width: "5px",
                            height: "5px"
                        },
                        sourcePosition: isHorizontal.value ? Position.Right : Position.Bottom,
                        targetPosition: isHorizontal.value ? Position.Left : Position.Top,
                        parentNode: undefined,
                        draggable: false,
                    })
                    elements.value.push({
                        id: "start|end",
                        source: "start",
                        target: "end",
                        type: "edge",
                        markerEnd: MarkerType.ArrowClosed,
                        data: {
                            edge: {
                                relation: {
                                    relationType: "SEQUENTIAL"
                                }
                            },
                            isFlowable: false,
                            initTask: true,
                        }
                    })

                    emit("loading", false);
                    return;
                }
                if (props.flowGraph === undefined) {
                    emit("loading", false);
                    return;
                }
                const dagreGraph = generateDagreGraph();
                const clusters = {};
                for (let cluster of (props.flowGraph.clusters || [])) {
                    for (let nodeUid of cluster.nodes) {
                        clusters[nodeUid] = cluster.cluster;
                    }

                    const dagreNode = dagreGraph.node(cluster.cluster.uid)
                    const parentNode = cluster.parents ? cluster.parents[cluster.parents.length - 1] : undefined;

                    const clusterUid = cluster.cluster.uid;
                    elements.value.push({
                        id: clusterUid,
                        label: clusterUid,
                        type: "cluster",
                        parentNode: parentNode,
                        position: getNodePosition(dagreNode, parentNode ? dagreGraph.node(parentNode) : undefined),
                        style: {
                            width: clusterUid === "Triggers" && isHorizontal.value ? "400px" : dagreNode.width + "px",
                            height: clusterUid === "Triggers" && !isHorizontal.value ? "250px" : dagreNode.height + "px",
                        },
                    })
                }

                let disabledLowCode = [];

                for (const node of props.flowGraph.nodes) {
                    const dagreNode = dagreGraph.node(node.uid);
                    let nodeType = "task";
                    if (node.type.includes("GraphClusterEnd")) {
                        nodeType = "dot";
                    } else if (clusters[node.uid] === undefined && node.type.includes("GraphClusterRoot")) {
                        nodeType = "dot";
                    } else if (node.type.includes("GraphClusterRoot")) {
                        nodeType = "dot";
                    } else if (node.type.includes("GraphTrigger")) {
                        nodeType = "trigger";
                    }
                    // Disable interaction for Dag task
                    // because our low code editor can not handle it for now
                    if (isTaskNode(node) && node.task.type === "io.kestra.core.tasks.flows.Dag") {
                        disabledLowCode.push(node.task.id);
                        YamlUtils.getChildrenTasks(props.source, node.task.id).forEach(child => {
                            disabledLowCode.push(child);
                        })
                    }

                    elements.value.push({
                        id: node.uid,
                        label: isTaskNode(node) ? node.task.id : "",
                        type: nodeType,
                        position: getNodePosition(dagreNode, clusters[node.uid] ? dagreGraph.node(clusters[node.uid].uid) : undefined),
                        style: {
                            width: getNodeWidth(node) + "px",
                            height: getNodeHeight(node) + "px"
                        },
                        sourcePosition: isHorizontal.value ? Position.Right : Position.Bottom,
                        targetPosition: isHorizontal.value ? Position.Left : Position.Top,
                        parentNode: clusters[node.uid] ? clusters[node.uid].uid : undefined,
                        draggable: nodeType === "task" && !props.isReadOnly && isTaskNode(node) ? !disabledLowCode.includes(node.task.id) : false,
                        data: {
                            node: node,
                            namespace: props.namespace,
                            flowId: props.flowId,
                            revision: props.execution ? props.execution.flowRevision : undefined,
                            isFlowable: isTaskNode(node) ? flowables().includes(node.task.id) : false
                        },
                    })
                }

                for (const edge of props.flowGraph.edges) {
                    elements.value.push({
                        id: edge.source + "|" + edge.target,
                        source: edge.source,
                        target: edge.target,
                        type: "edge",
                        markerEnd: MarkerType.ArrowClosed,
                        data: {
                            edge: edge,
                            haveAdd: complexEdgeHaveAdd(edge),
                            isFlowable: flowables().includes(edge.source) || flowables().includes(edge.target),
                            nextTaskId: getNextTaskId(edge.target),
                            disabled: disabledLowCode.includes(edge.source)
                        }
                    })
                }
            } catch (e) {
                console.error("Error while creating topology graph: " + e);
            }
            finally {
                emit("loading", false);
            }
        })
    }

    // Expose method to be triggered by parents
    defineExpose({
        generateGraph
    })
</script>

<template>
    <div ref="vueFlow" class="vueflow">
        <slot name="top-bar"/>
        <VueFlow
            v-model="elements"
            :default-marker-color="cssVariable('--bs-cyan')"
            :fit-view-on-init="true"
            :nodes-draggable="false"
            :nodes-connectable="false"
            :elevate-nodes-on-select="false"
            :elevate-edges-on-select="false"
        >
            <template #node-cluster="props">
                <Cluster v-bind="props" />
            </template>

            <template #node-dot="props">
                <Dot v-bind="props" />
            </template>

            <template #node-task="props">
                <Task
                    v-bind="props"
                    @follow="forwardEvent('follow', $event)"
                    @edit="forwardEvent('on-edit', $event)"
                    @delete="onDelete"
                    @addFlowableError="onAddFlowableError"
                    @mouseover="onMouseOver"
                    @mouseleave="onMouseLeave"
                    :is-read-only="isReadOnly"
                    :is-allowed-edit="isAllowedEdit"
                />
            </template>

            <template #node-trigger="props">
                <Trigger
                    v-bind="props"
                    @edit="forwardEvent('on-edit', $event)"
                    @delete="onDelete"
                    :is-read-only="isReadOnly"
                    :is-allowed-edit="isAllowedEdit"
                />
            </template>

            <template #edge-edge="props">
                <Edge
                    v-bind="props"
                    :yaml-source="source"
                    :flowables-ids="flowables()"
                    @edit="onCreateNewTask"
                    :is-read-only="isReadOnly"
                    :is-allowed-edit="isAllowedEdit"
                />
            </template>

            <Controls :show-interactive="false">
                <ControlButton @click="toggleOrientation" v-if="['topology'].includes(viewType)">
                    <SplitCellsVertical :size="48" v-if="!isHorizontal" />
                    <SplitCellsHorizontal v-if="isHorizontal" />
                </ControlButton>
            </Controls>
        </VueFlow>
    </div>
</template>



<style scoped lang="scss">
    .vueflow {
        height: 100%;
        width: 100%;
        position: relative;
    }
</style>
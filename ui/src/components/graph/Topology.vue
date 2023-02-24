<script setup>
    import {ref, onMounted, nextTick, watch, getCurrentInstance} from "vue";
    import {useStore} from 'vuex'
    import {VueFlow, useVueFlow, Position, MarkerType} from "@vue-flow/core"
    import {Controls, ControlButton} from "@vue-flow/controls"
    import dagre from "dagre"
    import ArrowCollapseRight from "vue-material-design-icons/ArrowCollapseRight.vue";
    import ArrowCollapseDown from "vue-material-design-icons/ArrowCollapseDown.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";

    import BottomLine from "../../components/layout/BottomLine.vue";
    import TriggerFlow from "../../components/flows/TriggerFlow.vue";
    import {cssVariable} from "../../utils/global"
    import Cluster from "./nodes/Cluster.vue";
    import Dot from "./nodes/Dot.vue"
    import Task from "./nodes/Task.vue";
    import Trigger from "./nodes/Trigger.vue";
    import Edge from "./nodes/Edge.vue";
    import {linkedElements} from "../../utils/vueFlow";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import {saveFlowTemplate} from "../../utils/flowTemplate";

    const {id, getNodes, removeNodes, getEdges, removeEdges, fitView, addSelectedElements, removeSelectedNodes, removeSelectedEdges} = useVueFlow()
    const store = useStore();
    const emit = defineEmits(["follow"])
    const user = store.getters['user/user'];
    const flow = store.getters['flow/flow'];
    const toast = getCurrentInstance().appContext.config.globalProperties.$toast();

    const props = defineProps({
        flowGraph: {
            type: Object,
            required: true
        },
        flowId: {
            type: String,
            required: true
        },
        namespace: {
            type: String,
            required: true
        },
        execution: {
            type: Object,
            default: undefined
        }
    })

    const isHorizontal = ref(localStorage.getItem("topology-orientation") !== "0");
    const isLoading = ref(false);
    const elements = ref([])
    const haveChange = ref(false)
    const flowYaml = ref({})

    const generateDagreGraph = () => {
        const dagreGraph = new dagre.graphlib.Graph({compound:true})
        dagreGraph.setDefaultEdgeLabel(() => ({}))
        dagreGraph.setGraph({rankdir: isHorizontal.value ? "LR" : "TB"})

        for (const node of props.flowGraph.nodes) {
            dagreGraph.setNode(node.uid, {
                width: getNodeWidth(node) ,
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

    const regenerateGraph = () => {
        removeEdges(getEdges.value)
        removeNodes(getNodes.value)

        nextTick(() => {
            generateGraph();
        })
    }

    const toggleOrientation = () => {
        localStorage.setItem(
            "topology-orientation",
            localStorage.getItem("topology-orientation") !== "0" ? "0" : "1"
        );
        isHorizontal.value = localStorage.getItem("topology-orientation") === "1";
        regenerateGraph();
    };

    const generateGraph = () => {
        isLoading.value = true;
        const dagreGraph = generateDagreGraph();

        const clusters = {};

        for (let cluster of (props.flowGraph.clusters || [])) {
            for (let nodeUid of cluster.nodes) {
                clusters[nodeUid] = cluster.cluster;
            }

            const dagreNode = dagreGraph.node(cluster.cluster.uid)
            const parentNode = cluster.parents ? cluster.parents[cluster.parents.length - 1] : undefined;

            elements.value.push({
                id: cluster.cluster.uid,
                label: cluster.cluster.uid,
                type: "cluster",
                parentNode: parentNode,
                position: getNodePosition(dagreNode, parentNode ? dagreGraph.node(parentNode) : undefined),
                style: {
                    width: dagreNode.width + "px",
                    height: dagreNode.height + "px",
                },
            })
        }

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
                data: {
                    node: node,
                    namespace: props.namespace,
                    flowId: props.flowId,
                    revision: props.execution ? props.execution.flowRevision : undefined,
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
                    edge: edge
                }
            })
        }
        fitView();
        isLoading.value = false;
    }

    onMounted(() => {
        generateGraph();
    })

    watch(() => props.flowGraph, async () => {
        regenerateGraph()
    });


    const isAllowedEdit = () => {
        return user.isAllowed(permission.FLOW, action.UPDATE, flow.namespace);
    };

    const onMouseOver = (node) => {
        addSelectedElements(linkedElements(id, node.uid));
    }

    const onMouseLeave = () => {
        removeSelectedNodes(getNodes.value);
        removeSelectedEdges(getEdges.value);
    }

    const forwardEvent = (type, event) => {
        emit(type, event);
    };

    const onEdit = (event) => {
        store.dispatch("flow/loadGraphFromSource", {flow: event})
            .then(value => {
                flowYaml.value = event;
                haveChange.value = true;
                store.dispatch("core/isUnsaved", true);
            })
    }

    const save = () => {
        store
            .dispatch(`flow/saveFlow`, {flow: flowYaml.value})
            .then((response) => {
                toast.saved(response.id);
                store.dispatch("core/isUnsaved", false);
            })
    };

</script>

<template>
    <el-card shadow="never" v-loading="isLoading">
        <VueFlow
            v-model="elements"
            :default-marker-color="cssVariable('--bs-cyan')"
            :fit-view-on-init="true"
            :nodes-connectable="true"
            :nodes-draggable="false"
            :elevate-nodes-on-select="true"
            :elevate-edges-on-select="true"
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
                    @edit="onEdit"
                    @mouseover="onMouseOver"
                    @mouseleave="onMouseLeave"
                />
            </template>

            <template #node-trigger="props">
                <Trigger
                    v-bind="props"
                    @edit="onEdit"
                    @mouseover="onMouseOver"
                    @mouseleave="onMouseLeave"
                />
            </template>

            <template #edge-edge="props">
                <Edge v-bind="props" />
            </template>

            <Controls :show-interactive="false">
                <ControlButton @click="toggleOrientation">
                    <ArrowCollapseDown v-if="!isHorizontal" />
                    <ArrowCollapseRight v-if="isHorizontal" />
                </ControlButton>
            </Controls>
        </VueFlow>
    </el-card>
    <bottom-line>
        <ul>
            <li>
                <trigger-flow type="default" :disabled="flow.disabled" :flow-id="flow.id" :namespace="flow.namespace" />
            </li>
            <li>
                <el-button :icon="ContentSave" size="large" @click="save" v-if="isAllowedEdit" :disabled="!haveChange" type="info">
                    {{ $t('save') }}
                </el-button>
            </li>
        </ul>
    </bottom-line>
</template>

<style lang="scss" scoped>
    .el-card {
        height: calc(100vh - 300px);
        :deep(.el-card__body) {
            height: 100%;
        }
    }
</style>
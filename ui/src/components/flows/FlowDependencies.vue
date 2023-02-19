<script setup>
    import {ref, onMounted, inject, nextTick} from "vue";
    import {useRoute} from "vue-router";
    import {VueFlow, useVueFlow, Position, MarkerType} from "@vue-flow/core"
    import {Controls, ControlButton} from "@vue-flow/controls"
    import dagre from "dagre"
    import ArrowExpandAll from "vue-material-design-icons/ArrowExpandAll.vue";

    import {cssVariable} from "../../utils/global"
    import FlowDependenciesBlock from "./FlowDependenciesBlock.vue";

    import {linkedElements} from "../../utils/vueFlow"

    const {id, addNodes, addEdges, getNodes, removeNodes, getEdges, removeEdges, fitView, addSelectedElements, removeSelectedNodes, removeSelectedEdges} = useVueFlow();

    const route = useRoute();
    const axios = inject("axios")

    const loaded = ref([]);
    const dependencies = ref({
        nodes: [],
        edges: []
    });

    const isLoading = ref(false);

    const load = (options) => {
        isLoading.value = true;
        return axios
            .get(`/api/v1/flows/${options.namespace}/${options.id}/dependencies`)
            .then(response => {
                loaded.value.push(`${options.namespace}_${options.id}`)

                if (Object.keys(response.data).length > 0) {
                    dependencies.value.nodes.push(...response.data.nodes)
                    dependencies.value.edges.push(...response.data.edges)
                }

                removeEdges(getEdges.value)
                removeNodes(getNodes.value)

                nextTick(() => {
                    generateGraph();
                })
            })
    };

    const expandAll =() =>  {
        for (const node of dependencies.value.nodes) {
            if (loaded.value.indexOf(node.uid) < 0) {
                load({namespace: node.namespace, id: node.id});
            }
        }
    };

    const expand = (data) => {
        load({namespace: data.namespace, id: data.id})
    };

    const generateDagreGraph = () => {
        const dagreGraph = new dagre.graphlib.Graph()
        dagreGraph.setDefaultEdgeLabel(() => ({}))
        dagreGraph.setGraph({rankdir: "LR"})

        for (const node of dependencies.value.nodes) {
            dagreGraph.setNode(node.uid, {
                width: 250 ,
                height: 62
            })
        }

        for (const edge of dependencies.value.edges) {
            dagreGraph.setEdge(edge.source, edge.target)
        }

        dagre.layout(dagreGraph)

        return dagreGraph;
    }

    const getNodePosition = (n) => {
        return {x: n.x - n.width / 2, y: n.y - n.height / 2};
    };

    const generateGraph = () => {
        const dagreGraph = generateDagreGraph();

        for (const node of dependencies.value.nodes) {
            const dagreNode = dagreGraph.node(node.uid);

            addNodes([{
                id: node.uid,
                type: "flow",
                position: getNodePosition(dagreNode),
                style: {
                    width: "250px",
                    height: "62px",
                },
                sourcePosition: Position.Right,
                targetPosition: Position.Left,
                data: {
                    node: node,
                    loaded: loaded.value.indexOf(node.uid) >= 0,
                    namespace: node.namespace,
                    flowId: node.id,
                    current: node.namespace === route.params.namespace && node.id === route.params.id,
                }
            }]);
        }

        for (const edge of dependencies.value.edges) {
            addEdges([{
                id: edge.source + "|" + edge.target,
                source: edge.source,
                target: edge.target,
                markerEnd: MarkerType.ArrowClosed,
                type: "smoothstep"
            }]);
        }

        fitView();
        isLoading.value = false;
    };

    onMounted(() => {
        load(route.params)
    })

    const onMouseOver = (node) => {
        addSelectedElements(linkedElements(id, node.uid));
    }

    const onMouseLeave = () => {
        removeSelectedNodes(getNodes.value);
        removeSelectedEdges(getEdges.value);
    }
</script>

<template>
    <el-card shadow="never" v-loading="isLoading">
        <VueFlow
            :default-marker-color="cssVariable('--bs-cyan')"
            :fit-view-on-init="true"
            :nodes-connectable="false"
            :nodes-draggable="false"
            :elevate-nodes-on-select="false"
        >
            <template #node-flow="props">
                <FlowDependenciesBlock
                    :node="props.data.node"
                    :loaded="props.data.loaded"
                    @expand="expand"
                    @mouseover="onMouseOver"
                    @mouseleave="onMouseLeave"
                />
            </template>

            <Controls :show-interactive="false">
                <ControlButton>
                    <el-tooltip :content="$t('expand dependencies')" :persistent="false" transition="" :hide-after="0">
                        <el-button :icon="ArrowExpandAll" size="small" @click="expandAll" />
                    </el-tooltip>
                </ControlButton>
            </Controls>
        </VueFlow>
    </el-card>
</template>

<style lang="scss" scoped>
    .el-card {
        height: calc(100vh - 360px);
        :deep(.el-card__body) {
            height: 100%;
        }
    }
</style>
<template>
    <el-card shadow="never" v-loading="isLoading">
        <el-alert v-if="!hasNodes" type="info" :closable="false">
            {{ $t('no result') }}
        </el-alert>
        <VueFlow
            v-else
            :default-marker-color="cssVariable('--bs-cyan')"
            :fit-view-on-init="true"
            :nodes-connectable="false"
            :nodes-draggable="false"
            :elevate-nodes-on-select="false"
        >
            <Background />
            <template #node-flow="nodeProps">
                <DependenciesNode
                    v-bind="nodeProps"
                    @expand-dependencies="expand"
                    @mouseover="onMouseOver"
                    @mouseleave="onMouseLeave"
                    @open-link="openFlow($route.params.tenant, $event)"
                />
            </template>

            <Controls :show-interactive="false">
                <ControlButton>
                    <el-tooltip :content="$t('expand dependencies')" :persistent="false" transition="" :hide-after="0" effect="light">
                        <el-button :icon="ArrowExpandAll" size="small" @click="expandAll" />
                    </el-tooltip>
                </ControlButton>
            </Controls>
        </VueFlow>
    </el-card>
</template>

<script setup>
    import {ref, onMounted, inject, nextTick} from "vue";
    import {useStore} from "vuex";
    import {useRouter} from "vue-router"
    import {VueFlow, useVueFlow, Position, MarkerType} from "@vue-flow/core"
    import {Controls, ControlButton} from "@vue-flow/controls"
    import {Background} from "@vue-flow/background";
    import dagre from "dagre"
    import ArrowExpandAll from "vue-material-design-icons/ArrowExpandAll.vue";

    import {cssVariable} from "@kestra-io/ui-libs";
    import {DependenciesNode} from "@kestra-io/ui-libs"

    import {linkedElements} from "../../utils/vueFlow"
    import {apiUrl} from "override/utils/route";


    const {id: vueFlowId, addNodes, addEdges, getNodes, removeNodes, getEdges, removeEdges, fitView, addSelectedElements, removeSelectedNodes, removeSelectedEdges} = useVueFlow();

    const axios = inject("axios")
    const router = useRouter();
    const store = useStore();

    const loaded = ref([]);
    const dependencies = ref({
        nodes: [],
        edges: []
    });

    const isLoading = ref(false);
    const hasNodes = ref(false);

    const props = defineProps({
        namespace: {
            type: String,
            required: true
        }
    });

    const load = () => {
        isLoading.value = true;
        return axios
            .get(`${apiUrl(store)}/namespaces/${props.namespace}/dependencies`)
            .then(response => {
                loaded.value.push(`${props.namespace}`)

                if (Object.keys(response.data).length > 0) {
                    dependencies.value.nodes.push(...response.data.nodes)
                    if (response.data.edges) {
                        dependencies.value.edges.push(...response.data.edges)
                    }
                }

                if (dependencies?.value?.nodes?.length > 0) {
                    hasNodes.value = true;
                }

                removeEdges(getEdges.value)
                removeNodes(getNodes.value)

                nextTick(() => {
                    generateGraph();
                })
            })
    };

    const loadOutsideDependencies = (options) => {
        isLoading.value = true;
        return axios
            .get(`${apiUrl(store)}/flows/${options.namespace}/${options.id}/dependencies`)
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
                loadOutsideDependencies({namespace: node.namespace, id: node.id});
            }
        }
    };

    const expand = (data) => {
        loadOutsideDependencies({namespace: data.namespace, id: data.flowId})
    };

    const generateDagreGraph = () => {
        const dagreGraph = new dagre.graphlib.Graph()
        dagreGraph.setDefaultEdgeLabel(() => ({}))
        dagreGraph.setGraph({rankdir: "LR"})

        for (const node of dependencies.value.nodes) {
            dagreGraph.setNode(node.uid, {
                width: 184 ,
                height: 44
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
                    width: "184px",
                    height: "44px",
                },
                sourcePosition: Position.Right,
                targetPosition: Position.Left,
                data: {
                    node: node,
                    loaded: loaded.value.indexOf(node.uid) >= 0,
                    namespace: node.namespace,
                    flowId: node.id,
                    current: node.id === props.namespace,
                    color: "pink",
                    link: true
                }
            }]);
        }

        for (const edge of dependencies.value.edges) {
            addEdges([{
                id: edge.source + "|" + edge.target,
                source: edge.source,
                target: edge.target,
                markerEnd: {
                    id: "marker-custom",
                    type: MarkerType.ArrowClosed,
                },
                type: "smoothstep"
            }]);
        }

        fitView();
        isLoading.value = false;
    };

    onMounted(() => {
        if (props.namespace !== undefined) {
            load();
        }
    })

    const onMouseOver = (node) => {
        addSelectedElements(linkedElements(vueFlowId, node.uid));
    }

    const onMouseLeave = () => {
        removeSelectedNodes(getNodes.value);
        removeSelectedEdges(getEdges.value);
    }

    const openFlow = (tenant, data) => {
        router.push({
            name: "flows/update",
            params: {
                "namespace": data.namespace,
                "id": data.flowId,
                tab: "dependencies",
                tenant: tenant
            },
        });
    }
</script>

<style lang="scss" scoped>
    .el-card {
        height: calc(100vh - 174px);
        :deep(.el-card__body) {
            height: 100%;
        }
    }
</style>
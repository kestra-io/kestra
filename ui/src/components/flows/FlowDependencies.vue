<template>
    <el-card shadow="never" v-loading="isLoading">
        <VueFlow
            :default-marker-color="cssVariable('--bs-cyan')"
            :fit-view-on-init="true"
            :nodes-connectable="false"
            :nodes-draggable="false"
            :elevate-nodes-on-select="false"
        >
            <Background />
            <template #node-flow="props">
                <DependenciesNode
                    v-bind="props"
                    @expand-dependencies="expand"
                    @mouseover="onMouseOver"
                    @mouseleave="onMouseLeave"
                    @open-link="openFlow"
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
    import {ref, onMounted, inject, nextTick, getCurrentInstance} from "vue";
    import {useRoute, useRouter} from "vue-router";
    import {VueFlow, useVueFlow, Position, MarkerType} from "@vue-flow/core"
    import {Controls, ControlButton} from "@vue-flow/controls"
    import {Background} from "@vue-flow/background";
    import dagre from "dagre"
    import ArrowExpandAll from "vue-material-design-icons/ArrowExpandAll.vue";

    import {cssVariable} from "@kestra-io/ui-libs";
    import {DependenciesNode} from "@kestra-io/ui-libs"

    import {linkedElements} from "../../utils/vueFlow"
    import {useStore} from "vuex";
    import {apiUrl} from "override/utils/route";

    const {id, addNodes, addEdges, getNodes, removeNodes, getEdges, removeEdges, fitView, addSelectedElements, removeSelectedNodes, removeSelectedEdges} = useVueFlow();

    const route = useRoute();
    const store = useStore();
    const axios = inject("axios")
    const router = useRouter();
    const t = getCurrentInstance().appContext.config.globalProperties.$t;

    const loaded = ref([]);
    const dependencies = ref({
        nodes: [],
        edges: []
    });
    const expanded = ref([]);

    const isLoading = ref(false);
    const initialLoad = ref(true);

    const load = (options) => {
        isLoading.value = true;
        return axios
            .get(`${apiUrl(store)}/flows/${options.namespace}/${options.id}/dependencies`)
            .then(response => {
                loaded.value.push(`${options.namespace}_${options.id}`)

                if (Object.keys(response.data).length > 0) {
                    dependencies.value.nodes.push(...response.data.nodes)
                    dependencies.value.edges.push(...response.data.edges)
                }

                if (!initialLoad.value) {
                    let newNodes = new Set(response.data.nodes.map(n => n.uid))
                    let oldNodes = new Set(getNodes.value.map(n => n.id))
                    store.dispatch("core/showMessage", {
                        variant: "success",
                        title: t("dependencies loaded"),
                        message: t("loaded x dependencies", [...newNodes].filter(node => !oldNodes.has(node)).length),
                    })
                }

                removeEdges(getEdges.value)
                removeNodes(getNodes.value)
                initialLoad.value = false

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
        expanded.value.push(data.node.uid)
        load({namespace: data.namespace, id: data.flowId})
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
                    current: node.namespace === route.params.namespace && node.id === route.params.id,
                    color: "pink",
                    link: true,
                    expandEnabled: !expanded.value.includes(node.uid)
                }
            }]);
        }

        for (const edge of dependencies.value.edges) {
            // TODO: https://github.com/kestra-io/kestra/issues/5350
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
        load(route.params)
    })

    const onMouseOver = (node) => {
        addSelectedElements(linkedElements(id, node.uid));
    }

    const onMouseLeave = () => {
        removeSelectedNodes(getNodes.value);
        removeSelectedEdges(getEdges.value);
    }

    const openFlow = (data) => {
        router.push({
            name: "flows/update",
            params: {
                "namespace": data.namespace,
                "id": data.flowId,
                tenant: route.params.tenant
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
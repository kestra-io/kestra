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
                <BasicNode
                    v-bind="props"
                    :title="props.data.flowId"
                    :state="props.data.state"
                    @expand-dependencies="expand"
                    @mouseover="onMouseOver"
                    @mouseleave="onMouseLeave"
                    @open-link="openFlow"
                />
            </template>

            <Panel position="top-left">
                <el-switch
                    v-model="expandAll"
                    :disabled="expandAll"
                    :active-text="t('expand all')"
                    @change="load(route.params)"
                />
            </Panel>

            <Controls :show-interactive="false" />
        </VueFlow>
    </el-card>
</template>

<script setup>
    import {ref, onMounted, inject, nextTick, onBeforeUnmount, watch} from "vue";
    import {useRoute, useRouter} from "vue-router";
    import {
        VueFlow,
        Panel,
        useVueFlow,
        Position,
        MarkerType,
    } from "@vue-flow/core";
    import {Controls} from "@vue-flow/controls";
    import {Background} from "@vue-flow/background";
    import dagre from "dagre";

    import {cssVariable} from "@kestra-io/ui-libs";
    import BasicNode from "@kestra-io/ui-libs/src/components/nodes/BasicNode.vue";
    
    import {apiUrl} from "override/utils/route";

    import {linkedElements} from "../../utils/vueFlow";
    import {useCoreStore} from "../../stores/core";
    import {useExecutionsStore} from "../../stores/executions";
    
    import {useStore} from "vuex";
    const store = useStore();
    
    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});
    
    const {
        id,
        addNodes,
        addEdges,
        getNodes,
        updateNode,
        removeNodes,
        getEdges,
        removeEdges,
        fitView,
        addSelectedElements,
        removeSelectedNodes,
        removeSelectedEdges,
    } = useVueFlow();

    const route = useRoute();
    const coreStore = useCoreStore();
    const executionsStore = useExecutionsStore();
    const axios = inject("axios");
    const router = useRouter();

    const loaded = ref([]);
    const dependencies = ref({
        nodes: [],
        edges: [],
    });
    const expanded = ref([]);

    const isLoading = ref(false);
    const initialLoad = ref(true);

    const stateColor = (state) => {
        switch (state) {
        case "RUNNING":
            return "primary";
        case "SUCCESS":
            return "success";
        case "WARNING":
            return "warning";
        case "FAILED":
            return "danger";
        default:
            return "yellow";
        }
    };

    let sse = ref();
    const messages = ref([]);
    watch(
        messages,
        (newMessages) => {
            if (newMessages.length <= 0) return;

            newMessages.forEach((message) => {
                const currentNode = getNodes.value.find(
                    (n) =>
                        n.data.flowId === message.flowId &&
                        n.data.namespace === message.namespace,
                );

                if (!currentNode) return;

                updateNode(currentNode.id, {
                    ...currentNode,
                    data: {
                        ...currentNode.data,
                        state: message.state.current,
                        color: stateColor(message.state.current),
                        link: {
                            executionId: message.executionId,
                            namespace: message.namespace,
                            flowId: message.flowId,
                        },
                    },
                });
            });
        },
        {deep: true},
    );
    const openSSE = () => {
        closeSSE();

        executionsStore.followExecutionDependencies({id: route.params.id, expandAll: expandAll.value})
            .then((response) => {
                sse.value = response;

                sse.value.onmessage = (executionEvent) => {
                    const isEnd = executionEvent && executionEvent.lastEventId === "end-all";
                    if (isEnd) closeSSE();

                    const message = JSON.parse(executionEvent.data);

                    if (!message.state) return;

                    messages.value.push(message);
                };

                sse.value.onerror = () => {
                    coreStore.message = {
                        variant: "error",
                        title: t("error"),
                        message: t("something_went_wrong.loading_execution"),
                    };
                };
            });
    };
    const closeSSE = () => {
        if (!sse.value) return;

        sse.value.close();
        sse.value = undefined;
    };

    const expandAll = ref(false);
    const load = (options) => {
        isLoading.value = true;
        return axios
            .get(
                `${apiUrl(store)}/flows/${options.namespace}/${options.flowId}/dependencies${expandAll.value ? "?expandAll=true" : ""}`,
            )
            .then((response) => {
                loaded.value.push(`${options.namespace}_${options.flowId}`);

                if (Object.keys(response.data).length > 0) {
                    dependencies.value.nodes.push(...response.data.nodes);
                    dependencies.value.edges.push(...response.data.edges);
                }

                if (!initialLoad.value) {
                    let newNodes = new Set(response.data.nodes.map((n) => n.uid));
                    let oldNodes = new Set(getNodes.value.map((n) => n.id));

                    const loadedCount = [...newNodes].filter(
                        (node) => !oldNodes.has(node),
                    ).length;

                    if (loadedCount > 0) {
                        coreStore.message = {
                            variant: "success",
                            title: t("dependencies loaded"),
                            message: t("loaded x dependencies", loadedCount),
                        };
                    }
                }

                removeEdges(getEdges.value);
                removeNodes(getNodes.value);
                initialLoad.value = false;

                nextTick(() => {
                    generateGraph();
                    openSSE();
                });
            });
    };

    const expand = (data) => {
        expanded.value.push(data.node.uid);
        load({namespace: data.namespace, id: data.flowId});
    };

    const generateDagreGraph = () => {
        const dagreGraph = new dagre.graphlib.Graph();
        dagreGraph.setDefaultEdgeLabel(() => ({}));
        dagreGraph.setGraph({rankdir: "LR"});

        for (const node of dependencies.value.nodes) {
            dagreGraph.setNode(node.uid, {
                width: 184,
                height: 44,
            });
        }

        for (const edge of dependencies.value.edges) {
            dagreGraph.setEdge(edge.source, edge.target);
        }

        dagre.layout(dagreGraph);

        return dagreGraph;
    };

    const getNodePosition = (n) => {
        return {x: n.x - n.width / 2, y: n.y - n.height / 2};
    };

    const generateGraph = () => {
        const dagreGraph = generateDagreGraph();

        for (const node of dependencies.value.nodes) {
            const dagreNode = dagreGraph.node(node.uid);

            addNodes([
                {
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
                        current:
                            node.namespace === route.params.namespace &&
                            node.id === route.params.flowId,
                        link: true,
                        expandEnabled: !expanded.value.includes(node.uid),
                    },
                },
            ]);
        }

        for (const edge of dependencies.value.edges) {
            // TODO: https://github.com/kestra-io/kestra/issues/5350
            addEdges([
                {
                    id: edge.source + "|" + edge.target,
                    source: edge.source,
                    target: edge.target,
                    markerEnd: {
                        id: "marker-custom",
                        type: MarkerType.ArrowClosed,
                    },
                    type: "smoothstep",
                },
            ]);
        }

        fitView();
        isLoading.value = false;
    };

    onMounted(() => {
        load(route.params);
    });

    onBeforeUnmount(() => {
        closeSSE();
    });

    const onMouseOver = (node) => {
        addSelectedElements(linkedElements(id, node.uid));
    };

    const onMouseLeave = () => {
        removeSelectedNodes(getNodes.value);
        removeSelectedEdges(getEdges.value);
    };

    const openFlow = (data) => {
        router.push({
            name: "flows/update",
            params: {
                namespace: data.link.namespace,
                id: data.link.flowId,
                tenant: route.params.tenant,
            },
        });
    };
</script>

<style lang="scss" scoped>
.el-card {
    height: calc(100vh - 174px);
    :deep(.el-card__body) {
        height: 100%;
    }
}
</style>

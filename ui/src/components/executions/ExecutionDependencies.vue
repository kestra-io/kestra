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
                    :icon-component="iconVNode"
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
    import {ref, onMounted, inject, nextTick, onBeforeUnmount, watch, h, computed} from "vue";
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

    import TaskIcon from "@kestra-io/ui-libs/src/components/misc/TaskIcon.vue";
    const icon = computed(() => {     
        const GRAY = "#2f3342";
        
        return window.btoa(`
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="25" viewBox="0 0 24 25" fill="none">
            <path fill-rule="evenodd" clip-rule="evenodd" 
                d="M4.34546 9.63757C4.74074 10.5277 5.31782 11.3221 6.03835 11.9681L7.03434 10.8209C6.4739 10.3185 6.02504 9.70059 5.71758 9.00824C5.41012 8.3159 5.25111 7.56496 5.25111 6.80532C5.25111 6.04568 5.41012 5.29475 5.71758 4.6024C6.02504 3.91006 6.4739 3.29216 7.03434 2.78977L6.03835 1.64258C5.31782 2.28851 4.74074 3.08293 4.34546 3.97307C3.95019 4.86321 3.74575 5.82867 3.74575 6.80532C3.74575 7.78197 3.95019 8.74744 4.34546 9.63757ZM16.955 4.38931C17.4802 3.97411 18.1261 3.74777 18.7913 3.74576C19.5894 3.74576 20.3547 4.06807 20.919 4.64177C21.4833 5.21548 21.8004 5.9936 21.8004 6.80494C21.8004 7.61628 21.4833 8.3944 20.919 8.96811C20.3547 9.54181 19.5894 9.86412 18.7913 9.86412C18.2559 9.86126 17.7312 9.71144 17.2725 9.43048L12.3325 14.4529L11.2688 13.3715L16.2088 8.34906C16.0668 8.10583 15.9592 7.84348 15.8891 7.56973H11.2688V6.04014H15.8891C16.055 5.38511 16.4298 4.80451 16.955 4.38931ZM17.9555 8.07674C18.2029 8.24482 18.4938 8.33453 18.7913 8.33453C19.1902 8.33412 19.5727 8.17284 19.8548 7.88607C20.1368 7.59931 20.2955 7.21049 20.2959 6.80494C20.2959 6.50241 20.2076 6.20668 20.0423 5.95514C19.877 5.70361 19.642 5.50756 19.3671 5.39178C19.0922 5.27601 18.7897 5.24572 18.4978 5.30474C18.206 5.36376 17.9379 5.50944 17.7275 5.72336C17.5171 5.93727 17.3738 6.20982 17.3157 6.50653C17.2577 6.80324 17.2875 7.11079 17.4014 7.39029C17.5152 7.66978 17.7081 7.90867 17.9555 8.07674ZM3.74621 15.2177V16.7473H7.19606L2.2417 21.7842L3.30539 22.8656L8.25975 17.8287V21.336H9.76427V15.2177H3.74621ZM15.7823 18.2769H12.7733V19.8064H15.7823V22.1008H21.8004V15.9825H15.7823V18.2769ZM17.2868 20.5712V17.5121H20.2959V20.5712H17.2868ZM8.02885 9.67292C7.62863 9.31407 7.30809 8.87275 7.08853 8.37827C6.86897 7.88378 6.75542 7.34747 6.75542 6.80494C6.75542 6.26241 6.86897 5.72609 7.08853 5.23161C7.30809 4.73713 7.62863 4.29581 8.02885 3.93696L9.02484 5.08415C8.78458 5.29946 8.59215 5.5643 8.46034 5.86106C8.32853 6.15782 8.26035 6.47971 8.26035 6.80532C8.26035 7.13094 8.32853 7.45282 8.46034 7.74958C8.59215 8.04634 8.78458 8.31118 9.02484 8.52649L8.02885 9.67292Z"
                fill="${GRAY}" />
            </svg>
        `);
    });
    const iconVNode = h(TaskIcon, {customIcon: {icon: icon.value}});

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

        sse.value = executionsStore.followExecutionDependencies({id: route.params.id, expandAll: expandAll.value})
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

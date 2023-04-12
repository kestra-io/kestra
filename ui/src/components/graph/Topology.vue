<script setup>
    import {ref, onMounted, nextTick, watch, getCurrentInstance, onBeforeUnmount} from "vue";
    import {mapState, useStore} from "vuex"
    import {VueFlow, Panel, useVueFlow, Position, MarkerType, PanelPosition} from "@vue-flow/core"
    import {Controls, ControlButton} from "@vue-flow/controls"
    import dagre from "dagre"
    import ArrowCollapseRight from "vue-material-design-icons/ArrowCollapseRight.vue";
    import ArrowCollapseDown from "vue-material-design-icons/ArrowCollapseDown.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import LightningBolt from "vue-material-design-icons/LightningBolt.vue";
    import FileEdit from "vue-material-design-icons/FileEdit.vue";
    import Exclamation from "vue-material-design-icons/Exclamation.vue";
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import Delete from "vue-material-design-icons/Delete.vue";

    import BottomLine from "../../components/layout/BottomLine.vue";
    import TriggerFlow from "../../components/flows/TriggerFlow.vue";
    import ValidationError from "../../components/flows/ValidationError.vue";
    import SwitchView from "./SwitchView.vue";
    import PluginDocumentation from "../plugins/PluginDocumentation.vue";
    import {cssVariable} from "../../utils/global"
    import Cluster from "./nodes/Cluster.vue";
    import Dot from "./nodes/Dot.vue"
    import Task from "./nodes/Task.vue";
    import Trigger from "./nodes/Trigger.vue";
    import Edge from "./nodes/Edge.vue";
    import {linkedElements} from "../../utils/vueFlow";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import YamlUtils from "../../utils/yamlUtils";
    import {getElement} from "bootstrap/js/src/util";
    import Utils from "../../utils/utils";
    import taskEditor from "../flows/TaskEditor.vue";
    import metadataEditor from "../flows/MetadataEditor.vue";
    import editor from "../inputs/Editor.vue";
    import yamlUtils from "../../utils/yamlUtils";
    import {pageFromRoute} from "../../utils/eventsRouter";
    import {canSaveFlowTemplate} from "../../utils/flowTemplate";

    const {
        id,
        toObject,
        getNodes,
        removeNodes,
        getEdges,
        removeEdges,
        fitView,
        addSelectedElements,
        removeSelectedNodes,
        removeSelectedEdges,
        getElements,
        removeSelectedElements,
        onNodeDragStart,
        onNodeDragStop,
        onNodeDrag
    } = useVueFlow()
    const store = useStore();
    const router = getCurrentInstance().appContext.config.globalProperties.$router;
    const emit = defineEmits(["follow"])
    const flow = store.getters["flow/flow"];
    const toast = getCurrentInstance().appContext.config.globalProperties.$toast();
    const t = getCurrentInstance().appContext.config.globalProperties.$t;
    const http = getCurrentInstance().appContext.config.globalProperties.$http;
    const tours = getCurrentInstance().appContext.config.globalProperties.$tours;

    const props = defineProps({
        flowGraph: {
            type: Object,
            required: false
        },
        flowId: {
            type: String,
            required: false
        },
        namespace: {
            type: String,
            required: false
        },
        execution: {
            type: Object,
            default: undefined
        },
        isCreating: {
            type: Boolean,
            default: false
        },
        isReadOnly: {
            type: Boolean,
            default: true
        },
        sourceCopy: {
            type: String,
            default: null
        },
        total: {
            type: Number,
            default: null
        },
        guidedProperties: {
            type: Object,
            default: {
                tourStarted: false
            }
        },
        flowError: {
            type: String,
            default: undefined
        }

    })

    const isHorizontal = ref(localStorage.getItem("topology-orientation") !== "0");
    const isLoading = ref(false);
    const elements = ref([])
    const haveChange = ref(false)
    const flowYaml = ref("")
    const lastPosition = ref(null)
    const newTrigger = ref(null)
    const isNewTriggerOpen = ref(false)
    const newError = ref(null)
    const isNewErrorOpen = ref(false)
    const isEditMetadataOpen = ref(false)
    const metadata = ref(null);
    const showTopology = ref(props.execution || props.isReadOnly ? "topology" : "doc");
    const updatedFromEditor = ref(false);
    const timer = ref(null);
    const dragging = ref(false);
    const taskError = ref(store.getters["flow/taskError"])
    const user = store.getters["auth/user"];

    watch(() => store.getters["flow/taskError"], async () => {
        taskError.value = store.getters["flow/taskError"];
    });


    const flowables = () => {
        return props.flowGraph && props.flowGraph.flowables ? props.flowGraph.flowables : [];
    }

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
    const flowHaveTasks = (source) => {
        const flow = source ? source : flowYaml.value
        return flow ? YamlUtils.flowHaveTasks(flow) : false;
    }

    const initYamlSource = () => {
        flowYaml.value = flow ? flow.source : YamlUtils.stringify({
            id: props.flowId,
            namespace: props.namespace
        });
        return flowYaml.value;
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

    const getNodePosition = (n, parent, alignTo) => {
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
        removeSelectedElements(getElements.value)
        elements.value = []
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
        fitView();
    };

    const generateGraph = () => {
        isLoading.value = true;
        if (!props.flowGraph) {
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
            isLoading.value = false;
            return;
        }
        if (props.flowGraph === undefined) {
            isLoading.value = false;
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
                draggable: nodeType === "task" && !props.isReadOnly,
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
                    nextTaskId: getNextTaskId(edge.target)
                }
            })
        }
        isLoading.value = false;
    }

    const getFirstTaskId = () => {
        return YamlUtils.getFirstTask(flowYaml.value);
    }

    const getNextTaskId = (target) => {
        while (YamlUtils.extractTask(flowYaml.value, target) === undefined) {
            const edge = props.flowGraph.edges.find(e => e.source === target)
            if (!edge) {
                return null
            }
            target = edge.target
        }
        return target

    }

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

    const isLinkToFirstFlowableTask = (edge) => {
        const firstTaskId = getFirstTaskId();
        return flowables().includes(firstTaskId) && edge.target === firstTaskId;

    }

    const getClusterTaskIdWithEndNodeUid = (nodeUid) => {
        const cluster = props.flowGraph.clusters.find(cluster => cluster.end === nodeUid);
        if (cluster) {
            return Utils.splitFirst(cluster.cluster.uid, "cluster_");
        }
        return undefined;
    }

    onMounted(() => {
        if (props.isCreating) {
            store.commit("flow/setFlowGraph", undefined);
        }
        initYamlSource();
        generateGraph();
        // Regenerate graph on window resize
        observeWidth();
        // Save on ctrl+s in topology
        document.addEventListener("keydown", save);
        // Guided tour
        setTimeout(() => {
            if (!props.guidedProperties.tourStarted
                && localStorage.getItem("tourDoneOrSkip") !== "true"
                && props.total === 0) {
                tours["guidedTour"].start();
                showTopology.value = "source";
            }
        }, 200)
        window.addEventListener("popstate", () => {
            stopTour();
        });
    })

    onBeforeUnmount(() => {
        store.commit("plugin/setEditorPlugin",undefined);
        document.removeEventListener("keydown", save);
        document.removeEventListener("popstate", () => {
            stopTour();
        });
    })


    const stopTour = () => {
        tours["guidedTour"].stop();
        store.commit("core/setGuidedProperties", {
            ...props.guidedProperties,
            tourStarted: false
        });
    }

    const observeWidth = () => {
        const resizeObserver = new ResizeObserver(function (entries) {
            clearTimeout(timer.value);
            timer.value = setTimeout(() => {
                regenerateGraph();
                nextTick(() => {
                    fitView()
                })
            }, 50);
        });
        resizeObserver.observe(document.getElementById("el-col-vueflow"));
    }

    watch(() => props.flowGraph, async () => {
        regenerateGraph()
    });

    watch(() => props.isReadOnly, async () => {
        showTopology.value = props.isCreating ? "source" : (props.execution || props.isReadOnly ? "topology" : "combined");
    });

    watch(() => props.guidedProperties, () => {
        if (localStorage.getItem("tourDoneOrSkip") !== "true") {
            if (props.guidedProperties.source !== undefined) {
                haveChange.value = true;
                flowYaml.value = props.guidedProperties.source
                updatedFromEditor.value = true;
            }
            if (props.guidedProperties.saveFlow) {
                save();
            }
        }
    });

    const isAllowedEdit = () => {
        if (props.isCreating) {
            return user && getFlowMetadata().namespace && user.isAllowed(permission.FLOW, action.CREATE, getFlowMetadata().namespace);
        } else {
            return user && user.isAllowed(permission.FLOW, action.UPDATE, props.namespace);
        }
    };

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

    const forwardEvent = (type, event) => {
        emit(type, event);
    };

    const updatePluginDocumentation = (event) => {
        const taskType = yamlUtils.getTaskType(event.model.getValue(), event.position)
        const pluginSingleList = store.getters["plugin/getPluginSingleList"];
        const pluginsDocumentation = store.getters["plugin/getPluginsDocumentation"];
        if (taskType && pluginSingleList.includes(taskType)) {
            if (!pluginsDocumentation[taskType]) {
                store
                    .dispatch("plugin/load", {cls: taskType})
                    .then(plugin => {
                        store.commit("plugin/setPluginsDocumentation", {...pluginsDocumentation, [taskType]: plugin});
                        store.commit("plugin/setEditorPlugin", plugin);
                    });
            } else if (pluginsDocumentation[taskType]) {
                store.commit("plugin/setEditorPlugin", pluginsDocumentation[taskType]);
            }
        } else {
            store.commit("plugin/setEditorPlugin", undefined);
        }
    };

    const onEdit = (event) => {
        store.dispatch("flow/validateFlow", {flow: event})
            .then(value => {
                if (value[0].constraints && !flowHaveTasks(event)) {
                    flowYaml.value = event;
                    haveChange.value = true;
                } else {
                    // flowYaml need to be update before
                    // loadGraphFromSource to avoid
                    // generateGraph to be triggered with the old value
                    flowYaml.value = event;
                    store.dispatch("flow/loadGraphFromSource", {
                        flow: event, config: {
                            validateStatus: (status) => {
                                return status === 200 || status === 422;
                            }
                        }
                    }).then(response => {
                        haveChange.value = true;
                        store.dispatch("core/isUnsaved", true);
                    })
                }
            })

    }

    const onDelete = (event) => {
        const flowParsed = YamlUtils.parse(flowYaml.value);
        toast.confirm(
            t("delete task confirm", {taskId: flowParsed.id}),
            () => {

                const section = event.section ? event.section : "tasks";
                if (section === "tasks" && flowParsed.tasks.length === 1 && flowParsed.tasks.map(e => e.id).includes(event.id)) {
                    store.dispatch("core/showMessage", {
                        variant: "error",
                        title: t("can not delete"),
                        message: t("can not have less than 1 task")
                    });
                    return;
                }
                onEdit(YamlUtils.deleteTask(flowYaml.value, event.id, section));
            },
            () => {
            }
        )
    }

    const onCreateNewTask = (event) => {
        const source = flowYaml.value;
        onEdit(YamlUtils.insertTask(source, event.taskId, event.taskYaml, event.insertPosition));
        haveChange.value = true;
    }

    const onUpdateNewTrigger = (event) => {
        clearTimeout(timer.value);
        timer.value = setTimeout(() => store.dispatch("flow/validateTask", {
            task: event,
            section: "trigger"
        }), 500);
        newTrigger.value = event;
    }

    const onSaveNewTrigger = () => {
        const source = flowYaml.value;
        const existingTask = YamlUtils.checkTaskAlreadyExist(source, newTrigger.value);
        if (existingTask) {
            store.dispatch("core/showMessage", {
                variant: "error",
                title: "Trigger Id already exist",
                message: `Trigger Id ${existingTask} already exist in the flow.`
            });
            return;
        }
        onEdit(YamlUtils.insertTrigger(source, newTrigger.value));
        newTrigger.value = null;
        isNewTriggerOpen.value = false;
        haveChange.value = true;
    }

    const onUpdateNewError = (event) => {
        clearTimeout(timer.value);
        timer.value = setTimeout(() => store.dispatch("flow/validateTask", {
            task: event,
            section: "task"
        }), 500);

        newError.value = event;
    }

    const onSaveNewError = () => {
        const source = flowYaml.value;
        const existingTask = YamlUtils.checkTaskAlreadyExist(source, newError.value);
        if (existingTask) {
            store.dispatch("core/showMessage", {
                variant: "error",
                title: "Task Id already exist",
                message: `Task Id ${existingTask} already exist in the flow.`
            });
            return;
        }
        onEdit(YamlUtils.insertError(source, newError.value));
        newError.value = null;
        isNewErrorOpen.value = false;
        haveChange.value = true;
    }

    const onAddFlowableError = (event) => {
        const source = flowYaml.value;
        onEdit(YamlUtils.insertErrorInFlowable(source, event.error, event.taskId));
        haveChange.value = true;
    }

    const getFlowMetadata = () => {
        return YamlUtils.getMetadata(flowYaml.value);
    }

    const checkRequiredMetadata = () => {
        if (metadata.value) {
            return metadata.value.id.length > 0 && metadata.value.namespace.length > 0
        }
        return getFlowMetadata().id.length > 0 && getFlowMetadata().namespace.length > 0
    }

    const onUpdateMetadata = (event) => {
        metadata.value = event;
    }

    const onSaveMetadata = () => {
        const source = flowYaml.value;
        flowYaml.value = YamlUtils.updateMetadata(source, metadata.value)
        metadata.value = null;
        isEditMetadataOpen.value = false;
        haveChange.value = true;
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
                onEdit(YamlUtils.swapTasks(flowYaml.value, taskNode1.id, taskNode2.id))
            } else {
                getNodes.value.find(n => n.id === e.node.id).position = lastPosition.value;
            }
        } else {
            getNodes.value.find(n => n.id === e.node.id).position = lastPosition.value;
        }
        resetNodesStyle();
        e.node.style = {...e.node.style, zIndex: 1}
        lastPosition.value = null;
    })

    onNodeDrag((e) => {
        resetNodesStyle();
        getNodes.value.filter(n => n.id !== e.node.id).forEach(n => {
            if (n.type === "trigger" || (n.type === "task" && YamlUtils.isParentChildrenRelation(flowYaml.value, n.id, e.node.id))) {
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
        if (tasksMeet.length === 1 && YamlUtils.isParentChildrenRelation(flowYaml.value, tasksMeet[0], node.id)) {
            return "parentchildrenerror";
        }
        if (intersections.filter(n => n.type === "trigger").length > 0) {
            return "triggererror";
        }
        return null;
    }

    const resetNodesStyle = () => {
        getNodes.value.filter(n => n.type === "task" || n.type === " trigger")
            .forEach(n => {
                n.style = {...n.style, opacity: "1", outline: "none"}
            })
    }

    const editorUpdate = (event) => {
        updatedFromEditor.value = true;
        flowYaml.value = event;
        haveChange.value = true;

        if (showTopology.value === "combined") {
            clearTimeout(timer.value);
            timer.value = setTimeout(() => onEdit(event), 500);
        }
    }

    const switchView = (event) => {
        showTopology.value = event
        if (["topology", "combined"].includes(showTopology.value)) {
            if (updatedFromEditor.value) {
                onEdit(flowYaml.value)
                updatedFromEditor.value = false;
            }
        }
    }

    const save = (e) => {
        if (e) {
            if (e.type === "keydown") {
                if (!(e.keyCode === 83 && e.ctrlKey) || !haveChange.value) {
                    return;
                }
                e.preventDefault();
            }
        }
        if (tours["guidedTour"].isRunning.value && !props.guidedProperties.saveFlow) {
            store.dispatch("api/events", {
                type: "ONBOARDING",
                onboarding: {
                    step: tours["guidedTour"].currentStep._value,
                    action: "next",
                },
                page: pageFromRoute(router.currentRoute.value)
            });
            tours["guidedTour"].nextStep();
            return;
        }
        if (props.isCreating) {
            const flowParsed = YamlUtils.parse(flowYaml.value);
            if (flowParsed.id && flowParsed.namespace) {
                store.dispatch("flow/createFlow", {flow: flowYaml.value})
                    .then((response) => {
                        toast.saved(response.id);
                        store.dispatch("core/isUnsaved", false);
                        router.push({
                            name: "flows/update",
                            params: {id: flowParsed.id, namespace: flowParsed.namespace, tab: "editor"}
                        });
                    })
                return;
            } else {
                store.dispatch("core/showMessage", {
                    variant: "error",
                    title: t("can not save"),
                    message: t("flow must have id and namespace")
                });
                return;
            }
        }
        store
            .dispatch("flow/saveFlow", {flow: flowYaml.value})
            .then((response) => {
                toast.saved(response.id);
                store.dispatch("core/isUnsaved", false);
            })
    };

    const canExecute = () => {
        return user.isAllowed(permission.EXECUTION, action.CREATE, namespace)
    }

    const canDelete = () => {
        return (
            user.isAllowed(
                permission.FLOW,
                action.DELETE,
                namespace
            )
        );
    }

    const deleteFlow = () => {
        const metadata = getFlowMetadata();

        return http
            .get(`/api/v1/flows/${metadata.namespace}/${metadata.id}/dependencies`, {params: {destinationOnly: true}})
            .then(response => {
                let warning = "";

                if (response.data && response.data.nodes) {
                    const deps = response.data.nodes
                        .filter(n => !(n.namespace === metadata.namespace && n.id === metadata.id))
                        .map(n => "<li>" + n.namespace + ".<code>" + n.id + "</code></li>")
                        .join("\n");

                    warning = "<div class=\"el-alert el-alert--warning is-light mt-3\" role=\"alert\">\n" +
                        "<div class=\"el-alert__content\">\n" +
                        "<p class=\"el-alert__description\">\n" +
                        this.$t("dependencies delete flow") +
                        "<ul>\n" +
                        deps +
                        "</ul>\n" +
                        "</p>\n" +
                        "</div>\n" +
                        "</div>"
                }

                return t("delete confirm", {name: metadata.id}) + warning;
            }).then(message => {
                toast
                    .confirm(message, () => {
                        return store
                            .dispatch("flow/deleteFlow", metadata)
                            .then(() => {
                                return router.push({
                                    name: "flows/list"
                                });
                            })
                            .then(() => {
                                toast.deleted(metadata.id);
                            })
                    });
            });
    }

</script>

<template>
    <el-card shadow="never" v-loading="isLoading">
        <div
            :class="showTopology === 'combined'? 'vueflow-combined' : showTopology === 'topology' ? 'vueflow': 'vueflow-hide'"
            id="el-col-vueflow"
        >
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
                        @edit="onEdit"
                        @delete="onDelete"
                        @addFlowableError="onAddFlowableError"
                        @mouseover="onMouseOver"
                        @mouseleave="onMouseLeave"
                        :is-read-only="isReadOnly"
                        :is-allowed-edit="isAllowedEdit()"
                    />
                </template>

                <template #node-trigger="props">
                    <Trigger
                        v-bind="props"
                        @edit="onEdit"
                        @delete="onDelete"
                        :is-read-only="isReadOnly"
                        :is-allowed-edit="isAllowedEdit()"
                    />
                </template>

                <template #edge-edge="props">
                    <Edge
                        v-bind="props"
                        :yaml-source="flowYaml"
                        :flowables-ids="flowables()"
                        @edit="onCreateNewTask"
                        :is-read-only="isReadOnly"
                        :is-allowed-edit="isAllowedEdit()"
                    />
                </template>

                <Controls :show-interactive="false">
                    <ControlButton @click="toggleOrientation">
                        <ArrowCollapseDown v-if="!isHorizontal" />
                        <ArrowCollapseRight v-if="isHorizontal" />
                    </ControlButton>
                </Controls>
            </VueFlow>
        </div>
        <editor
            v-if="['doc', 'combined', 'source'].includes(showTopology)"
            :class="['doc','combined'].includes(showTopology) ? 'editor-combined' : ''"
            @save="save"
            v-model="flowYaml"
            schema-type="flow"
            lang="yaml"
            @update:model-value="editorUpdate($event)"
            @cursor="updatePluginDocumentation($event)"
            :creating="isCreating"
            @restartGuidedTour="() => showTopology = 'source'"
        />
        <PluginDocumentation
            v-if="showTopology === 'doc'"
            class="plugin-doc"
        />
        <el-drawer
            v-if="isNewErrorOpen"
            v-model="isNewErrorOpen"
            title="Add a global error handler"
            destroy-on-close
            size=""
            :append-to-body="true"
        >
            <el-form label-position="top">
                <task-editor
                    section="tasks"
                    @update:model-value="onUpdateNewError($event)"
                />
            </el-form>
            <template #footer>
                <ValidationError :error="taskError" />
                <el-button :icon="ContentSave" @click="onSaveNewError()" type="primary" :disabled="taskError">
                    {{ $t("save") }}
                </el-button>
            </template>
        </el-drawer>
        <el-drawer
            v-if="isNewTriggerOpen"
            v-model="isNewTriggerOpen"
            title="Add a trigger"
            destroy-on-close
            size=""
            :append-to-body="true"
        >
            <el-form label-position="top">
                <task-editor
                    section="triggers"
                    @update:model-value="onUpdateNewTrigger($event)"
                />
            </el-form>
            <template #footer>
                <ValidationError :error="taskError" />
                <el-button :icon="ContentSave" @click="onSaveNewTrigger()" type="primary" :disabled="taskError">
                    {{ $t("save") }}
                </el-button>
            </template>
        </el-drawer>
        <el-drawer
            v-if="isEditMetadataOpen"
            v-model="isEditMetadataOpen"
            destroy-on-close
            size=""
            :append-to-body="true"
        >
            <template #header>
                <code>flow metadata</code>
            </template>

            <el-form label-position="top">
                <metadata-editor
                    :metadata="getFlowMetadata()"
                    @update:model-value="onUpdateMetadata($event)"
                    :editing="!props.isCreating"
                />
            </el-form>
            <template #footer>
                <el-button
                    :icon="ContentSave"
                    @click="onSaveMetadata()"
                    type="primary"
                    :disabled="!checkRequiredMetadata()"
                    class="edit-flow-save-button"
                >
                    {{ $t("save") }}
                </el-button>
            </template>
        </el-drawer>
        <SwitchView
            v-if="!isReadOnly"
            :type="showTopology"
            class="to-topology-button"
            @switch-view="switchView"
        />
    </el-card>
    <bottom-line>
        <ul>
            <li v-if="(isAllowedEdit || canDelete) && !isReadOnly">
                <el-dropdown>
                    <el-button size="large" type="default">
                        <DotsVertical title="" />
                        {{ t("actions") }}
                    </el-button>
                    <template #dropdown>
                        <el-dropdown-menu class="dropdown-menu">
                            <el-dropdown-item
                                v-if="!props.isCreating && canDelete"
                                class="dropdown-button"
                                :icon="Delete"
                                size="large"
                                @click="deleteFlow"
                            >
                                {{ $t("delete") }}
                            </el-dropdown-item>

                            <el-dropdown-item
                                v-if="!props.isCreating"
                                class="dropdown-button"
                                :icon="ContentCopy"
                                size="large"
                                @click="() => router.push({name: 'flows/create', query: {copy: true}})"
                            >
                                {{ $t("copy") }}
                            </el-dropdown-item>
                            <el-dropdown-item
                                v-if="isAllowedEdit"
                                class="dropdown-button"
                                :icon="Exclamation"
                                size="large"
                                @click="isNewErrorOpen = true;"
                                :disabled="!flowHaveTasks()"
                            >
                                {{ $t("add global error handler") }}
                            </el-dropdown-item>
                            <el-dropdown-item
                                v-if="isAllowedEdit"
                                class="dropdown-button"
                                :icon="LightningBolt"
                                size="large"
                                @click="isNewTriggerOpen = true;"
                                :disabled="!flowHaveTasks()"
                            >
                                {{ $t("add trigger") }}
                            </el-dropdown-item>
                            <el-dropdown-item
                                v-if="isAllowedEdit"
                                class="dropdown-button"
                                :icon="FileEdit"
                                size="large"
                                @click="isEditMetadataOpen = true;"
                            >
                                {{ $t("edit metadata") }}
                            </el-dropdown-item>
                        </el-dropdown-menu>
                    </template>
                </el-dropdown>
            </li>
            <li v-if="flow">
                <trigger-flow
                    v-if="!props.isCreating"
                    type="default"
                    :disabled="flow.disabled"
                    :flow-id="flow.id"
                    :namespace="flow.namespace"
                />
            </li>
            <li>
                <el-button
                    :icon="ContentSave"
                    size="large"
                    @click="save"
                    v-if="isAllowedEdit"
                    :disabled="!haveChange || !flowHaveTasks()"
                    type="primary"
                    class="edit-flow-save-button"
                >
                    {{ $t("save") }}
                </el-button>
            </li>
        </ul>
    </bottom-line>
</template>

<style lang="scss" scoped>
    .el-card {
        height: calc(100vh - 300px);
        position: relative;

        :deep(.el-card__body) {
            height: 100%;
        }
    }

    .to-topology-button {
        position: absolute;
        top: 30px;
        right: 30px;
    }

    .editor {
        height: 100%;
        width: 100%;
    }

    .editor-combined {
        height: 100%;
        width: 50%;
        float: left;
    }

    .vueflow {
        height: 100%;
        width: 100%;
    }

    .vueflow-combined {
        height: 100%;
        width: 50%;
        float: right;
    }

    .vueflow-hide {
        width: 0%;
    }

    .plugin-doc {
        overflow-x: hidden;
        padding: calc(var(--spacer) * 3);
        height: 100%;
        width: 50%;
        float: right;
        overflow-y: scroll;
        padding: calc(var(--spacer) * 1.5);
        background-color: var(--bs-gray-300);

        html.dark & {
            background-color: var(--bs-gray-500);
        }
    }

    .dropdown-menu {
        display: flex;
        flex-direction: column;
        width: 20rem;
    }
</style>
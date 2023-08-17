<script setup>
    // Core
    import {getCurrentInstance, nextTick, onMounted, onBeforeMount, ref, watch} from "vue";
    import {useStore} from "vuex";
    import {MarkerType, Position, useVueFlow, VueFlow} from "@vue-flow/core";

    import TaskEdit from "../flows/TaskEdit.vue";
    import SearchField from "../layout/SearchField.vue";
    import LogLevelSelector from "../logs/LogLevelSelector.vue";
    import LogList from "../logs/LogList.vue";
    import Collapse from "../layout/Collapse.vue";

    // Nodes
    import {
        TaskNode,
        DotNode,
        ClusterNode,
        EdgeNode,
        TriggerNode,
        CollapsedClusterNode
    } from "@kestra-io/ui-libs"

    // Topology Control
    import {Controls, ControlButton} from "@vue-flow/controls"
    import {Background} from "@vue-flow/background";
    import SplitCellsHorizontal from "../../assets/icons/SplitCellsHorizontal.vue"
    import SplitCellsVertical from "../../assets/icons/SplitCellsVertical.vue"

    // Utils
    import YamlUtils from "../../utils/yamlUtils";
    import {SECTIONS} from "../../utils/constants";
    import {linkedElements} from "../../utils/vueFlow";
    import {cssVariable} from "../../utils/global";
    import dagre from "dagre";
    import Utils from "../../utils/utils";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import TaskEditor from "../flows/TaskEditor.vue";
    import ValidationError from "../flows/ValidationError.vue";
    import Markdown from "../layout/Markdown.vue";
    import yamlUtils from "../../utils/yamlUtils";
    const router = getCurrentInstance().appContext.config.globalProperties.$router;

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
    const icons = ref(store.getters["plugin/getIcons"]);
    const edgeReplacer = ref({});
    const hiddenNodes = ref([]);
    const collapsed = ref([]);
    const clusterCollapseToNode = ref(["Triggers"])
    const clusterToNode = ref([])
    const taskObject = ref(null);
    const taskEditData = ref(null);
    const taskEdit = ref(null);
    const isShowLogsOpen = ref(false);
    const logFilter = ref("");
    const logLevel = ref(localStorage.getItem("defaultLogLevel") || "INFO");
    const isDrawerOpen = ref(false);
    const isShowDescriptionOpen = ref(false);
    const selectedTask = ref(null);

    // Init components
    onMounted(() => {
        // Regenerate graph on window resize
        observeWidth();

    })

    onBeforeMount(() => {
        store.dispatch("plugin/icons").then(icons => icons.value = icons)
    })

    watch(() => props.flowGraph, () => {
        generateGraph();
    })

    watch(() => store.getters["plugin/getIcons"], () => {
        icons.value = ref(store.getters["plugin/getIcons"])
    })

    watch(() => isDrawerOpen.value, () => {
        if (!isDrawerOpen.value) {
            isShowDescriptionOpen.value = false;
            isShowLogsOpen.value = false;
            selectedTask.value = null;
        }
    })

    watch(() => props.viewType, () => {
        isHorizontal.value = props.viewType === "source-topology" ? false :
            (props.viewType?.indexOf("blueprint") !== -1 ? true : localStorage.getItem("topology-orientation") === "1")
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
            t("delete task confirm", {taskId: event.id}),
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
                emit("on-edit", YamlUtils.deleteTask(props.source, event.id, section))
            },
            () => {
            }
        )
    }

    // Source edit functions
    const onCreateNewTask = (event) => {
        console.log(event)
        taskEditData.value = {
            insertionDetails: event,
            action: "create_task",
            section: SECTIONS.TASKS
        };
        taskEdit.value.$refs.taskEdit.click()
    }

    const onEditTask = (event) => {
        taskEditData.value = {
            action: "edit_task",
            section: event.section ? event.section : SECTIONS.TASKS,
            oldTaskId: event.task.id,
        };
        taskObject.value = event.task
        taskEdit.value.$refs.taskEdit.click()
    }

    const onAddFlowableError = (event) => {
        taskEditData.value = {
            action: "add_flowable_error",
            taskId: event.task.id
        };
        taskEdit.value.$refs.taskEdit.click()

    }

    const confirmEdit = (event) => {
        const source = props.source;
        const task = YamlUtils.extractTask(props.source, YamlUtils.parse(event).id);
        if (task === undefined || (task && YamlUtils.parse(event).id === taskEditData.value.oldTaskId)) {
            switch (taskEditData.value.action) {
            case("create_task"):
                emit("on-edit", YamlUtils.insertTask(source, taskEditData.value.insertionDetails[0], event, taskEditData.value.insertionDetails[1]))
                return;
            case("edit_task"):
                emit("on-edit", YamlUtils.replaceTaskInDocument(
                    source,
                    taskEditData.value.oldTaskId,
                    event
                ))
                return;
            case("add_flowable_error"):
                emit("on-edit", YamlUtils.insertErrorInFlowable(props.source, event, taskEditData.value.taskId))
                return;
            }
        } else {
            store.dispatch("core/showMessage", {
                variant: "error",
                title: t("error detected"),
                message: t("Task Id already exist in the flow", {taskId: YamlUtils.parse(event).id})
            });
        }
        taskEditData.value = null;
        taskObject.value = null;
    }

    const closeEdit = () => {
        taskEditData.value = null;
        taskObject.value = null;
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
                    n.style = {...n.style, outline: "0.5px solid " + cssVariable("--bs-gray-900")}
                    n.class = "rounded-3"
                }
            });
        }

    }

    const onMouseLeave = () => {
        resetNodesStyle();
    }

    const resetNodesStyle = () => {
        getNodes.value.filter(n => n.type === "task" || n.type === "trigger")
            .forEach(n => {
                n.style = {...n.style, opacity: "1", outline: "none"}
                n.class = ""
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
        e.node.style = {...e.node.style, zIndex: 5}
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
                    n.class = "rounded-3"
                }
            })
            e.node.style = {...e.node.style, outline: "0.5px solid " + cssVariable("--bs-primary")}
            e.node.class = "rounded-3"
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
        dagreGraph.setGraph({rankdir: isHorizontal.value ? "LR" : "TB", edgesep: 5, ranksep: 40})

        for (const node of props.flowGraph.nodes) {
            if (!hiddenNodes.value.includes(node.uid)) {
                dagreGraph.setNode(node.uid, {
                    width: getNodeWidth(node),
                    height: getNodeHeight(node)
                })
            }
        }

        for (let cluster of (props.flowGraph.clusters || [])) {
            if (clusterCollapseToNode.value.includes(cluster.cluster.uid) && collapsed.value.includes(cluster.cluster.uid)) {
                const node = {uid: cluster.cluster.uid, type: "collapsedcluster"};
                dagreGraph.setNode(cluster.cluster.uid, {
                    width: getNodeWidth(node),
                    height: getNodeHeight(node)
                });
                clusterToNode.value.push(node)
                continue
            }
            if (!edgeReplacer.value[cluster.cluster.uid]) {
                dagreGraph.setNode(cluster.cluster.uid, {clusterLabelPos: "top"});

                for (let node of (cluster.nodes || [])) {
                    if (!hiddenNodes.value.includes(node)) {
                        dagreGraph.setParent(node, cluster.cluster.uid)
                    }
                }
            }
            if (cluster.parents) {
                const nodeChild = edgeReplacer.value[cluster.cluster.uid] ? edgeReplacer.value[cluster.cluster.uid] : cluster.cluster.uid
                if (!hiddenNodes.value.includes(nodeChild)) {
                    dagreGraph.setParent(nodeChild, cluster.parents[cluster.parents.length - 1]);
                }
            }
        }

        for (const edge of (props.flowGraph.edges || [])) {
            const newEdge = replaceIfCollapsed(edge.source, edge.target);
            if (newEdge) {
                dagreGraph.setEdge(newEdge.source, newEdge.target)
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
        return node.task !== undefined && (node.type === "io.kestra.core.models.hierarchies.GraphTask")
    };

    const isTriggerNode = (node) => {
        return node.trigger !== undefined && (node.type === "io.kestra.core.models.hierarchies.GraphTrigger");
    }

    const isCollapsedCluster = (node) => {
        return node.type === "collapsedcluster";
    }

    const getNodeWidth = (node) => {
        return isTaskNode(node) || isTriggerNode(node) ? 184 : isCollapsedCluster(node) ? 150 : 5;
    };

    const getNodeHeight = (node) => {
        return isTaskNode(node) || isTriggerNode(node) ? 44 : isCollapsedCluster(node) ? 44 : 5;
    };

    const getNodeIcon = (node) => {
        const type = isTaskNode(node) ? node.task.type : isTriggerNode(node) ? node.trigger.type : undefined
        if (type && icons?.value?.value) {
            return icons.value.value[type]
        }
        return null;
    }

    const haveAdd = (edge) => {
        if (isLinkToFirstTask(edge)) {
            return [getNextTaskId(edge.target), "before"];
        }
        if (isTaskParallel(edge.target) || YamlUtils.isTrigger(props.source, edge.target) || YamlUtils.isTrigger(props.source, edge.source)) {
            return undefined;
        }
        if (YamlUtils.extractTask(props.source, edge.source) && YamlUtils.extractTask(props.source, edge.target)) {
            return [edge.source, "after"];
        }
        // Check if edge is an ending flowable
        // If true, enable add button to add a task
        // under the flowable task
        if (edge.source.endsWith("_end") && edge.target.endsWith("_end")) {
            // Cluster uid contains the flowable task id
            // So we look for the cluster having this end edge
            // to return his flowable id
            return [getClusterTaskIdWithEndNodeUid(edge.source), "after"];
        }
        if (flowables().includes(edge.source)) {
            return [getNextTaskId(edge.target), "before"];
        }
        if (YamlUtils.extractTask(props.source, edge.source) && edge.target.endsWith("_end")) {
            return [edge.source, "after"];
        }
        if (YamlUtils.extractTask(props.source, edge.source) && edge.target.endsWith("_start")) {
            return [edge.source, "after"];
        }
        if (YamlUtils.extractTask(props.source, edge.target) && edge.source.endsWith("_end")) {
            return [edge.target, "before"];
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

    const isLinkToFirstTask = (edge) => {
        const firstTaskId = getFirstTaskId();
        return edge.target === firstTaskId;
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

    const collapseCluster = (clusterUid, regenerate, recursive) => {

        const cluster = props.flowGraph.clusters.find(cluster => cluster.cluster.uid === clusterUid)
        const nodeId = clusterUid === "Triggers" ? "Triggers" : Utils.splitFirst(clusterUid, "cluster_");
        collapsed.value = collapsed.value.concat([nodeId])

        hiddenNodes.value = hiddenNodes.value.concat(cluster.nodes.filter(e => e != nodeId || recursive));
        if (clusterUid !== "Triggers") {
            hiddenNodes.value = hiddenNodes.value.concat([cluster.cluster.uid])
            edgeReplacer.value = {
                ...edgeReplacer.value,
                [cluster.cluster.uid]: nodeId,
                [cluster.start]: nodeId,
                [cluster.end]: nodeId
            }

            for (let child of cluster.nodes) {
                if (props.flowGraph.clusters.map(cluster => cluster.cluster.uid).includes(child)) {
                    collapseCluster(child, false, true);
                }
            }
        } else {
            edgeReplacer.value = {
                ...edgeReplacer.value,
                [cluster.start]: nodeId,
                [cluster.end]: nodeId
            }
        }

        if (regenerate) {
            generateGraph();
        }
    }

    const expand = (taskId) => {
        const cluster = props.flowGraph.clusters.find(cluster => cluster.cluster.uid === "cluster_" + taskId)

        edgeReplacer.value = {}
        hiddenNodes.value = []
        clusterToNode.value = []
        collapsed.value = collapsed.value.filter(n => n != taskId)

        collapsed.value.forEach(n => collapseCluster("cluster_" + n, false, false))

        generateGraph();
    }

    const replaceIfCollapsed = (source, target) => {
        const newSource = edgeReplacer.value[source] ? edgeReplacer.value[source] : source
        const newTarget = edgeReplacer.value[target] ? edgeReplacer.value[target] : target

        if (newSource == newTarget || (hiddenNodes.value.includes(newSource) || hiddenNodes.value.includes(newTarget))) {
            return null;
        }
        return {target: newTarget, source: newSource}
    }

    const cleanGraph = () => {
        removeEdges(getEdges.value)
        removeNodes(getNodes.value)
        removeSelectedElements(getElements.value)
        elements.value = []
    }

    const openFlow = (data) => {
        if (data.link.executionId) {
            store
                .dispatch("execution/loadExecution", {id: data.link.executionId})
                .then(value => {
                    store.commit("execution/setExecution", value);
                    router.push({
                        name: "executions/update",
                        params: {namespace: data.link.namespace, flowId: data.link.id, tab: "topology", id: data.link.executionId,},
                    });
                })
        } else {
            router.push({
                name: "flows/update",
                params: {"namespace": data.link.namespace, "id": data.link.id, tab: "overview"},
            });
        }
    }

    const nodeColor = (node) => {
        if (isTaskNode(node)) {
            if (collapsed.value.includes(node.uid)) {
                return "blue";
            }
            if (YamlUtils.isTaskError(props.source, node.task.id)) {
                return "danger"
            }
            if (node.task.type === "io.kestra.core.tasks.flows.Flow") {
                return "primary"
            }
        } else if (isTriggerNode(node) || isCollapsedCluster(node)) {
            return "success";
        }
        return "default"
    }

    const isTaskParallel = (taskId) => {
        const clusterTask = YamlUtils.parse(YamlUtils.extractTask(props.source, taskId));
        return clusterTask?.type === "io.kestra.core.tasks.flows.EachParallel" ||
            clusterTask?.type === "io.kestra.core.tasks.flows.Parallel" ? clusterTask : undefined;
    }

    const getEdgeColor = (edge) => {
        if (YamlUtils.isTaskError(props.source, edge.source) || YamlUtils.isTaskError(props.source, edge.target)) {
            return "danger"
        }
        return null;
    }

    const generateGraph = () => {
        cleanGraph();

        nextTick(() => {
            emit("loading", true);
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
                    data: {
                        edge: {
                            relation: {
                                relationType: "SEQUENTIAL"
                            }
                        },
                        isFlowable: false,
                        initTask: true,
                        color: "primary"
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
                if (!edgeReplacer.value[cluster.cluster.uid] && !collapsed.value.includes(cluster.cluster.uid)) {
                    for (let nodeUid of cluster.nodes) {
                        clusters[nodeUid] = cluster.cluster;
                    }

                    const clusterUid = cluster.cluster.uid;
                    const dagreNode = dagreGraph.node(clusterUid)
                    const parentNode = cluster.parents ? cluster.parents[cluster.parents.length - 1] : undefined;

                    elements.value.push({
                        id: clusterUid,
                        type: "cluster",
                        parentNode: parentNode,
                        position: getNodePosition(dagreNode, parentNode ? dagreGraph.node(parentNode) : undefined),
                        style: {
                            width: clusterUid === "Triggers" && isHorizontal.value ? "350px" : dagreNode.width + "px",
                            height: clusterUid === "Triggers" && !isHorizontal.value ? "180px" : dagreNode.height + "px"
                        },
                        data: {
                            collapsable: true,
                            color: clusterUid === "Triggers" ? "success" : "blue"
                        },
                        class: `bg-light-${clusterUid === "Triggers" ? "success" : "blue"}-border rounded p-2`,
                    })
                }
            }

            let disabledLowCode = [];
            for (const node of props.flowGraph.nodes.concat(clusterToNode.value)) {
                if (!hiddenNodes.value.includes(node.uid)) {
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
                    } else if (node.type === "collapsedcluster") {
                        nodeType = "collapsedcluster";
                    }
                    // Disable interaction for Dag task
                    // because our low code editor can not handle it for now
                    if (isTaskNode(node) && node.task.type === "io.kestra.core.tasks.flows.Dag") {
                        disabledLowCode.push(node.task.id);
                        YamlUtils.getChildrenTasks(props.source, node.task.id).forEach(child => {
                            disabledLowCode.push(child);
                        })
                    }

                    const taskId = node?.task?.id;
                    elements.value.push({
                        id: node.uid,
                        label: isTaskNode(node) ? taskId : "",
                        type: nodeType,
                        position: getNodePosition(dagreNode, clusters[node.uid] ? dagreGraph.node(clusters[node.uid].uid) : undefined),
                        style: {
                            width: getNodeWidth(node) + "px",
                            height: getNodeHeight(node) + "px"
                        },
                        sourcePosition: isHorizontal.value ? Position.Right : Position.Bottom,
                        targetPosition: isHorizontal.value ? Position.Left : Position.Top,
                        parentNode: clusters[node.uid] ? clusters[node.uid].uid : undefined,
                        draggable: nodeType === "task" && !props.isReadOnly && isTaskNode(node) ? !disabledLowCode.includes(taskId) : false,
                        data: {
                            node: node,
                            namespace: props.namespace,
                            flowId: props.flowId,
                            execution: props.execution,
                            revision: props.execution ? props.execution.flowRevision : undefined,
                            isFlowable: isTaskNode(node) ? flowables().includes(taskId) : false,
                            color: nodeType != "dot" ? nodeColor(node) : null,
                            expandable: taskId ? flowables().includes(taskId) && edgeReplacer.value["cluster_" + taskId] !== undefined : isCollapsedCluster(node),
                            icon: getNodeIcon(node),
                            isReadOnly: props.isReadOnly,
                            link: node.task?.type === "io.kestra.core.tasks.flows.Flow" ? linkDatas(node.task) : false
                        },
                        class: node.type === "collapsedcluster" ? `bg-light-${node.uid === "Triggers" ? "success" : "blue"}-border rounded p-2` : "",
                    })
                }
            }
            for (const edge of (props.flowGraph.edges || [])) {
                const newEdge = replaceIfCollapsed(edge.source, edge.target);
                if (newEdge) {
                    elements.value.push({
                        id: newEdge.source + "|" + newEdge.target,
                        source: newEdge.source,
                        target: newEdge.target,
                        type: "edge",
                        markerEnd: YamlUtils.extractTask(props.source, newEdge.target) ? {
                            id: "marker-custom",
                            type: MarkerType.ArrowClosed,
                        } : "",
                        data: {
                            haveAdd: haveAdd(edge),
                            isFlowable: flowables().includes(edge.source) || flowables().includes(edge.target),
                            haveDashArray: YamlUtils.isTrigger(props.source, edge.source) || YamlUtils.isTrigger(props.source, edge.target),
                            nextTaskId: getNextTaskId(edge.target),
                            disabled: disabledLowCode.includes(edge.source) || props.execution || props.isReadOnly || !props.isAllowedEdit,
                            color: getEdgeColor(edge)
                        },
                        style: {
                            zIndex: 10,
                        }
                    })
                }
            }
            fitView();
            emit("loading", false);
        })
    }

    const showLogs = (event) => {
        selectedTask.value = event
        isShowLogsOpen.value = true;
        isDrawerOpen.value = true;
    }

    const onSearch = (search) => {
        logFilter.value = search;
    }

    const onLevelChange = (level) => {
        logLevel.value = level;
    }

    const showDescription = (event) => {
        selectedTask.value = event
        isShowDescriptionOpen.value = true;
        isDrawerOpen.value = true;
    }

    const linkDatas = (task) => {
        const data = {id: task.flowId, namespace: task.namespace}
        if (props.execution) {
            const taskrun = props.execution.taskRunList.find(r => r.taskId == task.id && r.outputs.executionId)
            if (taskrun) {
                data.executionId = taskrun.outputs.executionId
            }
        }
        return data
    }

    // Expose method to be triggered by parents
    defineExpose({
        generateGraph
    })
</script>

<template>
    <div ref="vueFlow" class="vueflow">
        <slot name="top-bar" />
        <VueFlow
            v-model="elements"
            :default-marker-color="cssVariable('--bs-cyan')"
            :fit-view-on-init="true"
            :nodes-draggable="false"
            :nodes-connectable="false"
            :elevate-nodes-on-select="false"
            :elevate-edges-on-select="false"
        >
            <Background />
            <template #node-cluster="props">
                <ClusterNode
                    v-bind="props"
                    @collapse="collapseCluster($event, true)"
                />
            </template>

            <template #node-dot="props">
                <DotNode v-bind="props" />
            </template>

            <template #node-task="props">
                <TaskNode
                    v-bind="props"
                    @edit="onEditTask($event)"
                    @delete="onDelete"
                    @expand="expand($event)"
                    @openLink="openFlow($event)"
                    @showLogs="showLogs($event)"
                    @showDescription="showDescription($event)"
                    @mouseover="onMouseOver($event)"
                    @mouseleave="onMouseLeave()"
                    @addError="onAddFlowableError($event)"
                />
            </template>

            <template #node-trigger="props">
                <TriggerNode
                    v-bind="props"
                    @delete="onDelete"
                    :is-read-only="isReadOnly"
                    :is-allowed-edit="isAllowedEdit"
                    @edit="onEditTask($event)"
                />
            </template>

            <template #node-collapsedcluster="props">
                <CollapsedClusterNode
                    v-bind="props"
                    @expand="expand($event)"
                />
            </template>

            <template #edge-edge="props">
                <EdgeNode
                    v-bind="props"
                    :yaml-source="source"
                    :flowables-ids="flowables()"
                    @addTask="onCreateNewTask($event)"
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

        <!-- Drawer to create/add task -->
        <task-edit
            component="div"
            is-hidden
            :emit-task-only="true"
            class="node-action"
            :section="SECTIONS.TASKS"
            :task="taskObject"
            :flow-id="flowId"
            size="small"
            :namespace="namespace"
            :revision="execution ? execution.flowRevision : undefined"
            :emit-only="true"
            @update:task="confirmEdit($event)"
            @close="closeEdit()"
            ref="taskEdit"
        />

        <!--    Drawer to task informations (logs, description, ..)   -->
        <!--    Assuming selectedTask is always the id and the required data for the opened drawer    -->
        <el-drawer
            v-if="isDrawerOpen && selectedTask"
            v-model="isDrawerOpen"
            :title="`Task ${selectedTask.id}`"
            destroy-on-close
            size=""
            :append-to-body="true"
        >
            <div v-if="isShowLogsOpen">
                <collapse>
                    <el-form-item>
                        <search-field :router="false" @search="onSearch" class="me-2" />
                    </el-form-item>
                    <el-form-item>
                        <log-level-selector :value="logLevel" @update:model-value="onLevelChange" />
                    </el-form-item>
                </collapse>
                <log-list
                    v-for="taskRun in selectedTask.taskRuns"
                    :key="taskRun.id"
                    :execution="execution"
                    :task-run-id="taskRun.id"
                    :filter="logFilter"
                    :exclude-metas="['namespace', 'flowId', 'taskId', 'executionId']"
                    :level="logLevel"
                    @follow="forwardEvent('follow', $event)"
                    :hide-others-on-select="true"
                />
            </div>
            <div v-if="isShowDescriptionOpen">
                <markdown class="markdown-tooltip" :source="selectedTask.description" />
            </div>
        </el-drawer>
    </div>
</template>


<style scoped lang="scss">
    @use "@kestra-io/ui-libs/dist/style.css";
    @import "@kestra-io/ui-libs/dist/variables.scss";

    .vueflow {
        height: 100%;
        width: 100%;
        position: relative;
    }
</style>
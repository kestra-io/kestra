<template>
    <el-card shadow="never">
        <div id="container-topology" class="container-topology" />
        <TeleportContainer />
    </el-card>
</template>

<script>
    import TreeNode from "./TreeNode.vue";
    import ArrowCollapseRight from "vue-material-design-icons/ArrowCollapseRight";
    import ArrowCollapseDown from "vue-material-design-icons/ArrowCollapseDown";
    import Kicon from "../Kicon"
    import {shallowRef} from "vue";
    import {Graph} from "@antv/x6";
    import {DagreLayout} from "@antv/layout";
    import {register, getTeleport} from "@antv/x6-vue-shape";
    import {Scroller} from "@antv/x6-plugin-scroller";
    import Cytoscape from "../layout/Cytoscape"

    // Register a custom node
    register({
        shape: "tree_node",
        width: 200,
        height: 53,
        component: TreeNode,
    });
    const TeleportContainer = getTeleport();

    export default {
        components: {
            ArrowCollapseDown,
            ArrowCollapseRight,
            Kicon,
            TeleportContainer
        },
        props: {
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
        },
        emits: ["follow"],
        data() {
            return {
                orientation: true,
                icon: {
                    ArrowCollapseDown: shallowRef(ArrowCollapseDown),
                    ArrowCollapseRight: shallowRef(ArrowCollapseRight),
                },
                treeNodes: {}
            };
        },
        created() {
            this.orientation = localStorage.getItem("topology-orientation") === "1";
        },
        mounted() {
            this.generateGraph();
        },
        methods: {
            getEdgeLabel(relation) {
                let label = "";

                if (relation.relationType && relation.relationType !== "SEQUENTIAL") {
                    label = relation.relationType.toLowerCase();
                    if (relation.value) {
                        label += ` : ${relation.value}`;
                    }
                }
                return label;
            },
            getClusters() {
                const clusters = {};
                const nodes = [];

                for (let cluster of (this.flowGraph.clusters || [])) {
                    for (let nodeUid of cluster.nodes) {
                        clusters[nodeUid] = cluster.cluster;
                    }

                    nodes.push({
                        data: {
                            id: cluster.cluster.uid,
                            label: cluster.cluster.task.id,
                            type: "cluster",
                            parent: cluster.parents ? cluster.parents[cluster.parents.length - 1] : undefined
                        },
                    })
                }

                return {nodes: nodes, clusters: clusters};
            },
            getNodes: function (clusters) {
                const nodes = [];

                // add nodes
                for (const node of this.flowGraph.nodes) {
                    const isEdge = this.isEdgeNode(node);
                    const cluster = clusters[node.uid];

                    const nodeData = {
                        shape: isEdge ? "tree_node" : "circle",
                        width: isEdge ? 200 : 15,
                        height: isEdge ? 50 : 15,
                        // data: {
                        id: node.uid,
                        label: isEdge ? node.task.id : undefined,
                        type: isEdge ? "task" : "dot",
                        cls: node.type,
                        parent: cluster ? cluster.uid : undefined,
                        relationType: node.relationType,
                        // },
                        n: node,
                        namespace: this.namespace,
                        flowId: this.flowId,
                        execution: this.execution,

                        attrs: isEdge ? {} : {
                            body: {
                                fill: "#EAF5FC",
                            },
                        },
                    };
                    nodes.push(nodeData);
                }

                return nodes;
            },
            isRenderer(id) {
                return document.getElementById(id) !== null;
            },
            getEdges() {
                const edges = []
                for (const edge of this.flowGraph.edges) {
                    edges.push({
                        // data: {
                        id: edge.source + "|" + edge.target,
                        source: edge.source,
                        target: edge.target,
                        label: this.getEdgeLabel(edge.relation),
                        relationType: edge.relation && edge.relation.relationType ? edge.relation.relationType : undefined,
                        // },
                        selectable: true,
                    })
                }

                return edges;
            },
            generateGraph() {
                // init X6 graph
                const graphX6 = new Graph({
                    container: document.getElementById("container-topology"),
                    background: {
                        color: "#FFF",
                    },
                    interacting: false,
                    connecting: {
                        // router: "orth",
                        sourceAnchor: {
                            name: "right",
                        },
                        targetAnchor: {
                            name: "left",
                        },
                        connectionPoint: "anchor",
                    },
                });

                graphX6.use(
                    new Scroller({
                        enabled: true,
                        // autoResize: false 
                    })
                );

                // get nodes & edges
                const {clusters} = this.getClusters();
                // const {clusters} = this.getClusters();
                const taskNodes = this.getNodes(clusters);
                // const nodes = [...clustersNodes, ...taskNodes];
                const nodes = [...taskNodes];
                const edges = this.getEdges();

                // init
                const modelData = {
                    edges,
                    nodes,
                };

                // layout
                const dagreLayout = new DagreLayout({
                    type: "dagre",
                    rankdir: "LR",
                    controlPoints: true,
                    ranksep: 80,
                })
                const model = dagreLayout.layout(modelData)
                graphX6.fromJSON(model);

                graphX6.zoomToFit({
                    minScale: 0.2,
                    maxScale: 1.5,
                    padding: 44
                });

                console.log("this.flowGraph.clusters >>>>", this.flowGraph.clusters);
                // Box
                Array.isArray(this.flowGraph.clusters) && this.flowGraph.clusters.forEach(({nodes}, index) => {
                    const nodeInstances = [];
                    Array.isArray(nodes) && nodes.forEach((nodeId) => {
                        const nodeInstance = graphX6.getCellById(nodeId);
                        if (nodeInstance) {
                            nodeInstances.push(nodeInstance);
                        }
                    });
                    const boxFrame = graphX6.getCellsBBox(nodeInstances);
                    const layerIndex = this.flowGraph.clusters.length - index + 1;
                    const offset = layerIndex * 15;
                    graphX6.addNode({
                        x: boxFrame.x - offset,
                        y: boxFrame.y - offset,
                        width: boxFrame.width + offset * 2,
                        height: boxFrame.height + offset * 2,
                        zIndex: -1,

                        attrs: {
                            body: {
                                fill: "#FCFBFC",
                                stroke: "#000",
                                strokeDasharray: 10,
                                strokeWidth: 1,
                            },
                        },
                    })
                });
            },
            isEdgeNode(node) {
                return node.task !== undefined && (node.type === "io.kestra.core.models.hierarchies.GraphTask" || node.type === "io.kestra.core.models.hierarchies.GraphClusterRoot")
            },
        },
        beforeUnmount() {
        }
    };
</script>
<style scoped lang="scss">
.container-topology {
    height: calc(100vh - 360px);
}
</style>

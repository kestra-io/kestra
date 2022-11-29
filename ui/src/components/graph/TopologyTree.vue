<template>
    <div>
        <cytoscape ref="cytoscape">
            <template #btn>
                <el-tooltip :content="$t('topology-graph.graph-orientation')">
                    <el-button :icon="orientation ? icon.ArrowCollapseDown : icon.ArrowCollapseRight" size="small" @click="toggleOrientation" />
                </el-tooltip>
            </template>
        </cytoscape>

        <div v-for="(node, id) in treeNodes" :key="id">
            <teleport :to="'#' + id" v-if="isRenderer(id)">
                <tree-node
                    :n="node"
                    :namespace="namespace"
                    :flow-id="flowId"
                    :execution="execution"
                    @follow="forwardEvent('follow', $event)"
                />
            </teleport>
        </div>
    </div>
</template>

<script>
    import * as cytoscape from "cytoscape";
    import * as dagre from "cytoscape-dagre";
    import * as cytoscapeDomNode  from "cytoscape-dom-node";

    import TreeNode from "./TreeNode";
    import ArrowCollapseRight from "vue-material-design-icons/ArrowCollapseRight";
    import ArrowCollapseDown from "vue-material-design-icons/ArrowCollapseDown";
    import Kicon from "../Kicon"
    import Cytoscape from "../layout/Cytoscape"
    import {shallowRef} from "vue";

    export default {
        components: {
            ArrowCollapseDown,
            ArrowCollapseRight,
            Kicon,
            Cytoscape,
            TreeNode
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
        cy: undefined,
        watch: {
            flowGraph() {
                this.generateGraph();
            }
        },
        created() {
            this.orientation = localStorage.getItem("topology-orientation") === "1";
        },
        mounted: function () {
            this.generateGraph();
        },
        methods: {
            forwardEvent(type, event) {
                this.$emit(type, event);
            },
            toggleOrientation() {
                this.orientation = !this.orientation;
                localStorage.setItem(
                    "topology-orientation",
                    this.orientation ? 1 : 0
                );
                this.generateGraph();
            },
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
                        data: {
                            id: node.uid,
                            label: isEdge ? node.task.id : undefined,
                            type: isEdge ? "task" : "dot",
                            cls: node.type,
                            parent: cluster ? cluster.uid : undefined,
                            relationType: node.relationType,
                        },
                    };

                    if (this.isEdgeNode(node)) {
                        let div = document.createElement("div");
                        div.className = "node-binder"
                        div.id = `node-${node.uid.hashCode()}`

                        this.treeNodes[div.id] = node;

                        nodeData.data.dom = div
                    }

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
                        data: {
                            id: edge.source + "|" + edge.target,
                            source: edge.source,
                            target: edge.target,
                            label: this.getEdgeLabel(edge.relation),
                            relationType: edge.relation && edge.relation.relationType ? edge.relation.relationType : undefined
                        },
                        selectable: true,
                    })
                }

                return edges;
            },
            getStyles() {
                const darkTheme = document.getElementsByTagName("html")[0].className.indexOf("dark") >= 0;

                return [
                    {
                        selector: "*",
                        style: {
                            "events": "no",
                            "overlay-color": "#FBD10B",
                            "overlay-padding": "2"
                        }
                    },
                    {
                        selector: "node[type = \"task\"]",
                        style: {
                            "shape": "rectangle",
                            "width": 202,
                            "height": 50,
                            "border-color": darkTheme ? "#292e40" : "#8997bd",
                            "border-width": 1,
                            "events": "yes"
                        }
                    },
                    {
                        selector: "node[type = \"dot\"]",
                        style: {
                            "width": 10,
                            "height": 10,
                            "background-color": "#12a4ed",
                        }
                    },
                    {
                        selector: "node[type = \"cluster\"]",
                        style: {
                            label: "data(label)",
                            "background-color": "#12a4ed",
                            "background-opacity": 0.05,
                            "border-color": "#12a4ed",
                            "color": "#12a4ed",
                            "text-margin-y": 20,
                            "padding": "25px",
                        }
                    },
                    {
                        selector: "edge",
                        style: {
                            "font-size": "13px",
                            "width": 1,
                            "target-arrow-shape": "vee",
                            "line-color": "#12a4ed",
                            "target-arrow-color": "#12a4ed",
                            "source-endpoint": this.orientation ? undefined : "50% 0%",
                            "target-endpoint": this.orientation ? undefined : "-50% 0%",
                            "curve-style": "straight",
                            "events": "yes",
                        }
                    },
                    {
                        selector: "edge:selected",
                        style: {
                            "line-color": "#FBD10B",
                            "target-arrow-color": "#FBD10B",
                        }
                    },
                    {
                        selector: "node:selected",
                        style: {
                            "border-color": "#FBD10B",
                        }
                    },
                    {
                        selector: "node[type = \"dot\"]:selected",
                        style: {
                            "background-color": "#FBD10B",
                        }
                    },
                    {
                        selector: "edge[label]",
                        style: {
                            "label": "data(label)",
                            "color": "#8997bd",
                            "line-height": 2,
                            "source-text-offset": "100",
                            "target-text-offset": "100",
                            "edge-text-rotation": "autorotate",
                            "text-margin-y": "-10px",
                        }
                    },
                    {
                        selector: "edge[relationType = \"ERROR\"]",
                        style: {
                            "line-color": "#f5325c",
                            "color": "#f5325c",
                            "target-arrow-color": "#f5325c",
                        }
                    },
                    {
                        selector: "edge[relationType = \"DYNAMIC\"]",
                        style: {
                            "line-color": "#6d81f5",
                            "color": "#6d81f5",
                            "target-arrow-color": "#6d81f5",
                        }
                    },
                    {
                        selector: "edge[relationType = \"CHOICE\"]",
                        style: {
                            "line-color": "#ff8500",
                            "color": "#ff8500",
                            "target-arrow-color": "#ff8500",
                        }
                    },
                    {
                        selector: "core",
                        css: {
                            "active-bg-size": 0,
                            "selection-box-border-width": 0,
                            "selection-box-color": "#f5325c",
                            "active-bg-color" : "#f5325c"
                        }
                    }
                ];
            },
            onReady(cy) {
                cy.autolock(true);
                this.$refs.cytoscape.setReady(true)
            },
            generateGraph() {
                this.$refs.cytoscape.setReady(false)

                // plugins
                cytoscape.use(dagre);
                if (typeof cytoscape("core", "domNode") == "undefined") {
                    cytoscape.use(cytoscapeDomNode);
                }

                // get nodes & edges
                const {nodes: clustersNodes, clusters} = this.getClusters();
                const taskNodes = this.getNodes(clusters);
                const nodes = [...clustersNodes, ...taskNodes];
                const edges = this.getEdges();

                // init
                const self = this;
                this.cy = cytoscape({
                    container: document.getElementById(this.$refs.cytoscape.uuid),
                    ready: function() {
                        self.onReady(this);
                    },
                    elements: {
                        nodes: nodes,
                        edges: edges
                    },
                    style: this.getStyles(),
                    layout: {
                        name: "dagre",
                        rankDir: this.orientation ? "TB" : "LR",
                    },
                    pixelRatio: 1,
                    minZoom: 0.2,
                    maxZoom: 2
                });

                this.$refs.cytoscape.instance(this.cy);

                this.cy.domNode();

                this.cy.layout({
                    name: "dagre",
                    animate: false,
                    fit: true,
                    padding: 50,
                    spacingFactor: 1.4,
                }).run();

                this.cy.nodes().on("tapdragover", (e) => {
                    e.target.select();
                    e.target.predecessors().select();
                    e.target.successors().select();
                });

                this.cy.nodes().on("tapdragout tapstart", (e) => {
                    e.target.unselect();
                    e.target.predecessors().unselect();
                    e.target.successors().unselect();
                });
            },
            isEdgeNode(node) {
                return node.task !== undefined && (node.type === "io.kestra.core.models.hierarchies.GraphTask" || node.type === "io.kestra.core.models.hierarchies.GraphClusterRoot")
            },
        },
        beforeUnmount() {
            this.cy && this.cy.destroy()
        }
    };
</script>

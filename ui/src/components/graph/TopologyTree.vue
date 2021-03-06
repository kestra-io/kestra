<template>
    <div>
        <div class="d-flex top">
            <div>
                <b-btn-group>
                    <b-btn size="sm" @click="toggleOrientation">
                        <kicon placement="bottomright" :tooltip="$t('topology-graph.graph-orientation')">
                            <arrow-collapse-down v-if="orientation" />
                            <arrow-collapse-right v-else />
                        </kicon>
                    </b-btn>
                    <b-btn size="sm" @click="setAction('in')">
                        <kicon placement="bottom" :tooltip="$t('topology-graph.zoom-in')">
                            <magnify-plus />
                        </kicon>
                    </b-btn>
                    <b-btn size="sm" @click="setAction('out')">
                        <kicon placement="bottom" :tooltip="$t('topology-graph.zoom-out')">
                            <magnify-minus />
                        </kicon>
                    </b-btn>
                    <b-btn size="sm" @click="setAction('reset')" id="zoom-reset">
                        <kicon placement="bottom" :tooltip="$t('topology-graph.zoom-reset')">
                            <arrow-collapse-all />
                        </kicon>
                    </b-btn>
                    <b-btn size="sm" @click="setAction('fit')" id="zoom-fit">
                        <kicon placement="bottom" :tooltip="$t('topology-graph.zoom-fit')">
                            <fit-to-page />
                        </kicon>
                    </b-btn>
                </b-btn-group>
            </div>
        </div>

        <div :class="{hide: !ready}" class="graph-wrapper" :id="uuid" ref="wrapper" />

        <div v-if="ready">
            <div v-for="node in treeTaskNode" :key="node.uid">
                <MountingPortal
                    :mount-to="`#node-${node.uid.hashCode()}`"
                    append
                    :name="`source-${node.uid.hashCode()}`"
                >
                    <tree-node
                        :n="node"
                        :namespace="namespace"
                        :flow-id="flowId"
                        :execution="execution"
                    />
                </MountingPortal>
            </div>
        </div>
    </div>
</template>
<script>
    import * as cytoscape from "cytoscape";
    import * as dagre from "cytoscape-dagre";
    import * as nodeHtmlLabel  from "cytoscape-node-html-label";
    import {MountingPortal} from "portal-vue"

    import TreeNode from "./TreeNode";
    import ArrowCollapseRight from "vue-material-design-icons/ArrowCollapseRight";
    import ArrowCollapseDown from "vue-material-design-icons/ArrowCollapseDown";
    import MagnifyPlus from "vue-material-design-icons/MagnifyPlus";
    import MagnifyMinus from "vue-material-design-icons/MagnifyMinus";
    import ArrowCollapseAll from "vue-material-design-icons/ArrowCollapseAll";
    import FitToPage from "vue-material-design-icons/FitToPage";
    import Utils from "../../utils/utils";
    import Kicon from "../Kicon"

    export default {
        components: {
            MountingPortal,
            TreeNode,
            ArrowCollapseDown,
            ArrowCollapseRight,
            MagnifyPlus,
            MagnifyMinus,
            ArrowCollapseAll,
            FitToPage,
            Kicon
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
        data() {
            return {
                uuid: Utils.uid(),
                ready: false,
                orientation: true,
                zoom: undefined,
                zoomFactor: 1,
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
            setAction(action) {
                if (action === "in") {
                    if (this.cy.zoom() <= 1.7) {
                        this.cy.zoom(this.cy.zoom() + 0.2);
                    }
                } else if (action === "out") {
                    if (this.cy.zoom() >= 0.3) {
                        this.cy.zoom(this.cy.zoom() - 0.2);
                    }
                } else if (action === "reset") {
                    this.cy.zoom(1);
                } else if (action === "fit") {
                    this.cy.fit(null, 50)
                }
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
            getNodes(clusters) {
                const nodes = [];
                // add nodes
                for (const node of this.flowGraph.nodes) {
                    const isEdge = this.isEdgeNode(node);
                    const cluster = clusters[node.uid];

                    nodes.push({
                        data: {
                            id: node.uid,
                            label: isEdge ? node.task.id : undefined,
                            type: isEdge ? "task" : "dot",
                            cls: node.type,
                            parent: cluster ? cluster.uid : undefined,
                            relationType: node.relationType
                        },
                    });
                }

                return nodes;
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
                            // "label": "data(label)",
                            "shape": "rectangle",
                            "width": 202,
                            "height": 50,
                            "border-width": 0,
                            "background-color": "#FFF",
                            "background-opacity": 0,
                            "text-halign": "center",
                            "text-valign": "center",
                        }
                    },
                    {
                        selector: "node[type = \"dot\"]",
                        style: {
                            "width": 10,
                            "height": 10,
                            "background-color": "#1AA5DE",
                        }
                    },
                    {
                        selector: "node[type = \"cluster\"]",
                        style: {
                            label: "data(label)",
                            "background-color": "#1AA5DE",
                            "background-opacity": 0.05,
                            "border-color": "#1AA5DE",
                            "color": "#1AA5DE",
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
                            "line-color": "#1AA5DE",
                            "target-arrow-color": "#1AA5DE",
                            "source-endpoint": this.orientation ? undefined : "50% 0%",
                            "target-endpoint": this.orientation ? undefined : "-50% 0%",
                            "curve-style": "straight",
                            "events": "yes",
                        }
                    },
                    {
                        selector: "edge:selected",
                        style: {
                            "line-style": "dashed",
                            "line-dash-pattern": "3 3",
                        }
                    },
                    {
                        selector: "edge[label]",
                        style: {
                            "label": "data(label)",
                            "color": "#666",
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
                            "line-color": "#dc3545",
                            "color": "#dc3545",
                            "target-arrow-color": "#dc3545",
                        }
                    },
                    {
                        selector: "edge[relationType = \"DYNAMIC\"]",
                        style: {
                            "line-color": "#6610f2",
                            "color": "#6610f2",
                            "target-arrow-color": "#6610f2",
                        }
                    },
                    {
                        selector: "edge[relationType = \"CHOICE\"]",
                        style: {
                            "line-color": "#fd7e14",
                            "color": "#fd7e14",
                            "target-arrow-color": "#fd7e14",
                        }
                    },
                    {
                        selector: "core",
                        css: {
                            "active-bg-size": 0,
                            "selection-box-border-width": 0,
                            "selection-box-color": "#FF0000",
                            "active-bg-color" : "#FF0000"
                        }
                    }
                ];
            },
            onReady(cy) {
                cy.autolock(true);
                cy.nodeHtmlLabel([
                    {
                        query: "node[type = \"task\"]",
                        tpl: d => {
                            return `<div class="node-binder" id="node-${d.id.hashCode()}" />`;
                        }
                    }
                ], {
                    enablePointerEvents: true
                });

                this.bindNodes();
            },
            generateGraph() {
                this.ready = false;

                // plugins
                try {
                    cytoscape.use(dagre);
                    cytoscape.use(nodeHtmlLabel);
                    // eslint-disable-next-line no-empty
                } catch (ignored) {}

                // get nodes & edges
                const {nodes: clustersNodes, clusters} = this.getClusters();
                const taskNodes = this.getNodes(clusters);
                const nodes = [...clustersNodes, ...taskNodes];
                const edges = this.getEdges();

                // init
                const self = this;
                this.cy = cytoscape({
                    container: document.getElementById(this.$refs.wrapper.id),
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
                        animate: false,
                        fit: true,
                        padding: 50,
                        spacingFactor: 1.4,
                    },
                    pixelRatio: 1,
                    minZoom: 0.2,
                    maxZoom: 2
                });
            },
            bindNodes() {
                let ready = true;
                for (const node of this.treeTaskNode) {
                    if (
                        // !this.$refs[node.uid] ||
                        // !this.$refs[node.uid].length ||
                        !this.$el.querySelector(`#node-${node.uid.hashCode()}`)
                    ) {
                        ready = false;
                    }
                }

                if (ready) {
                    this.ready = true;
                } else {
                    setTimeout(this.bindNodes, 50);
                }
            },
            isEdgeNode(node) {
                return node.task !== undefined && (node.type === "io.kestra.core.models.hierarchies.GraphTask" || node.type === "io.kestra.core.models.hierarchies.GraphClusterRoot")
            },
        },
        computed: {
            treeTaskNode() {
                return this.flowGraph.nodes.filter(n => {
                    return this.isEdgeNode(n);
                })
            },
        },
        destroyed() {
            this.ready = false;
        }
    };
</script>
<style lang="scss" scoped>
@import "../../styles/variable";

.graph-wrapper {
    height: calc(100vh - 260px);
}

.hidden {
    opacity: 0;
    height:0;
    overflow: hidden;
}

.hide {
    opacity: 0;
}

.top {
    background-color: $breadcrumb-bg;
    .breadcrumb {
        margin-bottom: 0;
        border-radius: 0;
        border: 0;
    }
    > div {
        margin: 6px;
    }
}
</style>

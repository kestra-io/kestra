<template>
    <div>
        <div class="d-flex top">
            <div>
                <b-tooltip placement="left" target="graph-orientation">
                    {{ $t('graph orientation') }}
                </b-tooltip>
                <b-btn size="sm" @click="toggleOrientation" id="graph-orientation">
                    <arrow-collapse-down v-if="orientation" />
                    <arrow-collapse-right v-else />
                </b-btn>
                <b-btn size="sm" @click="setZoom('in')">
                    <magnify-plus />
                </b-btn>
                <b-btn size="sm" @click="setZoom('out')">
                    <magnify-minus />
                </b-btn>
                <b-btn size="sm" @click="setZoom('reset')">
                    <arrow-collapse-all />
                </b-btn>
            </div>
        </div>

        <div :class="{hide: !ready}" class="wrapper" ref="wrapper" />
        <div class="hidden">
            <tree-node
                @onFilterGroup="onFilterGroup"
                :ref="node.uid"
                v-for="node in treeTaskNode"
                :key="node.uid"
                :n="node"
                :namespace="namespace"
                :flow-id="flowId"
                :is-flow="isFlow"
            />
        </div>
    </div>
</template>
<script>
    import {debounce} from "throttle-debounce";

    const dagreD3 = require("dagre-d3");
    import TreeNode from "./TreeNode";
    import * as d3 from "d3";
    import ArrowCollapseRight from "vue-material-design-icons/ArrowCollapseRight";
    import ArrowCollapseDown from "vue-material-design-icons/ArrowCollapseDown";
    import MagnifyPlus from "vue-material-design-icons/MagnifyPlus";
    import MagnifyMinus from "vue-material-design-icons/MagnifyMinus";
    import ArrowCollapseAll from "vue-material-design-icons/ArrowCollapseAll";

    export default {
        components: {
            TreeNode,
            ArrowCollapseDown,
            ArrowCollapseRight,
            MagnifyPlus,
            MagnifyMinus,
            ArrowCollapseAll
        },
        props: {
            flowGraph: {
                type: Object,
                required: true
            },
            label: {
                type: Function,
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
            isFlow: {
                type: Boolean,
                default: false
            }
        },
        data() {
            return {
                ready: false,
                filterGroup: undefined,
                orientation: true,
                zoom: undefined,
                resizeHandler: undefined,
                zoomFactor: 1,
                lastX: 50,
                lastY: 50,
            };
        },
        watch: {
            flowGraph() {
                this.generateGraph();
            },
            $route() {
                if (this.$route.query.filter !== this.filterGroup) {
                    this.filterGroup = this.$route.query.filter;
                    this.generateGraph();
                }
            }
        },
        created() {
            this.orientation = localStorage.getItem("topology-orientation") === "1";
            if (this.$route.query.filter) {
                this.filterGroup = this.$route.query.filter;
            }
            this.resizeHandler = debounce(500, () => {
                this.generateGraph()
            })

            window.addEventListener("resize", this.resizeHandler);
        },
        mounted() {
            this.generateGraph();
        },
        methods: {
            setZoom(direction) {
                if (direction === "in") {
                    if (this.zoomFactor <= 1.7) {
                        this.zoomFactor += 0.2
                        this.generateGraph()
                    }
                } else if (direction === "out") {
                    if (this.zoomFactor >= 0.3) {
                        this.zoomFactor -= 0.2
                        this.generateGraph()
                    }
                } else if (direction === "reset") {
                    this.zoomFactor = 1
                    this.lastX = 50
                    this.lastY = 50
                    this.generateGraph()
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
            getOptions(relation) {
                const edgeOption = {};
                if (relation.relationType && relation.relationType !== "SEQUENTIAL") {
                    edgeOption.label = relation.relationType.toLowerCase();
                    if (relation.value) {
                        edgeOption.label += ` : ${relation.value}`;
                    }

                    edgeOption.class =
                        {
                            ERROR: "error-edge",
                            DYNAMIC: "dynamic-edge",
                            CHOICE: "choice-edge",
                            PARALLEL: "choice-edge"
                        }[relation.relationType] || "";
                }

                return edgeOption;
            },
            generateGraph() {
                // Create the input graph
                const arrowColor = "#ccc";
                if (this.zoom) {
                    this.zoom.on("zoom", null);
                }

                // init
                this.$refs.wrapper.innerHTML = `<svg id="svg-canvas" width="100%" style="min-height:${window.innerHeight - 290}px"/>`
                const g = new dagreD3.graphlib.Graph({
                    compound: true,
                    multigraph: true
                })
                    .setGraph({})
                    .setDefaultEdgeLabel(function() {
                        return {};
                    });

                // add nodes
                for (const node of this.flowGraph.nodes) {
                    if (this.isEdgeNode(node)) {
                        g.setNode(node.uid, {
                            labelType: "html",
                            class: "root-node",
                            label: `<div class="vector-circle-wrapper" id="node-${node.uid.hashCode()}" />`,
                            height: 20,
                            width: 20
                        });
                    } else {
                        g.setNode(node.uid, {
                            labelType: "html",
                            label: `<div class="node-binder" id="node-${node.uid.hashCode()}" />`,
                            class: node.task && node.task.disabled ? "task-disabled" : "",
                            width: 180
                        });
                    }
                }

                // add edges
                for (const edge of this.flowGraph.edges) {
                    g.setEdge(edge.source, edge.target, this.getOptions(edge.relation));
                }

                // add cluster
                for (let cluster of this.flowGraph.clusters) {
                    g.setNode(cluster.cluster.uid, {
                        label: cluster.cluster.task.id,
                        clusterLabelPos: "top",
                        width: "100%"
                    });

                    for (let nodeUid of cluster.nodes) {
                        g.setParent(nodeUid, cluster.cluster.uid);
                    }
                }

                // reset padding
                g.nodes().forEach(v => {
                    const node = g.node(v);
                    if (node) {
                        node.paddingLeft = node.paddingRight = node.paddingTop = node.paddingBottom = 0;
                    }
                });

                if (!this.orientation) {
                    g.graph().rankDir = "LR";
                }

                const render = new dagreD3.render();

                // Set up an SVG group so that we can translate the final graph.
                const svgWrapper = d3.select("#svg-canvas"),
                      svgGroup = svgWrapper.append("g");

                // Run the renderer. This is what draws the final graph.
                this.zoom = d3
                    .zoom()
                    .on("zoom", () => {
                        const t = d3.event.transform;
                        this.lastX = t.x
                        this.lastY = t.y
                        svgGroup.attr(
                            "transform",
                            `translate(${this.lastX || t.x},${this.lastY || t.y}) scale(${t.k*this.zoomFactor})`
                        );
                    })
                    .scaleExtent([1, 1]);

                // zoom
                svgWrapper.call(this.zoom);
                svgWrapper.on("dblclick.zoom", null);

                // path color
                render(d3.select("#svg-canvas g"), g);
                d3.selectAll("#svg-canvas g path").style("stroke", arrowColor);
                d3.selectAll("#svg-canvas .edgePath marker").style(
                    "fill",
                    arrowColor
                );

                const transform = d3.zoomIdentity.translate(0, 0).translate(this.lastX || 0, this.lastY || 0);
                svgWrapper.call(this.zoom.transform, transform);

                this.bindNodes();
            },
            bindNodes() {
                let ready = true;
                for (const node of this.treeTaskNode) {
                    if (
                        !this.$refs[node.uid] ||
                        !this.$refs[node.uid].length
                    ) {
                        ready = false;
                    }
                }

                if (ready) {
                    for (const node of this.treeTaskNode) {
                        this.$el
                            .querySelector(`#node-${node.uid.hashCode()}`)
                            .appendChild(this.$refs[node.uid][0].$el);
                    }
                    this.ready = true;
                } else {
                    setTimeout(this.bindNodes, 30);
                }
            },
            onFilterGroup(group) {
                if (this.$route.query.filter !== group) {
                    this.filterGroup = group;
                    this.$router.push({
                        query: {...this.$route.query, filter: group}
                    });
                    this.generateGraph();
                }
            },
            isEdgeNode(node) {
                return node.type !== "org.kestra.core.models.hierarchies.GraphTask"
            },
        },
        computed: {
            groups() {
                const groups = new Set();
                this.flowGraph.forEach(node =>
                    (node.groups || []).forEach(group => groups.add(group))
                );
                return groups;
            },
            treeTaskNode() {
                return this.flowGraph.nodes.filter(n => n.task !== undefined && n.type === "org.kestra.core.models.hierarchies.GraphTask")
            },
            clusterNode() {
                return this.flowGraph.nodes.filter(n => n.task !== undefined && n.type === "org.kestra.core.models.hierarchies.GraphCluster")
            }
        },
        destroyed() {
            this.ready = false;
            if (this.resizeHandler) {
                window.removeEventListener("resize", this.resizeHandler)
            }
        }
    };
</script>
<style lang="scss">
@import "../../styles/variable";

.clusters {
    rect {
        fill: $primary;
        opacity: 0.05;
        outline: 1px solid lighten($primary, 35%);
    }

    .label {
        fill: lighten($primary, 15%);
    }
}

text {
    font-size: $font-size-sm;
}

.node rect {
    stroke: $gray-600;
    fill: #fff;
    stroke-width: 1px;
}

.edgePath path {
    stroke: #333;
    stroke-width: 1.5px;
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
.node-binder,
.node-wrapper,
.node,
foreignObject {
    width: 180px;
    height: 48px;
}
.root-node {
    color: $gray-600;
    foreignObject {
        width: 35px;
        height: 35px;
    }
    > rect {
        stroke: transparent;
        fill: none !important;
    }
    .vector-circle-wrapper {
        background: $gray-600;
        width: 15px;
        height: 15px;
        border-radius: 50%;
        top: 10px;
        position: absolute;
        left: 10px;
    }
}

.task-disabled {
    .card-header .task-title {
        text-decoration: line-through;
    }
}

.hidden {
    opacity: 0;
    height:0px;
    overflow: hidden;
}

.error-edge {
    > path {
        stroke: $red !important;
    }
    marker path {
        fill: $red !important;
        stroke: $red !important;
    }
}
.dynamic-edge {
    > path {
        stroke: $blue !important;
    }
    marker path {
        fill: $blue !important;
        stroke: $blue !important;
    }
}

.choice-edge {
    > path {
        stroke: $green !important;
    }
    marker path {
        fill: $green !important;
        stroke: $green !important;
    }
}

.edgeLabel tspan {
    fill: $gray-600;
}


.hide {
    opacity: 0;
}

.edgeLabel text {
    font-size: 10px;
}
</style>

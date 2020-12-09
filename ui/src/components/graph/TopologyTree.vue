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
                :ref="slug(node)"
                v-for="node in filteredDataTree"
                :key="slug(node)"
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
            dataTree: {
                type: Array,
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
                filteredDataTree: undefined,
                clusterColors: [],
                resizeHandler: undefined,
                zoomFactor: 1,
                lastX: 50,
                lastY: 50,
            };
        },
        watch: {
            dataTree() {
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
            resetClusterColors() {
                this.clusterColors = "#F7F9F9#E5E7E9#D5DBDB#CCD1D1#AEB6BF#ABB2B9#FBFCFC#F2F3F4#EAEDED#E5E8E8#D6DBDF#D5D8DC".split("#")
            },
            nextClusterColor() {
                if (this.clusterColors.length === 0) {
                    this.resetClusterColors()
                }
                return `#${this.clusterColors.pop()}`
            },
            toggleOrientation() {
                this.orientation = !this.orientation;
                localStorage.setItem(
                    "topology-orientation",
                    this.orientation ? 1 : 0
                );
                this.generateGraph();
            },

            generateGraph() {
                this.resetClusterColors()
                this.filteredDataTree = this.getFilteredDataTree();

                // Create the input graph
                const arrowColor = "#ccc";
                if (this.zoom) {
                    this.zoom.on("zoom", null);
                }
                this.$refs.wrapper.innerHTML =
                    `<svg id="svg-canvas" width="100%" style="min-height:${window.innerHeight - 290}px"/>`
                const g = new dagreD3.graphlib.Graph({
                    compound: true,
                    multigraph: true
                })
                    .setGraph({})
                    .setDefaultEdgeLabel(function() {
                        return {};
                    });

                const getOptions = node => {
                    const edgeOption = {};
                    if (node.relation !== "SEQUENTIAL") {
                        edgeOption.label = node.relation.toLowerCase();
                        if (node.taskRun && node.taskRun.value) {
                            edgeOption.label += ` : ${node.taskRun.value}`;
                        }
                    }
                    edgeOption.class =
                        {
                            ERROR: "error-edge",
                            DYNAMIC: "dynamic-edge",
                            CHOICE: "choice-edge",
                            PARALLEL: "choice-edge"
                        }[node.relation] || "";
                    return edgeOption;
                };
                const clusters = {}
                for (const node of this.filteredDataTree) {
                    const cluster = node.groups ? node.groups[node.groups.length - 1] : undefined
                    if (cluster) {
                        if (!clusters[cluster]) {
                            clusters[cluster] = new Set()
                        }
                        if (node.groups && node.groups.length > 1) {
                            clusters[cluster].add(node.groups[node.groups.length - 2])
                        }
                    }
                }
                // const ancestors = {}
                // for (const node of this.filteredDataTree) {

                // }
                console.log("--- >start")
                for (const node of this.filteredDataTree) {

                    const options = getOptions(node)
                    g.setEdge(this.slug(node), this.slug(node), options);
                    const group = node.groups && node.groups.length ? node.groups[node.groups.length - 1] : undefined
                    console.log("id", node.task.id, "group",  group)
                    // console.log(node.task.id,"<-", node.task.id)
                    // console.log("parent", node.task)
                    // console.log("child", child.task)
                }
                for (const node of this.filteredDataTree) {
                    const slug = this.slug(node);
                    const cluster = node.groups ? node.groups[node.groups.length - 1] : undefined
                    g.setParent(slug, cluster)
                    g.setNode(slug, {
                        labelType: "html",
                        label: `<div class="node-binder" id="node-${slug}"/>`,
                        class: node.task.disabled ? "task-disabled" : ""
                    });
                }
                for (let cluster in clusters) {
                    for (let parent of clusters[cluster]) {
                        g.setParent(cluster, parent)
                    }
                    g.setNode(cluster, {label: cluster, clusterLabelPos: "top", style: `fill: ${this.nextClusterColor()}`});
                }

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
                svgWrapper.call(this.zoom);
                svgWrapper.on("dblclick.zoom", null);

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
                for (const node of this.filteredDataTree) {
                    if (
                        !this.$refs[this.slug(node)] ||
                        !this.$refs[this.slug(node)].length
                    ) {
                        ready = false;
                    }
                }
                if (ready) {
                    for (const node of this.filteredDataTree) {
                        this.$el
                            .querySelector(`#node-${this.slug(node)}`)
                            .appendChild(this.$refs[this.slug(node)][0].$el);
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
            slug(node) {
                const hash =
                    node.task.id +
                    (node.taskRun && node.taskRun.value
                        ? "-" + node.taskRun.value
                        : "");
                return hash.hashCode();
            },
            getFilteredDataTree() {
                return this.dataTree
            }
        },
        computed: {
            groups() {
                const groups = new Set();
                this.dataTree.forEach(node =>
                    (node.groups || []).forEach(group => groups.add(group))
                );
                return groups;
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
.clusters rect {
    fill: #00ffd0;
    stroke: #999;
    stroke-width: 1.5px;
}

text {
    font-weight: 300;
    font-family: "Helvetica Neue", Helvetica, Arial, sans-serf;
    font-size: 14px;
}

.node rect {
    stroke: #999;
    fill: #fff;
    stroke-width: 1.5px;
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
    width: 250px;
    height: 55px;
}
.root-node {
    text-align: center;
    font-size: 2.2em;
    color: $gray-600;
    foreignObject {
        width: 50px;
    }
    > rect {
        stroke: white;
        fill: none !important;
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

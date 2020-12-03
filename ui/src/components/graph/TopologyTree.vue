<template>
    <div>
        <div class="d-flex top">
            <b-breadcrumb class="flex-grow-1">
                <b-breadcrumb-item
                    :class="{'font-weight-bold': filterGroup === undefined}"
                    @click="onFilterGroup(undefined)"
                    text="root"
                />
                <b-breadcrumb-item
                    :class="{'font-weight-bold': group === filterGroup}"
                    v-for="group in groups"
                    @click="onFilterGroup(group)"
                    :text="group"
                    :key="group"
                />
            </b-breadcrumb>
            <div>
                <b-tooltip placement="left" target="graph-orientation">
                    {{ $t('graph orientation') }}
                </b-tooltip>
                <b-btn size="sm" @click="toggleOrientation" id="graph-orientation">
                    <arrow-collapse-down v-if="orientation" />
                    <arrow-collapse-right v-else />
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
                :is-flow="isFlow"
            />
            <tree-node
                :ref="`node-${slug(virtualRootNode)}`"
                v-if="virtualRootNode"
                :n="virtualRootNode"
                :is-flow="isFlow"
            />
        </div>

        <div ref="vector-circle">
            <vector-circle v-if="!virtualRootNode" title />
        </div>
    </div>
</template>
<script>
    const dagreD3 = require("dagre-d3");
    import TreeNode from "./TreeNode";
    import * as d3 from "d3";
    import ArrowCollapseRight from "vue-material-design-icons/ArrowCollapseRight";
    import ArrowCollapseDown from "vue-material-design-icons/ArrowCollapseDown";
    import VectorCircle from "vue-material-design-icons/Circle";
    const parentHash = node => {
        if (node.parent) {
            const parent = node.parent[0];
            return nodeHash(parent)
        } else {
            return undefined;
        }
    };
    const nodeHash = node => {
        return (node.id + (node.value ? "-" + node.value : "")).hashCode();
    }

    export default {
        components: {
            TreeNode,
            ArrowCollapseDown,
            ArrowCollapseRight,
            VectorCircle
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
                virtualRootNode: undefined
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
            this.dataTree.forEach(node => {
                node.children = this.children(node)
            })
        },
        mounted() {
            this.generateGraph();
        },
        methods: {
            toggleOrientation() {
                this.orientation = !this.orientation;
                localStorage.setItem(
                    "topology-orientation",
                    this.orientation ? 1 : 0
                );
                this.generateGraph();
            },
            getVirtualRootNode() {
                return this.filterGroup
                    ? {
                        task: {
                            id: this.filterGroup
                        }
                    }
                    : undefined;
            },
            generateGraph() {
                this.filteredDataTree = this.getFilteredDataTree();
                this.virtualRootNode = this.getVirtualRootNode();

                // Create the input graph
                const arrowColor = "#ccc";
                if (this.zoom) {
                    this.zoom.on("zoom", null);
                }
                this.$refs.wrapper.innerHTML =
                    "<svg id=\"svg-canvas\" width=\"100%\" style=\"min-height:800px\"/>";
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
                const ancestorsHashes = new Set(
                    this.filteredDataTree.filter(e => e.parent).map(e => nodeHash(e.parent[0]))
                );
                const virtualRootId = this.getVirtualRootNode() && this.getVirtualRootNode().task.id
                const groupDisabled = this.isGroupDisabled(virtualRootId);

                for (const node of this.filteredDataTree) {
                    const slug = this.slug(node);
                    const hash = parentHash(node);
                    g.setNode(slug, {
                        labelType: "html",
                        label: `<div class="node-binder" id="node-${slug}"/>`,
                        class: groupDisabled || node.task.disabled ? "task-disabled" : ""
                    });
                    const options = getOptions(node);
                    const parentId = node.parent && node.parent[0] && node.parent[0].id
                    if (ancestorsHashes.has(hash) && ["SEQUENTIAL", "ERROR"].includes(node.relation) && virtualRootId !== parentId) {
                        g.setEdge(parentHash(node), slug, options);
                    } else {
                        g.setEdge("parent node", slug, options)
                    }
                }
                const rootNode = {
                    labelType: "html",
                    clusterLabelPos: "bottom"
                };
                if (this.filterGroup) {
                    rootNode.label = `<div class="node-binder root-node-virtual" id="node-${this.slug(
                        this.virtualRootNode
                    )}"/>`;
                    rootNode.class = groupDisabled  ? "task-disabled" : "";
                } else {
                    rootNode.class = "root-node";
                    rootNode.label = "<div class=\"vector-circle-wrapper\"/>";
                    rootNode.height = 30;
                    rootNode.width = 30;
                }
                g.setNode("parent node", rootNode);
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
                        svgGroup.attr(
                            "transform",
                            `translate(${t.x},${t.y}) scale(${t.k})`
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
                const transform = d3.zoomIdentity.translate(50, 50).translate(0, 0);
                svgWrapper.call(this.zoom.transform, transform);
                this.bindNodes();
            },
            children(node) {
                const children = []
                for (let child of this.dataTree) {
                    for (let parent of (child.parent || [])) {
                        if (parent.id === node.task.id) {
                            children.push(JSON.parse(JSON.stringify(node)))
                        }
                    }
                }
                return children
            },
            virtalNodeReady() {
                if (this.virtualRootNode) {
                    const vueNode = this.$refs[
                        `node-${this.slug(this.virtualRootNode)}`
                    ];
                    return vueNode && vueNode.$el;
                } else {
                    return true;
                }
            },
            isGroupDisabled(virtualRootId) {
                if (virtualRootId === undefined) {
                    return  false;
                }

                const virtualRoot = this.dataTree.filter(node => node.task.id === virtualRootId)[0];

                if (virtualRoot.task.disabled === true) {
                    return true;
                }

                if (virtualRoot.groups === undefined) {
                    return false;
                }

                return virtualRoot.groups
                    .map(value => this.dataTree.filter(node => node.task.id === value)[0])
                    .filter(node => node.task.disabled === true)
                    .length > 0
            },
            bindNodes() {
                let ready = true;
                for (const node of this.filteredDataTree) {
                    if (
                        !this.virtalNodeReady() ||
                        !this.$refs[this.slug(node)] ||
                        !this.$refs[this.slug(node)].length ||
                        !this.$refs["vector-circle"]
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
                    if (this.virtualRootNode) {
                        this.$el
                            .querySelector(
                                `#node-${this.slug(this.virtualRootNode)}`
                            )
                            .appendChild(
                                this.$refs[
                                    `node-${this.slug(this.virtualRootNode)}`
                                ].$el
                            );
                    } else {
                        this.$el
                            .querySelector(".vector-circle-wrapper")
                            .appendChild(this.$refs["vector-circle"]);
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
                if (this.filterGroup) {
                    return this.dataTree.filter(
                        node =>
                            node.groups &&
                            node.groups[node.groups.length - 1] === this.filterGroup
                    );
                } else {
                    return this.dataTree.filter(node => !node.groups);
                }
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

.vector-circle-wrapper path {
    fill: $gray-400 !important;
}

.root-node-virtual {
    opacity: 0.5;
}
.hide {
    opacity: 0;
}

.edgeLabel text {
    font-size: 10px;
}
</style>

<template>
    <div id="streams-topology-graph">
        <div id="streams-topology-generals">
            <ul class="list-inline left-border">
                <li class="list-inline-item">
                    <strong>sub-topologies :</strong>
                    {{subTopologyCount}}
                </li>
                <li class="list-inline-item">
                    <strong>topic sources :</strong>
                    {{sourceCount}}
                </li>
                <li class="list-inline-item">
                    <strong>topic sinks :</strong>
                    {{sinkCount}}
                </li>
                <li class="list-inline-item">
                    <strong>stores :</strong>
                    {{storeCount}}
                </li>
            </ul>
        </div>
        <div id="svg-container" class="bg-white rounded box-shadow bordered pl-0 pr-0"></div>
    </div>
</template>

<script>
import * as d3 from "d3";

export default {
    name: "topology-graph",
    props: {"topology": {
        type: Object,
        required: true
    }},
    data: function() {
        return {
            subTopologyCount: 0,
            sourceCount: 0,
            sinkCount: 0,
            storeCount: 0
        };
    },
    mounted: function() {
        this.build();
    },
    watch: {
        topology: "build"
    },
    methods: {
        build() {
            if (this.topology.subTopologies !== undefined) {
                let objectTopology = this.buildTopologyGraph(this.topology);
                this.subTopologyCount = objectTopology.subTopologies.length;
                this.storeCount = objectTopology.stores().length;

                let nodes = Array.from(objectTopology.nodes().values());
                this.sourceCount = nodes.filter(n => n.type == "SOURCE").length;
                this.sinkCount = nodes.filter(n => n.type == "SINK").length;
                this.createSVG(objectTopology);
            }
        },

        buildTopologyGraph(json) {
            let topologiesLayouts = [];

            let sourceTopics = [];

            // get all source topics across all sub-topologies.
            json.subTopologies.forEach(sub => {
                sub.nodes.forEach(node => {
                    if (node.type == "SOURCE") {
                        node.topics.forEach(topic => sourceTopics.push(topic));
                    }
                });
            });

            json.globalStores.forEach(store => {
                store.source.topics.forEach(t => sourceTopics.push(t));
            });

            let subTopologies = [];
            json.subTopologies.forEach(sub => subTopologies.push(sub));
            json.globalStores.forEach(store => {
                let nodes = [];
                nodes.push(store.source);
                nodes.push(store.processor);
                subTopologies.push({ id: store.id, nodes: nodes });
            });

            subTopologies.forEach(sub => {
                let nodesKeyedByName = new Map();
                let topicNodes = new Map();
                let stores = new Map();

                sub.nodes.forEach(node => {
                    nodesKeyedByName.set(node.name, node);
                    if (node.type == "SOURCE") {
                        node.topics.forEach(topic => sourceTopics.push(topic));
                    }
                });

                sub.nodes.forEach((node, i) => {
                    let objectNode = new ProcessorNode(node);
                    objectNode.subTopology = sub.id;
                    objectNode.id = i;
                    nodesKeyedByName.set(node.name, objectNode);

                    objectNode.stores.forEach(s => {
                        let store = stores.get(s) || new Store({ name: s });
                        store.nodes.push(objectNode);
                        stores.set(s, store);
                    });

                    if (node.type == "SOURCE") {
                        node.topics.forEach(topic => {
                            let objectTopicNode =
                                topicNodes.get(topic) ||
                                new TopicNode({
                                    name: topic,
                                    subTopology: sub.id
                                });
                            objectTopicNode.successors.push(node.name);
                            topicNodes.set(topic, objectTopicNode);
                        });
                    }
                    if (node.type == "SINK") {
                        let isIntermediate = sourceTopics.includes(node.topic);
                        let objectTopicNode = new TopicNode({
                            name: node.topic,
                            isIntermediate: isIntermediate,
                            subTopology: sub.id
                        });
                        nodesKeyedByName.set(
                            objectTopicNode.name,
                            objectTopicNode
                        );
                        node.successors.push(node.topic);
                    }
                });

                let layouts = this.buildTopologyNodes(
                    nodesKeyedByName,
                    Array.from(topicNodes.values())
                );

                let levels = layouts.map(
                    (nodes, i) =>
                        new SubTopologyLevel({
                            id: i,
                            subTopology: sub.id,
                            nodes: nodes
                        })
                );
                topologiesLayouts.push(
                    new SubTopology({
                        id: sub.id,
                        levels: levels,
                        stores: Array.from(stores.values())
                    })
                );
            });

            return new Topology(topologiesLayouts);
        },

        buildTopologyNodes(grouped, nodes, layouts = [], level = 0) {
            if (nodes.length == 0) return layouts;
            layouts.push(nodes);
            let layout = [];
            nodes.forEach((node, i) => {
                node.id = i;
                node.level = level;
                node.successors.forEach(s => {
                    let successor = grouped.get(s);
                    if (successor.isDrawable()) {
                        layout.push(successor);
                    }
                });
            });
            return this.buildTopologyNodes(grouped, layout, layouts, level + 1);
        },

        createSVG(objectTopology) {
            let container = document.getElementById("svg-container");

            let svg = d3
                .select("#svg-container")
                .append("svg")
                .attr("viewBox", [-5, 0, 1800, 1000])
                .attr("width", "100%")
                .attr("height", "800");

            let g = svg.append("g");

            function zoomed() {
                g.attr("transform", d3.event.transform);
            }

            let zoom = d3
                .zoom()
                .extent([[0, 0], [0, 0]])
                .scaleExtent([1, 8])
                .on("zoom", zoomed);

            svg.call(zoom);

            var defs = svg.append("defs");
            defs.selectAll("marker")
                .data([
                    {
                        id: 2,
                        name: "arrow",
                        path: "M 0,0 m -5,-5 L 5,0 L -5,5 Z",
                        viewbox: "-5 -5 10 10"
                    }
                ])
                .enter()
                .append("svg:marker")
                .attr("id", "arrow")
                .attr("markerHeight", 5)
                .attr("markerWidth", 5)
                .attr("markerUnits", "strokeWidth")
                .attr("orient", "auto")
                .attr("refX", 0)
                .attr("refY", 0)
                .attr("viewBox", "-5 -5 10 10")
                .append("svg:path")
                .attr("d", "M 0,0 m -5,-5 L 5,0 L -5,5 Z")
                .attr("fill", "black");

            let icon = defs.append("g").attr("id", "store-icon");
            icon.append("path")
                .attr(
                    "d",
                    "M 1 17 C 1 -4.33 96 -4.33 96 17 L 96 65 C 96 86.33 1 86.33 1 65 Z"
                )
                .attr("fill", "#ffffff")
                .attr("stroke", "#000000")
                .attr("stroke-width", 2);
            icon.append("path")
                .attr("d", "M 1 17 C 1 33 96 33 96 17")
                .attr("fill", "none")
                .attr("stroke", "#000000")
                .attr("stroke-width", 2);

            var config = {
                topologyWidth: 360,
                topologyMarginRight: 120,
                topologyMarginTop: 15,
                topologyNodeWith: 320
            };

            objectTopology.subTopologies.forEach(d => {
                d.coordinates = {
                    x:
                        d.id *
                        (config.topologyWidth + config.topologyMarginRight),
                    y: config.topologyMarginTop
                };
            });

            var nodesKeyedByName = objectTopology.nodes();

            var div = d3
                .select("body")
                .append("div")
                .attr("class", "tooltip")
                .style("opacity", 0);

            var gSubTopologies = g
                .selectAll()
                .data(objectTopology.subTopologies)
                .enter()
                .append("g")
                .attr("id", function(d) {
                    return "sub-topology-" + d.id;
                })
                .attr("transform", function(d) {
                    return (
                        "translate(" +
                        d.coordinates.x +
                        "," +
                        d.coordinates.y +
                        " )"
                    );
                });

            // add sub-topology
            gSubTopologies
                .append("rect")
                .attr("x", 0)
                .attr("y", 0)
                .attr("fill", "#fff")
                .attr("stroke", "#26b793")
                .attr("stroke-width", 2)
                .attr("fill-opacity", 0.5)
                .attr("width", config.topologyWidth)
                .attr("height", function(d, i) {
                    return d.levels.length * 120;
                })
                .attr("rx", 10)
                .attr("ry", 10)
                .classed("sub-topology", true);

            // add sub-topology name
            gSubTopologies
                .append("text")
                .attr("x", config.topologyWidth - 10)
                .attr("y", 20)
                .attr("dy", ".30em")
                .text(function(d) {
                    return "sub-topology-" + d.id;
                })
                .attr("text-anchor", "end")
                .classed("text-sub-topology-id", true);

            // add groups for containing all sub-topology's nodes
            var gSubTopology = gSubTopologies
                .append("g")
                .attr("id", function(d) {
                    return "g-sub-topology-" + d.id + "-nodes";
                })
                .classed("g-sub-topology-levels", true);

            // add group for each sub-topology's level
            gSubTopology.each(function(subTopology) {
                let storeTranslateX = config.topologyWidth + 10;
                let storeTranslateY = (subTopology.levels.length * 120) / 2;

                let gStore = d3
                    .select(this)
                    .selectAll(".g-sub-topology-store")
                    .data(function(d) {
                        return d.stores;
                    })
                    .enter()
                    .append("g")
                    .attr("transform", function(d, i) {
                        return (
                            "translate(" +
                            storeTranslateX +
                            ", " +
                            (storeTranslateY + i * 100) +
                            ")"
                        );
                    })
                    .each(function(d, i) {
                        d.coordinates = {
                            x: subTopology.coordinates.x + storeTranslateX + 50,
                            y:
                                subTopology.coordinates.y +
                                20 +
                                (storeTranslateY + i * 100)
                        };
                    })
                    .classed("g-sub-topology-store", true);
                gStore.append("use").attr("xlink:href", "#store-icon");

                gStore
                    .append("circle")
                    .attr("cx", 50)
                    .attr("cy", 20)
                    .attr("r", 7)
                    .style("fill", "black")
                    .attr("id", function(d) {
                        return "anchor-" + d.name.toLowerCase();
                    })
                    .classed("sub-topology-node-anchor", true)
                    .on("mouseover", function(d) {
                        div.transition()
                            .duration(200)
                            .style("opacity", 1);
                        div.html("<span>" + d.name + "</span")
                            .style("left", d3.event.pageX + 30 + "px")
                            .style("top", d3.event.pageY - 30 + "px");
                    })
                    .on("mouseout", function(d) {
                        div.transition()
                            .duration(500)
                            .style("opacity", 0);
                    });

                var gSubTopologyLevels = d3
                    .select(this)
                    .selectAll(".g-sub-topology-level")
                    .data(function(d) {
                        return d.levels;
                    })
                    .enter()
                    .append("g")
                    .classed("g-sub-topology-level", true)
                    .attr("id", function(d, i) {
                        return (
                            "g-sub-topology-" + subTopology.id + "-level-" + i
                        );
                    })
                    .each(function(d, i) {
                        let translateY = 50 + i * 100;
                        d.coordinates = {
                            x: subTopology.coordinates.x,
                            y: subTopology.coordinates.y + translateY
                        };
                    })
                    .attr("transform", function(d, i) {
                        return "translate(0, " + (50 + i * 100) + ")";
                    });

                gSubTopologyLevels
                    .selectAll(".g-sub-topology-nodes")
                    .data(function(d) {
                        return d.nodes.filter(node => node.isDrawable());
                    })
                    .enter()
                    .append("g")
                    .attr("class", function(d) {
                        return (
                            "g-sub-topology-node g-sub-topology-node-" +
                            d.type.toLowerCase()
                        );
                    });

                gSubTopologyLevels.each(function(level, i) {
                    d3.select(this)
                        .selectAll(".g-sub-topology-node")
                        .each(function(d, i) {
                            let x =
                                20 +
                                (config.topologyNodeWith / level.nodes.length) *
                                    i;
                            let width =
                                config.topologyNodeWith / level.nodes.length -
                                5;
                            d3.select(this)
                                .append("rect")
                                .attr("x", x)
                                .attr("width", width)
                                .attr("height", 80)
                                .attr("rx", 18)
                                .attr("ry", 18)
                                .classed("topology-node", true)
                                .attr("id", function(d) {
                                    return d.name.toLowerCase();
                                });
                            // add topology node name
                            d3.select(this)
                                .append("text")
                                .attr("x", x + width / 2)
                                .attr("y", 25)
                                .attr("dy", ".40em")
                                .attr("text-anchor", "middle")
                                .text(function(d) {
                                    return d.normalizedName();
                                })
                                .classed("topology-node", true);

                            let cx = x + (width / 4) * 3;
                            let cy = 60;
                            d3.select(this)
                                .datum(function(d) {
                                    d.coordinates = {
                                        x: level.coordinates.x + cx,
                                        y: level.coordinates.y + cy
                                    };
                                    return d;
                                })
                                .append("circle")
                                .attr("cx", cx)
                                .attr("cy", cy)
                                .attr("r", 7)
                                .style("fill", "black")
                                .attr("id", function(d) {
                                    return "anchor-" + d.name.toLowerCase();
                                })
                                .classed("sub-topology-node-anchor", true)
                                .on("mouseover", function(d) {
                                    div.transition()
                                        .duration(200)
                                        .style("opacity", 1);
                                    div.html("<span>" + d.name + "</span")
                                        .style(
                                            "left",
                                            d3.event.pageX + 30 + "px"
                                        )
                                        .style(
                                            "top",
                                            d3.event.pageY - 30 + "px"
                                        );
                                })
                                .on("mouseout", function(d) {
                                    div.transition()
                                        .duration(500)
                                        .style("opacity", 0);
                                });
                        });
                });
            });
            // compute the coordinates of links between nodes/nodes and nodes/topics
            var links = [];

            g.selectAll(".g-sub-topology-node").each(function(source) {
                source.successors.forEach(s => {
                    let target = nodesKeyedByName.get(s);
                    let x1 = source.coordinates.x;
                    let y1 = source.coordinates.y;
                    let x2 =
                        target.coordinates.y > source.coordinates.y
                            ? target.coordinates.x
                            : target.coordinates.x - 10;
                    let y2 =
                        target.coordinates.y > source.coordinates.y
                            ? target.coordinates.y - 10
                            : target.coordinates.y;
                    links.push({
                        source: { x: x1, y: y1 },
                        target: { x: x2, y: y2 }
                    });
                });
            });

            g.selectAll(".g-sub-topology-store").each(function(target) {
                target.nodes.forEach(source => {
                    let x1 = source.coordinates.x;
                    let y1 = source.coordinates.y;
                    let x2 = target.coordinates.x;
                    let y2 = target.coordinates.y;
                    links.push({
                        source: { x: x1, y: y1 },
                        target: { x: x2, y: y2 }
                    });
                });
            });

            g.selectAll()
                .data(links.filter(d => d.target.y <= d.source.y))
                .enter()
                .append("path")
                .attr("class", "topology-node-link-h")
                .style("stroke-dasharray", "3, 3")
                .attr(
                    "d",
                    d3
                        .linkHorizontal()
                        .x(function(d) {
                            return d.x;
                        })
                        .y(function(d) {
                            return d.y;
                        })
                )
                .attr("marker-end", "url(#arrow)");

            g.selectAll()
                .data(links.filter(d => d.target.y > d.source.y))
                .enter()
                .append("path")
                .attr("class", "topology-node-link-v")
                .attr(
                    "d",
                    d3
                        .linkVertical()
                        .x(function(d) {
                            return d.x;
                        })
                        .y(function(d) {
                            return d.y;
                        })
                )
                .attr("marker-end", "url(#arrow)");
        }
    }
};

class Topology {
    constructor(subTopologies = []) {
        this.subTopologies = subTopologies;
    }

    stores() {
        let stores = [];
        this.subTopologies.forEach(d => (stores = stores.concat(d.stores)));
        return stores;
    }

    nodes() {
        let nodes = new Map();
        this.subTopologies.forEach(sub => {
            sub.nodes().forEach(n => {
                nodes.set(n.name, n);
            });
        });
        return nodes;
    }
}

class SubTopology {
    constructor(data) {
        this.id = data.id;
        this.stores = data.stores;
        this.levels = data.levels;
    }

    nodes() {
        let nodes = [];
        this.levels.forEach(l => (nodes = nodes.concat(l.nodes)));
        return nodes;
    }
}

class Store {
    constructor(data) {
        this.name = data.name;
        this.nodes = data.nodes || [];
    }
}

class SubTopologyLevel {
    constructor(data) {
        this.id = data.id;
        this.subTopology = data.subTopology;
        this.nodes = data.nodes;
    }
}

class TopologyNode {
    constructor(data) {
        this.id = data.id;
        this.subTopology = data.subTopology;
        this.level = data.level;
        this.name = data.name;
        this.type = data.type;
        this.successors = data.successors || [];
        this.predecessors = data.predecessors || [];

        TopologyNode.pattern = new RegExp("^[A-Z]+((?:-[A-Z]+)+)-[0-9]{10}$");
    }

    normalizedName() {
        if (TopologyNode.pattern.test(this.name)) {
            return TopologyNode.pattern.exec(this.name)[1].slice(1);
        }
        return this.name;
    }

    isDrawable() {
        return true;
    }
}

class ProcessorNode extends TopologyNode {
    constructor(data) {
        super(data);
        this.stores = data.stores || [];
    }
}

class TopicNode extends TopologyNode {
    constructor(data) {
        super({
            name: data.name,
            type: "TOPIC",
            successors: data.successors,
            predecessors: data.predecessors
        });
        this.isIntermediate = data.isIntermediate || false;
    }

    isDrawable() {
        return !this.isIntermediate;
    }
}
</script>


<style scoped>
#svg-container {
    display: inline-block;
    position: relative;
    width: 100%;
    min-height: 100%;
}
#streams-topology-graph {
    padding-left: 15px;
}

#svg-container svg {
    padding-left: 15px;
}
.text-sub-topology-id {
    font-weight: bold;
    text-anchor: end;
}

text.topology-node {
    font-size: 15px;
}

.g-sub-topology-node rect {
    fill-opacity: 0.5;
    stroke-width: 3;
    stroke-opacity: 0.8;
}
.g-sub-topology-node.g-sub-topology-node-processor rect,
.g-sub-topology-node.g-sub-topology-node-sink rect,
.g-sub-topology-node.g-sub-topology-node-source rect {
    fill: #2762c2;
    stroke: #2762c2;
}

.g-sub-topology-node.g-sub-topology-node-topic rect {
    fill: #26b793;
    stroke: #26b793;
}

.topology-node-link-v,
.topology-node-link-h {
    stroke-width: 2;
    stroke: black;
    fill: none;
}

div.tooltip {
    position: absolute;
    text-align: center;
    padding: 10px;
    font: 14px sans-serif;
    font-weight: bold;
    background: #ffffff;
    border: 3px solid #2762c2;
    border-radius: 8px;
    pointer-events: none;
}
</style>
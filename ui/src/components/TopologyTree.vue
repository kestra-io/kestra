<template>
    <div>
        <div id="topology"></div>
        <b-card v-if="node" :title="`Task : ${node.id}`" style="max-width: 40rem;" class="mb-2">
            <b-card-text>
                <p><b>Type</b> : {{node.type}}</p>
                <p><b>Format</b> : {{node.format}}</p>
                <p><b>Timeout</b> : {{node.timeout}}</p>
                <p><b>Level</b> : {{node.level}}</p>
                <p><b>Retry</b> : {{node.retry}}</p>
                <p><b>Commands</b> : {{node.commands}}</p>
                <p v-if="node.tasks"><b>Task count</b> : {{node.tasks.length}}</p>
            </b-card-text>
        </b-card>
        <div v-if="open">
            <b-button @click="open = false">X</b-button>
            <pre>{{tree}}</pre>
        </div>
        <b-button v-else @click="open = true">V</b-button>
    </div>
</template>
<script>
import * as d3 from "d3";
export default {
    props: {
        tree: {
            type: Object,
            required: true
        }
    },
    data() {
        return {
            open: true,
            node: undefined
        };
    },
    methods: {
        setCurrentNode(node) {
            this.node = node.data;
        }
    },
    mounted() {
        const data = this.tree;

        // set the dimensions and margins of the graph
        var width = 460;
        var height = 460;

        // append the svg object to the body of the page
        var svg = d3
            .select("#topology")
            .append("svg")
            .attr("width", "100vw")
            .attr("height", "50vh")
            .append("g")
            .attr("transform", "translate(40,0)"); // bit of margin on the left = 40

        // read json data
        // Create the cluster layout:
        var cluster = d3.cluster().size([height, width - 100]); // 100 is the margin I will have on the right side
        // Give the data to this cluster layout:
        var root = d3.hierarchy(data, function(d) {
            return d.tasks;
        });
        cluster(root);

        const yFactor = 2.5;
        // Add the links between nodes:
        svg.selectAll("path")
            .data(root.descendants().slice(1))
            .enter()
            .append("path")
            .attr("d", function(d) {
                return (
                    "M" +
                    d.y * yFactor +
                    "," +
                    d.x +
                    "C" +
                    (d.parent.y + 150) +
                    "," +
                    d.x +
                    " " +
                    d.parent.y * yFactor +
                    "," +
                    d.parent.x + // 50 and 150 are coordinates of inflexion, play with it to change links shape
                    " " +
                    d.parent.y * yFactor +
                    "," +
                    d.parent.x
                );
            })
            .style("fill", "none")
            .attr("stroke", "#bbb")
            .attr("stroke-width", "2");

        const g = svg
            .selectAll("g")
            .data(root.descendants())
            .enter()
            .append("g")
            .attr("transform", function(d) {
                return "translate(" + d.y * yFactor + "," + d.x + ")";
            });
        g.append("rect")
            .attr("width", 130)
            .attr("y", -15)
            .attr("x", node => (node.depth === 0 ? -30 : -10))
            .attr("height", 30)
            .style("rx", 15)
            .style("ry", 15)
            .style("fill", "#c9fc8d")
            // .attr("stroke", "#888")
            .style("stroke-width", 1)
            .attr("title", node => node.data.id);
        g.on("click", this.setCurrentNode);
        g.append("text")
            .text(
                node =>
                    `${node.data.id.substr(0, 13)}${
                        node.data.id.length > 13 ? "..." : ""
                    }`
            )
            .attr("fill", "black")
            .attr("font-family", "monospace")
            .attr("y", 5)
            .attr("x", node => (node.depth === 0 ? -27 : -7));
    }
};
</script>
<style scoped>
text {
    font-family: monospace !important;
}
</style>

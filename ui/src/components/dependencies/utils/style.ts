import type cytoscape from "cytoscape";

import {cssVariable} from "@kestra-io/ui-libs";

const VARIABLES = {
    node: {
        default: {
            background: "--ks-dependencies-node-background",
            border: "--ks-dependencies-node-border",
        },
        selected: {
            background: "--ks-dependencies-node-background-selected",
            border: "--ks-dependencies-node-border-selected",
        },
        highlighted: {
            background: "--ks-dependencies-node-background-hover",
            border: "--ks-dependencies-node-border-hover",
        },
        faded: {
            background: "--ks-dependencies-node-background-selected-level2",
            border: "--ks-dependencies-node-border-selected-level2",
        },
        hovered: {
            background: "--ks-dependencies-node-background-hover",
            border: "--ks-dependencies-node-border-hover",
        },
    },
    edge: {
        default: "--ks-dependencies-edge",
        selected: "--ks-dependencies-edge-selected",
        faded: "--ks-dependencies-edge-selected-level2",
        hovered: "--ks-dependencies-edge-hover",
    },
};

const nodeBase: cytoscape.Css.Node = {
    label: "data(flow)",
    "border-width": 2,
    "border-style": "solid",
    color: "white",
    "font-size": 10,
    "text-valign": "bottom",
    "text-margin-y": 10,
};

const edgeBase: cytoscape.Css.Edge = {
    "target-arrow-shape": "triangle",
    "curve-style": "bezier",
    width: 1,
    "line-style": "solid",
};

function nodeColors(type: keyof typeof VARIABLES.node): Partial<cytoscape.Css.Node> {
    return {
        "background-color": cssVariable(VARIABLES.node[type].background)!,
        "border-color": cssVariable(VARIABLES.node[type].border)!,
    };
}

function edgeColors(type: keyof typeof VARIABLES.edge): Partial<cytoscape.Css.Edge> {
    return {
        "line-color": cssVariable(VARIABLES.edge[type])!,
        "target-arrow-color": cssVariable(VARIABLES.edge[type])!,
    };
}

export const style: cytoscape.StylesheetJson = [
    {
        selector: "node",
        style: {...nodeBase, ...nodeColors("default")},
    },
    {
        selector: "node.selected",
        style: {...nodeBase, ...nodeColors("selected"), color: "white"},
    },
    {
        selector: "node.highlighted",
        style: {...nodeBase, ...nodeColors("highlighted")},
    },
    {
        selector: "node.faded",
        style: {...nodeBase, ...nodeColors("faded"), "background-opacity": 0.75, "border-opacity": 0.75, color: "white"},
    },
    {
        selector: "node.hovered",
        style: {...nodeBase, ...nodeColors("hovered")},
    },
    {
        selector: "edge",
        style: {...edgeBase, ...edgeColors("default")},
    },
    {
        selector: "edge.selected",
        style: {...edgeBase, ...edgeColors("selected"), "line-style": "dashed", "line-dash-pattern": [3, 5], width: 2},
    },
    {
        selector: "edge.faded",
        style: {...edgeBase, ...edgeColors("faded"), width: 1, opacity: 0.75},
    },
    {
        selector: "edge.hovered",
        style: {...edgeBase, ...edgeColors("hovered"), width: 2},
    },
];

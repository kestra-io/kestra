import type cytoscape from "cytoscape";

import {cssVariable} from "@kestra-io/ui-libs";

const VARIABLES = {
    node: {
        default: {
            background: "--ks-dependencies-node-background",
            border: "--ks-dependencies-node-border",
        },
        faded: {
            background: "--ks-dependencies-node-background-selected-level2",
            border: "--ks-dependencies-node-border-selected-level2",
        },
        selected: {
            background: "--ks-dependencies-node-background-selected",
            border: "--ks-dependencies-node-border-selected",
        },
        hovered: {
            background: "--ks-dependencies-node-background-hover",
            border: "--ks-dependencies-node-border-hover",
        },
    },
    edge: {
        default: "--ks-dependencies-node-border",
        faded: "--ks-dependencies-edge-selected-level2",
        hovered: "--ks-dependencies-edge-hover",
    },
};

const nodeBase: cytoscape.Css.Node = {
    label: "data(flow)",
    "border-width": 2,
    "border-style": "solid",
    color: cssVariable("--ks-content-primary"),
    "font-size": 10,
    "text-valign": "bottom",
    "text-margin-y": 10,
};

const edgeBase: cytoscape.Css.Edge = {
    "target-arrow-shape": "triangle",
    "curve-style": "bezier",
    width: 2,
    "line-style": "solid",
};

const edgeAnimated: cytoscape.Css.Edge = {
    "line-style": "dashed",
    "line-dash-pattern": [3, 5],
};

function nodeColors(type: keyof typeof VARIABLES.node = "default"): Partial<cytoscape.Css.Node> {
    return {
        "background-color": cssVariable(VARIABLES.node[type].background)!,
        "border-color": cssVariable(VARIABLES.node[type].border)!,
    };
}

export function edgeColors(type: keyof typeof VARIABLES.edge = "default"): Partial<cytoscape.Css.Edge> {
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
        selector: "node.faded",
        style: {
            ...nodeBase,
            ...nodeColors("faded"),
            "background-opacity": 0.75,
            "border-opacity": 0.75,
        },
    },
    {
        selector: "node.selected",
        style: {...nodeBase, ...nodeColors("selected")},
    },
    {
        selector: "node.hovered",
        style: {...nodeBase, ...nodeColors("hovered")},
    },
    {
        selector: "edge",
        style: {...edgeBase, ...edgeColors("default"), width: 1},
    },
    {
        selector: "edge.faded",
        style: {...edgeBase, ...edgeColors("faded"), ...edgeAnimated},
    },
    {
        selector: "edge.hovered",
        style: {...edgeBase, ...edgeColors("hovered")},
    },
    {
        selector: "edge.executions",
        style: {...edgeBase, ...edgeAnimated},
    },
];

import type cytoscape from "cytoscape";

import {cssVariable} from "@kestra-io/ui-libs";

const VARIABLES = {
    node: {
        default: {
            background: "--ks-dependencies-node-background-default",
            border: "--ks-dependencies-node-border-default",
        },
        faded: {
            background: "--ks-dependencies-node-background-faded",
            border: "--ks-dependencies-node-border-faded",
        },
        selected: {
            background: "--ks-dependencies-node-background-selected",
            border: "--ks-dependencies-node-border-selected",
        },
        hovered: {
            background: "--ks-dependencies-node-background-hovered",
            border: "--ks-dependencies-node-border-hovered",
        },
    },
    edge: {
        default: "--ks-dependencies-edge-default",
        faded: "--ks-dependencies-edge-faded",
        selected: "--ks-dependencies-node-background-selected",
        hovered: "--ks-dependencies-edge-hovered",
    },
};

const nodeBase = (): cytoscape.Css.Node => ({
    label: "data(flow)",
    "border-width": 2,
    "border-style": "solid",
    color: cssVariable("--ks-content-primary"),
    "font-size": 10,
    "text-valign": "bottom",
    "text-margin-y": 10,
});

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

export const getStyle = (): cytoscape.StylesheetJson => [
    {
        selector: "node",
        style: {...nodeBase(), ...nodeColors("default")},
    },
    {
        selector: "node.faded",
        style: {
            ...nodeBase(),
            ...nodeColors("faded"),
            "background-opacity": 0.75,
            "border-opacity": 0.75,
        },
    },
    {
        selector: "node.selected",
        style: {...nodeBase(), ...nodeColors("selected")},
    },
    {
        selector: "node.hovered",
        style: {...nodeBase(), ...nodeColors("hovered")},
    },
    {
        selector: "edge",
        style: {...edgeBase, ...edgeColors("default"), width: 1},
    },
    {
        selector: "edge.faded",
        style: {...edgeBase, ...edgeColors("faded")},
    },
    {
        selector: "edge.selected",
        style: {...edgeBase, ...edgeColors("selected"), ...edgeAnimated},
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

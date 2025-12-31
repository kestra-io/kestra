import type cytoscape from "cytoscape";

import {cssVariable} from "@kestra-io/ui-libs";

import {States} from "./types";

const VARIABLES: {node: { background: States; border: States }; edge: States;} = {
    node: {
        background: {
            default: "--ks-dependencies-node-background-default",
            faded: "--ks-dependencies-node-background-faded",
            selected: "--ks-dependencies-node-background-selected",
            hovered: "--ks-dependencies-node-background-hovered",
            assets: "--ks-dependencies-node-background-assets",
        },
        border: {
            default: "--ks-dependencies-node-border-default",
            faded: "--ks-dependencies-node-border-faded",
            selected: "--ks-dependencies-node-border-selected",
            hovered: "--ks-dependencies-node-border-hovered",
            assets: "--ks-dependencies-node-border-assets",
        },
    },
    edge: {
        default: "--ks-dependencies-edge-default",
        faded: "--ks-dependencies-edge-faded",
        selected: "--ks-dependencies-edge-selected",
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

function nodeColors(type: keyof States = "default"): Partial<cytoscape.Css.Node> {
    return {
        "background-color": cssVariable(VARIABLES.node.background[type])!,
        "border-color": cssVariable(VARIABLES.node.border[type])!,
    };
}

export function edgeColors(type: keyof States = "default"): Partial<cytoscape.Css.Edge> {
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
        selector: "node[metadata.subtype = \"ASSET\"]",
        style: {...nodeBase(), ...nodeColors("assets")},
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

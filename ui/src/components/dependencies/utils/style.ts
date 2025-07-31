import type cytoscape from "cytoscape";

import {cssVariable} from "@kestra-io/ui-libs";

const VARIABLES = {
    node: {
        default: {
            background: "--ks-dependencies-node-background",
            border: "--ks-dependencies-node-border",
        },
        hover: {
            background: "--ks-dependencies-node-background-hover",
            border: "--ks-dependencies-node-border-hover",
        },
        selected: {
            background: "--ks-dependencies-node-background-selected",
            border: "--ks-dependencies-node-border-selected",
        },
        faded: {
            background: "--ks-dependencies-node-background-selected-level2",
            border: "--ks-dependencies-node-border-selected-level2",
        },
    },
    edge: {
        default: "--ks-dependencies-edge",
        hover: "--ks-dependencies-edge-hover",
        selected: "--ks-dependencies-edge-selected",
        faded: "--ks-dependencies-edge-selected-level2",
    },
};

const commonNode: Record<string, any> = {
    label: "data(flow)",
    "border-width": 2,
    "border-style": "solid",
    color: "white",
    "font-size": 10,
    "text-valign": "bottom",
    "text-margin-y": 10,
};

const commonEdge: Record<string, any> = {
    "target-arrow-shape": "triangle",
    "curve-style": "bezier",
};

const hoverEdge: Record<string, any> = {
    "line-color": cssVariable(VARIABLES.edge.hover)!,
    "target-arrow-color": cssVariable(VARIABLES.edge.hover)!,
    width: 2,
};

const selectedEdge: Record<string, any> = {
    "line-style": "dashed",
    "line-dash-pattern": [3, 5],
    width: 2,
};

export const style: cytoscape.StylesheetJson = [
    {
        selector: "node",
        style: {
            ...commonNode,
            "background-color": cssVariable(VARIABLES.node.default.background)!,
            "border-color": cssVariable(VARIABLES.node.default.border)!,
        },
    },
    {
        selector: "node.faded",
        style: {
            "background-color": cssVariable(VARIABLES.node.faded.background)!,
            "border-color": cssVariable(VARIABLES.node.faded.border)!,
            "background-opacity": 0.75,
            "border-opacity": 0.75,
            color: "white",
        },
    },
    {
        selector: "node.hovered",
        style: {
            "background-color": cssVariable(VARIABLES.node.hover.background)!,
            "border-color": cssVariable(VARIABLES.node.hover.border)!,
        },
    },
    {
        selector: "node.faded.hovered",
        style: {
            "background-color": cssVariable(VARIABLES.node.hover.background)!,
            "border-color": cssVariable(VARIABLES.node.hover.border)!,
            opacity: 1,
        },
    },
    {
        selector: "node.selected",
        style: {
            "background-color": cssVariable(VARIABLES.node.selected.background)!,
            "border-color": cssVariable(VARIABLES.node.selected.border)!,
            color: "white",
        },
    },
    {
        selector: "node.selected.hovered",
        style: {
            "background-color": cssVariable(VARIABLES.node.hover.background)!,
            "border-color": cssVariable(VARIABLES.node.hover.border)!,
            color: "white",
        },
    },
    {
        selector: "edge",
        style: {
            ...commonEdge,
            width: 1,
            "line-color": cssVariable(VARIABLES.edge.default)!,
            "target-arrow-color": cssVariable(VARIABLES.edge.default)!,
            "line-style": "solid",
        },
    },
    {
        selector: "edge.faded",
        style: {
            "line-color": cssVariable(VARIABLES.edge.faded)!,
            "target-arrow-color": cssVariable(VARIABLES.edge.faded)!,
            width: 1,
            opacity: 0.75,
            "line-style": "solid",
        },
    },
    {
        selector: "edge.hovered",
        style: hoverEdge,
    },
    {
        selector: "edge.faded.hovered",
        style: {
            ...hoverEdge,
            opacity: 1,
            "line-style": "solid",
        },
    },
    {
        selector: "edge.selected",
        style: {
            ...selectedEdge,
            "line-color": cssVariable(VARIABLES.edge.selected)!,
            "target-arrow-color": cssVariable(VARIABLES.edge.selected)!,
        },
    },
    {
        selector: "edge.selected.hovered",
        style: {
            ...selectedEdge,
            ...hoverEdge,
        },
    },
];

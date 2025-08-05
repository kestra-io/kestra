import {onMounted, ref} from "vue";

import type {Ref} from "vue";

import cytoscape from "cytoscape";

import {State, cssVariable} from "@kestra-io/ui-libs";

import {FLOW, EXECUTION, type Node, type Element, getDependencies} from "../../../../scripts/product/dependencies";

import {style} from "../utils/style";
const SELECTED = "selected", FADED = "faded",  HOVERED = "hovered", EXECUTIONS = "executions";

/**
 * Builds cytoscape initialization options with graph elements and interaction settings.
 * The container should be set dynamically before initialization.
 *
 * @param subtype - The dependency subtype, either `"FLOW"` or `"EXECUTION"`.
 * @returns A cytoscape options object excluding the container, with elements populated.
 *
 * @see {@link https://js.cytoscape.org/#core | Cytoscape core options documentation}
 */
export function options(subtype: typeof FLOW | typeof EXECUTION): { elements: Element[] } & Omit<cytoscape.CytoscapeOptions, "container" | "elements"> {
    return {
        elements: getDependencies({subtype}),
        minZoom: 0.1,
        maxZoom: 2,
    };
}

/**
 * Layout options for the COSE layout algorithm used in cytoscape.
 *
 * @see {@link https://js.cytoscape.org/#layouts/cose | COSE layout options documentation}
 */
const layout: cytoscape.CoseLayoutOptions = {
    name: "cose",

    // Physical forces
    nodeRepulsion: 2_000_000,
    edgeElasticity: 100,
    idealEdgeLength: 250,

    // Gravity settings
    gravity: 0.05,

    // Layout iterations & cooling
    numIter: 10_000,
    initialTemp: 200,
    minTemp: 1,

    // Spacing and padding
    padding: 50,
    componentSpacing: 150,

    // Node sizing
    nodeDimensionsIncludeLabels: true,
};

/**
 * Sets the size of each node in the cytoscape instance
 * based on the number of connected edges.
 *
 * The node size is calculated as: `baseSize + count * scale`,
 * capped at `maxSize`.
 *
 * @param cy - The cytoscape core instance containing the graph.
 * @param baseSize - The base size of each node. Default is 20.
 * @param scale - The scale factor for each connected edge. Default is 2.
 * @param maxSize - The maximum allowed size for a node. Default is 100.
 */
export function setNodeSizes(cy: cytoscape.Core, baseSize = 20, scale = 2, maxSize = 100): void {
    cy.nodes().forEach((node) => {
        const count = node.connectedEdges().length;

        let size = baseSize + count * scale;
        if (size > maxSize) size = maxSize;

        node.style({width: size, height: size});
    });
}

/**
 * Retrieves the execution state color for a given node.
 *
 * - Looks up the node’s `metadata.state` value.
 * - Uses the State service to resolve the corresponding color.
 * - Returns a fallback color if no state is defined.
 *
 * @param node - The cytoscape node element to evaluate.
 * @returns The color associated with the node’s execution state, or a fallback if missing.
 */
function getStateColor(node: cytoscape.NodeSingular): string {
    const state = node.data("metadata")?.state;
    return state ? State.getStateColor(state) : cssVariable("--ks-dependencies-node-background")!;
}

/**
 * Applies execution state colors to all nodes in the cytoscape graph.
 *
 * - Removes all custom classes from nodes and edges.
 * - Sets each node’s background and border color based on its execution state.
 *
 * @param cy - The cytoscape core instance managing the graph.
 */
function setExecutionNodeColors(cy: cytoscape.Core): void {
    // Remove all existing custom classes from the graph
    clearClasses(cy);

    // Apply state-based colors to nodes
    cy.nodes().forEach((node) => {
        node.style({
            "background-color": getStateColor(node),
            "border-color": getStateColor(node)
        });
    });
}

/**
 * Removes the default or specified classes from all elements in the cytoscape instance.
 *
 * @param cy - The cytoscape core instance containing the graph.
 * @param classes - An array of class names to remove (default: ["selected", "faded", "hovered", "executions"]).
 */
export function clearClasses(cy: cytoscape.Core, classes: string[] = [SELECTED, FADED, HOVERED, EXECUTIONS]): void {
    cy.elements().removeClass(classes.join(" "));
}

/**
 * Fits the cytoscape viewport to include all elements, with default or specified padding.
 *
 * @param cy - The cytoscape core instance containing the graph.
 * @param padding - The number of pixels to pad around the elements (default: 50).
 */
export function fit(cy: cytoscape.Core, padding: number = 50): void {
    cy.fit(undefined, padding);
}

/**
 * Handles selecting a node in the cytoscape graph.
 *
 * - Removes all existing "selected", "faded", "hovered" and "executions" states from nodes and edges.
 * - Marks the chosen node as selected.
 * - Applies a faded style to connected elements based on the subtype:
 *   - FLOW: Fades both connected edges and neighbor nodes.
 *   - EXECUTION: Highlights connected edges with execution color, fades neighbor nodes.
 * - Updates the provided Vue ref with the selected node’s ID.
 * - Smoothly centers and zooms the viewport on the selected node.
 *
 * @param cy - The cytoscape core instance managing the graph.
 * @param node - The node element to select.
 * @param selected - Vue ref storing the currently selected node ID.
 * @param subtype - Determines how connected elements are highlighted ("FLOW" or "EXECUTION").
 * @param id - Optional explicit ID to assign to the ref (defaults to the node’s own ID).
 */
function selectHandler(cy: cytoscape.Core, node: cytoscape.NodeSingular, selected: Ref<Node["id"] | undefined>, subtype: typeof FLOW | typeof EXECUTION, id?: Node["id"]): void {
    // Remove all "selected", "faded", "hovered" and "executions" classes from every element
    clearClasses(cy);

    // Mark the chosen node as selected
    node.addClass(SELECTED);

    if (subtype === FLOW) {
        // FLOW: Fade both connected edges and neighbor nodes
        node.connectedEdges().union(node.connectedEdges().connectedNodes()).addClass(FADED);
    } else {
        // EXECUTION: Highlight connected edges with execution color
        node.connectedEdges().removeClass(FADED).addClass(EXECUTIONS).style({
            "line-color": getStateColor(node),
            "target-arrow-color": getStateColor(node)
        });
    }

    // Update the Vue ref with the selected node’s ID
    selected.value = id ?? node.id();

    // Center and zoom the viewport on the selected node
    cy.animate({center: {eles: node}, zoom: 1.2}, {duration: 500});
}

/**
 * Sets up hover handlers for nodes and edges.
 *
 * @param cy - The cytoscape core instance containing the graph.
 */
function hoverHandler(cy: cytoscape.Core): void {
    ["node", "edge"].forEach((type) => {
        cy.on("mouseover", type, (event: cytoscape.EventObject) => event.target.addClass(HOVERED));
        cy.on("mouseout", type, (event: cytoscape.EventObject) => event.target.removeClass(HOVERED));
    });
}

/**
 * Initializes a cytoscape instance inside the provided container element,
 * applies styling and sizing rules, and sets up interactive behaviors.
 *
 * @param container - A Vue ref to an HTML element which will host the cytoscape graph.
 * @param subtype - The dependency subtype, either `"FLOW"` or `"EXECUTION"`. Defaults to `"FLOW"`.
 */
export function useDependencies(container: Ref<HTMLElement | null>, subtype: typeof FLOW | typeof EXECUTION = FLOW) {
    let cy: cytoscape.Core;

    const OPTIONS = ref(options(subtype));

    const loading = ref(true);

    const selectedNodeID: Ref<Node["id"] | undefined> = ref(undefined);

    /**
     * Selects a node in the cytoscape graph by its ID.
     *
     * @param id - The ID of the node to select.
     */
    const selectNode = (id: Node["id"]): void => {
        if (!cy) return;

        const node = cy.getElementById(id);

        if (node.nonempty()) {
            selectHandler(cy, node, selectedNodeID, subtype, id);
        }
    };

    onMounted(() => {
        if (!container.value) return;

        cy = cytoscape({container: container.value, layout, ...OPTIONS.value, style});

        // Dynamically size nodes based on connectivity
        setNodeSizes(cy);

        // Apply execution state colors to each node
        if(subtype === EXECUTION) setExecutionNodeColors(cy);

        // Setup hover handlers for nodes and edges
        hoverHandler(cy);

        // Animate dashed selected edges
        let dashOffset = 0;
        function animateEdges(): void {
            dashOffset -= 0.25;
            cy.edges(`.${SELECTED}, .${FADED}`).style("line-dash-offset", dashOffset);
            requestAnimationFrame(animateEdges);
        }
        animateEdges();

        // Node tap handler using selectHandler
        cy.on("tap", "node", (event: cytoscape.EventObject) => {
            const node = event.target;

            selectHandler(cy, node, selectedNodeID, subtype);
        });

        // Preselect the first node after layout completes
        cy.on("layoutstop", () => {
            loading.value = false;

            const node = cy.nodes()[0];

            if (!node) return;

            selectHandler(cy, node, selectedNodeID, subtype);
        });
    });

    return {
        OPTIONS,
        loading,
        selectedNodeID,
        selectNode,
        handlers: {
            zoomIn: () => cy.zoom({level: cy.zoom() + 0.1, renderedPosition: cy.getElementById(selectedNodeID.value!).renderedPosition()}),
            zoomOut: () => cy.zoom({level: cy.zoom() - 0.1, renderedPosition: cy.getElementById(selectedNodeID.value!).renderedPosition()}),
            clearSelection: () => {
                clearClasses(cy);
                selectedNodeID.value = undefined;
                fit(cy);
            },
            fit: () => fit(cy)
        }
    };
}

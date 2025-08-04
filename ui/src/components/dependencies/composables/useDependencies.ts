import {onMounted, ref} from "vue";

import type {Ref} from "vue";

import cytoscape from "cytoscape";

import {type Node, type Element, getDependencies} from "../../../../scripts/product/dependencies";

import {style} from "../utils/style";
const SELECTED = "selected", FADED = "faded",  HOVERED = "hovered";

/**
 * Cytoscape initialization options, including graph elements and interaction settings.
 * The container should be set dynamically before initialization.
 *
 * @see {@link https://js.cytoscape.org/#core | Cytoscape core options documentation}
 */
export const options: { elements: Element[] } & Omit<cytoscape.CytoscapeOptions, "container" | "elements"> = {
    elements: getDependencies({}),
    minZoom: 0.1,
    maxZoom: 2,
};

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
 * Removes the default or specified classes from all elements in the cytoscape instance.
 *
 * @param cy - The cytoscape core instance containing the graph.
 * @param classes - An array of class names to remove (default: ["selected", "faded", "hovered"]).
 */
export function clearClasses(cy: cytoscape.Core, classes: string[] = ["selected", "faded", "hovered"]): void {
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
 * - Removes all existing "selected", "faded", and "hovered" states from nodes and edges.
 * - Marks the chosen node as selected.
 * - Applies a faded style to the node’s directly connected edges and neighbor nodes.
 * - Updates the provided Vue ref with the selected node’s ID.
 * - Smoothly centers and zooms the viewport on the selected node.
 *
 * @param cy - The cytoscape core instance managing the graph.
 * @param node - The node element to select.
 * @param selected - Vue ref storing the currently selected node ID.
 * @param id - Optional explicit ID to assign to the ref (defaults to the node’s own ID).
 */
function selectHandler(cy: cytoscape.Core, node: cytoscape.NodeSingular, selected: Ref<Node["id"] | undefined>, id?: Node["id"]): void {
    // Remove all "selected", "faded", and "hovered" classes from every element
    clearClasses(cy);

    // Mark the chosen node as selected
    node.addClass(SELECTED);

    // Find edges and neighbor nodes directly connected to the selected node
    const connected = node.connectedEdges().union(node.connectedEdges().connectedNodes());

    // Apply faded styling to connected edges and neighbor nodes
    connected.addClass(FADED);

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
 */
export function useDependencies(container: Ref<HTMLElement | null>) {
    let cy: cytoscape.Core;

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
            selectHandler(cy, node, selectedNodeID, id);
        }
    };

    onMounted(() => {
        if (!container.value) return;

        cy = cytoscape({container: container.value, layout, ...options, style});

        // Dynamically size nodes based on connectivity
        setNodeSizes(cy);

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

            selectHandler(cy, node, selectedNodeID);
        });

        // Preselect the first node after layout completes
        cy.on("layoutstop", () => {
            loading.value = false;

            const node = cy.nodes()[0];

            if (!node) return;

            selectHandler(cy, node, selectedNodeID);
        });
    });

    return {
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

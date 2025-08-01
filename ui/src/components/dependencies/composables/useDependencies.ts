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
    minZoom: 0.25,
    maxZoom: 1.5,
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
    numIter: 100,
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
 * Handles selecting a node in the cytoscape graph.
 *
 * - Clears any "selected" and "hovered" state on nodes and edges.
 * - Applies a faded style to the selected node’s connected edges and neighbor nodes.
 * - Highlights the selected node itself.
 * - Updates the provided Vue ref with the selected node's ID.
 * - Animates the viewport to center and zoom into the selected node.
 *
 * @param cy       - The cytoscape core instance managing the graph.
 * @param node     - The node element to select.
 * @param selected - Vue ref to store the selected node ID.
 * @param id       - Optional explicit ID to set on the ref (defaults to node.id()).
 */
function selectHandler(cy: cytoscape.Core, node: cytoscape.NodeSingular, selected: Ref<Node["id"] | undefined>, id?: Node["id"]): void {
    // Remove all selected and hovered classes from nodes and edges
    cy.$(`.${SELECTED}, .${HOVERED}`).removeClass(`${SELECTED} ${HOVERED}`);

    // Get edges and neighbor nodes connected directly to the selected node
    const connected = node
        .connectedEdges()
        .union(node.connectedEdges().connectedNodes());

    // Add faded styling to the connected edges and nodes
    connected.addClass(FADED);

    // Highlight the selected node itself
    node.addClass(SELECTED);

    // Update the Vue ref with the selected node ID
    selected.value = id ?? node.id();

    // Animate viewport to center and zoom into the selected node
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
            cy.edges(`.${SELECTED}`).filter((element) => !element.hasClass(FADED)).style("line-dash-offset", dashOffset);
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
            const node = cy.nodes()[0];

            if (!node) return;

            selectHandler(cy, node, selectedNodeID);
        });
    });

    return {selectedNodeID, selectNode};
}

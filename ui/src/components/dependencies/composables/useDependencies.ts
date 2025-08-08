import {onMounted, onBeforeUnmount, nextTick, watch, ref} from "vue";

import {useCoreStore} from "../../../stores/core";
import {useFlowStore} from "../../../stores/flow";
import {useExecutionsStore} from "../../../stores/executions";

import {useI18n} from "vue-i18n";

import type {Ref} from "vue";

import type {RouteParams} from "vue-router";

import {v4 as uuid} from "uuid";

import cytoscape from "cytoscape";

import {State, cssVariable} from "@kestra-io/ui-libs";

import {NODE, EDGE, FLOW, EXECUTION, type Node, type Edge, type Element} from "../utils/types";
import {getRandomNumber, getDependencies} from "../../../../tests/fixtures/dependencies/getDependencies";

import {edgeColors, style} from "../utils/style";
const SELECTED = "selected", FADED = "faded",  HOVERED = "hovered", EXECUTIONS = "executions";

const options: Omit<cytoscape.CytoscapeOptions, "container" | "elements"> & { elements?: Element[] } = {
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
    clearClasses(cy, EXECUTION);

    // Apply state-based colors to nodes
    cy.nodes().forEach((node) => {
        node.style({
            "background-color": getStateColor(node),
            "border-color": getStateColor(node)
        });
    });
}

/**
 * Removes the specified CSS classes from all elements (nodes and edges) in the cytoscape instance.
 * 
 * If the subtype is "EXECUTION", it also reapplies the default edge styling.
 *
 * This function is typically used to clear selection, hover, and execution-related classes
 * before applying new styles or resetting the graph state.
 *
 * @param cy - The cytoscape core instance containing the graph elements.
 * @param subtype - The dependency subtype, either "FLOW" or "EXECUTION".
 *                  Edge styles are only reset when subtype is "EXECUTION".
 * @param classes - An array of class names to remove from all elements.
 *                  Defaults to ["selected", "faded", "hovered", "executions"].
 */
export function clearClasses(cy: cytoscape.Core, subtype: typeof FLOW | typeof EXECUTION, classes: string[] = [SELECTED, FADED, HOVERED, EXECUTIONS]): void {
  cy.elements().removeClass(classes.join(" "));
  if (subtype === EXECUTION) cy.edges().style(edgeColors());
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
    clearClasses(cy, subtype);

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
 * Initializes and manages a Cytoscape instance within a Vue component.
 *
 * @param container - Vue ref pointing to the DOM element that hosts the Cytoscape graph.
 * @param subtype - Dependency subtype, either `"FLOW"` or `"EXECUTION"`. Defaults to `"FLOW"`.
 * @param initialNodeID - Optional ID of the node to preselect after layout completes.
 * @param params - Vue Router params, expected to include `id` and `namespace`.
 * @param isTesting - When true, bypasses API data fetching and uses mock/test data.
 * @returns An object with element getters, loading state, selected node ID,
 *          selection helpers, and control handlers.
 */
export function useDependencies(container: Ref<HTMLElement | null>, subtype: typeof FLOW | typeof EXECUTION = FLOW, initialNodeID: string, params: RouteParams, isTesting = false) {
    const coreStore = useCoreStore();
    const flowStore = useFlowStore();
    const executionsStore = useExecutionsStore();

    const {t} = useI18n({useScope: "global"});

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
            selectHandler(cy, node, selectedNodeID, subtype, id);
        }
    };

    let elements: { data: cytoscape.ElementDefinition[]; count: number }  = {data: [], count: 0};
    onMounted(async () => {
        if (!container.value) return;

        if(isTesting) elements = {data: getDependencies({subtype}), count: getRandomNumber(1, 100)};
        else elements = await flowStore.loadDependencies({id: (subtype === FLOW ? params.id : params.flowId) as string, namespace: params.namespace as string, subtype});

        if(subtype === EXECUTION) nextTick(() => openSSE());

        cy = cytoscape({container: container.value, layout, ...options, style, elements: elements.data});

        // Hide nodes immediately after initialization to avoid visual flickering or rearrangement during layout setup
        cy.ready(() => cy.nodes().style("display", "none"));

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

        cy.on("layoutstop", () => {
            loading.value = false;

            // Reveal nodes after layout rendering completes
            cy.nodes().style("display", "element");

            // Preselect the proper node after layout rendering completes
            const node = isTesting ? cy.nodes()[0] : cy.nodes().filter((n) => n.data("flow") === initialNodeID);
            if (node) selectHandler(cy, node, selectedNodeID, subtype);
        });
    });

    const sse = ref();
    const messages = ref<Record<string, any>[]>([]);

    watch(messages, (newMessages) => {
        if (newMessages.length <= 0) return;

        newMessages.forEach((message: Record<string, any>) => {
            const matched = cy.nodes().filter((element) => element.data("id") === `${message.tenantId}_${message.namespace}_${message.flowId}`);

            if (matched.nonempty()) {
                matched.forEach((node) => {
                    const state = message.state.current;

                    node.data({...node.data(), metadata: {...node.data("metadata"), state}});
                    node.style({"background-color": State.getStateColor(state), "border-color": State.getStateColor(state)});
                });
            }
        });
    },
    {deep: true},
    );

    const openSSE = () => {
        if (subtype !== EXECUTION) return;

        closeSSE();

        sse.value = executionsStore.followExecutionDependencies({id: params.id as string, expandAll: true});
        sse.value.onmessage = (event: MessageEvent) => {
            const isEnd = event && event.lastEventId === "end-all";

            if (isEnd) closeSSE();

            const message = JSON.parse(event.data);

            if (!message.state) return;

            messages.value.push(message);
        };

        sse.value.onerror = () => {
            coreStore.message = {variant: "error", title: t("error"), message: t("something_went_wrong.loading_execution")};
        };
    };

    const closeSSE = () => {
        if (!sse.value) return;

        sse.value.close();
        sse.value = undefined;
    };

    onBeforeUnmount(() => {
        if (subtype === EXECUTION) closeSSE();
    });

    return {
        getElements: () => elements.data,
        loading,
        selectedNodeID,
        selectNode,
        handlers: {
            zoomIn: () => cy.zoom({level: cy.zoom() + 0.1, renderedPosition: cy.getElementById(selectedNodeID.value!).renderedPosition()}),
            zoomOut: () => cy.zoom({level: cy.zoom() - 0.1, renderedPosition: cy.getElementById(selectedNodeID.value!).renderedPosition()}),
            clearSelection: () => {
                clearClasses(cy, subtype);
                selectedNodeID.value = undefined;
                fit(cy);
            },
            fit: () => fit(cy)
        }
    };
}

/**
 * Transforms an API response containing nodes and edges into
 * Cytoscape-compatible elements with the given subtype.
 *
 * @param response - The API response object containing `nodes` and `edges` arrays.
 * @param subtype - The node subtype, either `"FLOW"` or `"EXECUTION"`.
 * @returns An array of cytoscape elements with correctly typed nodes and edges.
 */
export function transformResponse(response: { nodes: { uid: string; namespace: string; id: string; revision?: string }[]; edges: { source: string; target: string }[] }, subtype: typeof FLOW | typeof EXECUTION): Element[] {
  const nodes: Node[] = response.nodes.map((node) => ({id: node.uid, type: NODE, flow: node.id, namespace: node.namespace, metadata: subtype === FLOW ? {subtype: FLOW, revision: node.revision ? Number(node.revision) : undefined} : {subtype: EXECUTION}}));
  const edges: Edge[] = response.edges.map((edge) => ({id: uuid(), type: EDGE, source: edge.source, target: edge.target}));
  return [...nodes.map((node) => ({data: node} as Element)), ...edges.map((edge) => ({data: edge} as Element))];
}
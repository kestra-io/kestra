import {onMounted, onBeforeUnmount, nextTick, watch, ref} from "vue";

import {useCoreStore} from "../../../stores/core";
import {useFlowStore} from "../../../stores/flow";
import {useExecutionsStore} from "../../../stores/executions";
import {useNamespacesStore} from "override/stores/namespaces";
import {useMiscStore} from "override/stores/misc";

import {useI18n} from "vue-i18n";

import type {Ref} from "vue";

import type {RouteParams} from "vue-router";

import {v4 as uuid} from "uuid";

import throttle from "lodash/throttle";

import cytoscape from "cytoscape";

import {State, cssVariable} from "@kestra-io/ui-libs";

import {NODE, EDGE, FLOW, EXECUTION, NAMESPACE, type Node, type Edge, type Element} from "../utils/types";
import {getRandomNumber, getDependencies} from "../../../../tests/fixtures/dependencies/getDependencies";

import {edgeColors, getStyle} from "../utils/style";
const SELECTED = "selected", FADED = "faded", HOVERED = "hovered", EXECUTIONS = "executions";

const options: Omit<cytoscape.CytoscapeOptions, "container" | "elements"> & {elements?: Element[]} = {
    minZoom: 0.1,
    maxZoom: 2,
    wheelSensitivity: 0.025,
};

/**
 * Layout options for the COSE layout algorithm used in cytoscape.
 *
 * @see {@link https://js.cytoscape.org/#layouts/cose | COSE layout options documentation}
 */
const layout: cytoscape.CoseLayoutOptions = {
    name: "cose",

    // Physical forces
    nodeRepulsion: 10_000_000,
    edgeElasticity: 100,
    idealEdgeLength: 250,

    // Gravity settings
    gravity: 0.05,

    // Layout iterations & cooling
    numIter: 30_000,
    initialTemp: 200,
    minTemp: 1,

    // Spacing and padding
    padding: 50,
    componentSpacing: 200,

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
 * Retrieves the execution state color for a given cytoscape node or a provided state string.
 *
 * - If a `state` is provided, it will be used directly.
 * - If not, it attempts to read the state from the node's `metadata`.
 * - Falls back to a default color if no state is available.
 *
 * @param node - Optional cytoscape node to extract the state from.
 * @param state - Optional direct state string.
 * @returns The color associated with the execution state, or a fallback if missing.
 */
function getStateColor(node?: cytoscape.NodeSingular, state?: string): string {
    const resolved = state ?? node?.data("metadata")?.state;
    return resolved ? State.getStateColor(resolved) : cssVariable("--ks-dependencies-node-background")!;
}

/**
 * Applies execution state colors to specified nodes in the cytoscape graph.
 *
 * - Removes all custom classes from nodes and edges.
 * - Sets each node’s background and border color based on its execution state.
 *
 * @param cy - The cytoscape core instance managing the graph.
 * @param nodes - Optional array of nodes to apply colors to. If not provided, all nodes are used.
 */
function setExecutionNodeColors(cy: cytoscape.Core, nodes?: cytoscape.NodeSingular[]): void {
    // Remove all existing custom classes from the graph
    clearClasses(cy, EXECUTION);

    // Apply state-based colors to provided nodes or all nodes
    (nodes ?? cy.nodes()).forEach((node) => {
        node.style({
            "background-color": getStateColor(node),
            "border-color": getStateColor(node),
        });
    });
}

/**
 * Throttled function that applies the given color to specified edges in the cytoscape graph.
 *
 * - Removes the `FADED` class and adds the `EXECUTIONS` class to each edge.
 * - Sets the edge’s line and arrow colors using the provided color.
 * - The operation is throttled to limit how often edge styles are updated, preventing performance issues
 *   when called frequently in rapid succession (e.g., event streams).
 *
 * @param edges - Collection of edges to apply colors to.
 * @param color - The color to apply to the edges.
 * @remarks
 * The throttling interval is currently set to 300ms. Leading and trailing calls are both executed,
 * ensuring the first and last updates within the interval are applied.
 */
const setExecutionEdgeColors = throttle(
    (edges: cytoscape.EdgeCollection, color: string) => {
        edges.forEach((edge) => {
            edge.removeClass(FADED).addClass(EXECUTIONS).style({
                "line-color": color,
                "target-arrow-color": color,
            });
        });
    },
    300,
    {leading: true, trailing: true},
);

/**
 * Removes the specified CSS classes from all elements (nodes and edges) in the cytoscape instance.
 *
 * If the subtype is `EXECUTION`, it also reapplies the default edge styling.
 *
 * This function is typically used to clear selection, hover, and execution-related classes
 * before applying new styles or resetting the graph state.
 *
 * @param cy - The cytoscape core instance containing the graph elements.
 * @param subtype - The dependency subtype, either `FLOW`, `EXECUTION` or `NAMESPACE`.
 *                  Edge styles are only reset when subtype is `EXECUTION`.
 * @param classes - An array of class names to remove from all elements.
 *                  Defaults to [`selected`, `faded`, `hovered`, `executions`].
 */
export function clearClasses(cy: cytoscape.Core, subtype: typeof FLOW | typeof EXECUTION | typeof NAMESPACE | typeof ASSET, classes: string[] = [SELECTED, FADED, HOVERED, EXECUTIONS]): void {
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
 * - Clears all existing states (`selected`, `faded`, `hovered`, `executions`) from the graph.
 * - Applies the `FADED` class to all elements by default.
 * - Marks the clicked node as `SELECTED`.
 * - Marks its direct edges and first-level child nodes as `SELECTED`.
 * - If the subtype is `EXECUTION`, styles the connected edges with the appropriate execution color.
 * - Updates the provided Vue ref with the selected node’s ID.
 * - Smoothly centers and zooms the viewport on the selected node.
 *
 * Coloring logic is based on: https://github.com/kestra-io/kestra/issues/10925#issuecomment-3245743846
 *
 * @param cy - The cytoscape core instance managing the graph.
 * @param node - The node element to select.
 * @param selected - Vue ref storing the currently selected node ID.
 * @param subtype - Determines how connected elements are highlighted (`FLOW`, `EXECUTION` or `NAMESPACE`).
 * @param id - Optional explicit ID to assign to the ref (defaults to the node’s own ID).
 */
function selectHandler(cy: cytoscape.Core, node: cytoscape.NodeSingular, selected: Ref<Node["id"] | undefined>, subtype: typeof FLOW | typeof EXECUTION | typeof NAMESPACE | typeof ASSET, id?: Node["id"]): void {
    // Clear all existing classes
    clearClasses(cy, subtype);

    // Fade all elements in the graph
    cy.elements().addClass(FADED);

    // Mark the clicked node as selected
    node.addClass(SELECTED);

    // Highlight direct edges and first-level child nodes of the selected node
    const edges = node.connectedEdges();
    const children = edges.connectedNodes();

    edges.addClass(SELECTED);
    children.addClass(SELECTED);

    // If subtype is EXECUTION, apply execution-specific edge styling
    if (subtype === EXECUTION) {
        setExecutionEdgeColors(edges, getStateColor(node));
    }

    // Update the Vue ref with the selected node's ID
    selected.value = id ?? node.id();

    // Smoothly center and zoom the viewport on the selected node
    cy.animate({center: {eles: node}, zoom: 1.2}, {duration: 500});
}

/**
 * Sets up hover handlers for nodes and edges.
 *
 * @param cy - The cytoscape core instance containing the graph.
 */
function hoverHandler(cy: cytoscape.Core): void {
    // Node hover: highlight node + connected edges + connected nodes
    cy.on("mouseover", "node", (event: cytoscape.EventObject) => {
        const node = event.target;
        node.union(node.connectedEdges())
            .union(node.connectedEdges().connectedNodes())
            .addClass(HOVERED);
    });

    cy.on("mouseout", "node", (event: cytoscape.EventObject) => {
        const node = event.target;
        node.union(node.connectedEdges())
            .union(node.connectedEdges().connectedNodes())
            .removeClass(HOVERED);
    });

    // Edge hover: highlight only the edge itself
    cy.on("mouseover", "edge", (event: cytoscape.EventObject) => event.target.addClass(HOVERED));
    cy.on("mouseout", "edge", (event: cytoscape.EventObject) => event.target.removeClass(HOVERED));
}

/**
 * Initializes and manages a cytoscape instance within a Vue component.
 *
 * @param container - Vue ref pointing to the DOM element that hosts the cytoscape graph.
 * @param subtype - Dependency subtype, either `FLOW`, `EXECUTION` or `NAMESPACE`. Defaults to `FLOW`.
 * @param initialNodeID - Optional ID of the node to preselect after layout completes.
 * @param params - Vue Router params, expected to include `id` and `namespace`.
 * @param isTesting - When true, bypasses API data fetching and uses mock/test data.
 * @returns An object with element getters, loading state, rendering state, selected node ID,
 *          selection helpers, and control handlers.
 */
export function useDependencies(
    container: Ref<HTMLElement | null>,
    subtype: typeof FLOW | typeof EXECUTION | typeof NAMESPACE | typeof ASSET = FLOW,
    initialNodeID: string,
    params: RouteParams,
    isTesting = false,
    fetchAssetDependencies?: () => Promise<{
        data: Element[];
        count: number;
    }>
) {
    const coreStore = useCoreStore();
    const flowStore = useFlowStore();
    const executionsStore = useExecutionsStore();
    const namespacesStore = useNamespacesStore();
    const miscStore = useMiscStore();

    watch(() => miscStore.theme, () => {
        if (!cy) return;

        // Update the stylesheet so nodes and edges reflect the new theme colors
        cy.style().fromJson(getStyle()).update();
    });

    const {t} = useI18n({useScope: "global"});

    let cy: cytoscape.Core;

    const isLoading = ref(true);
    const isRendering = ref(true);

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

    const elements = ref<{
        data: cytoscape.ElementDefinition[];
        count: number;
    }>({
        data: [],
        count: 0,
    });
    onMounted(async () => {
        if (isTesting) {
            if (!container.value) {
                isLoading.value = false;
                return;
            }

            elements.value = {data: getDependencies({subtype}), count: getRandomNumber(1, 100)};
            isLoading.value = false;
        } else {
            try {
                if (fetchAssetDependencies) {
                    const result = await fetchAssetDependencies();
                    elements.value = {
                        data: result.data,
                        count: result.count
                    };
                    isLoading.value = false;
                } else if (subtype === NAMESPACE) {
                    const {data} = await namespacesStore.loadDependencies({
                        namespace: params.id as string,
                    });
                    const nodes = data.nodes ?? [];
                    elements.value = {
                        data: transformResponse(data, NAMESPACE),
                        count: new Set(nodes.map((r: { uid: string }) => r.uid)).size,
                    };
                    isLoading.value = false;
                } else {
                    const result = await flowStore.loadDependencies(
                        {
                            id: (subtype === FLOW ? params.id : params.flowId) as string,
                            namespace: params.namespace as string,
                            subtype,
                        },
                        false
                    );
                    elements.value = {data: result.data ?? [], count: result.count};
                    isLoading.value = false;
                }
            } catch (error) {
                console.error(`Failed to load ${subtype} dependencies:`, error);
                elements.value = {data: [], count: 0};
                isLoading.value = false;
            }
        }

        if (isTesting && container.value) {
            cy = cytoscape({container: container.value, layout, ...options, style: getStyle(), elements: elements.value.data});
        } else if (!isTesting && elements.value.data.length > 0) {
            await nextTick(); // Wait for the container to be available in the DOM
            
            if (!container.value) return;

            cy = cytoscape({container: container.value, layout, ...options, style: getStyle(), elements: elements.value.data});
        }

        if (!cy) return;

        if (subtype === EXECUTION) nextTick(() => openSSE());

        // Hide nodes immediately after initialization to avoid visual flickering or rearrangement during layout setup
        cy.ready(() => cy.nodes().style("display", "none"));

        // Dynamically size nodes based on connectivity
        setNodeSizes(cy);

        // Apply execution state colors to each node
        if (subtype === EXECUTION) setExecutionNodeColors(cy);

        // Setup hover handlers for nodes and edges
        hoverHandler(cy);

        // Animate dashed selected edges
        let dashOffset = 0;
        function animateEdges(): void {
            dashOffset -= 0.25;
            cy.edges(`.${FADED}, .${EXECUTIONS}`).style("line-dash-offset", dashOffset);
            requestAnimationFrame(animateEdges);
        }
        animateEdges();

        // Node tap handler using selectHandler
        cy.on("tap", "node", (event: cytoscape.EventObject) => {
            const node = event.target;

            selectHandler(cy, node, selectedNodeID, subtype);
        });

        cy.on("layoutstop", () => {           
            // Reveal nodes after layout rendering completes
            isRendering.value = false;
            cy.nodes().style("display", "element");

            const node = isTesting ? cy.nodes()[0] : cy.nodes().filter((n) => n.data("flow") === initialNodeID);
            if (subtype === NAMESPACE) fit(cy); // If the subtype is NAMESPACE, fit the entire graph in the viewport
            else if (node) selectHandler(cy, node, selectedNodeID, subtype); // Else, preselect the proper node after layout rendering completes
        });
    });

    const sse = ref();
    const messages = ref<Record<string, any>[]>([]);

    watch(
        messages,
        (newMessages) => {
            if (!newMessages?.length) return;

            const message = newMessages[newMessages.length - 1]; // Only process the newest event message

            const matched = cy.getElementById(`${message.tenantId}_${message.namespace}_${message.flowId}`);

            if (matched.nonempty()) {
                const state = message.state.current;

                matched.data({...matched.data(), metadata: {...matched.data("metadata"), id: message.executionId, state}});

                setExecutionNodeColors(cy, [matched]);
                setExecutionEdgeColors(matched.connectedEdges(), getStateColor(undefined, state));
            }
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
        getElements: () => elements.value.data,
        isLoading,
        isRendering,
        selectedNodeID,
        selectNode,
        handlers: {
            zoomIn: () =>
                cy.zoom({
                    level: cy.zoom() + 0.1,
                    renderedPosition: cy.getElementById(selectedNodeID.value!).renderedPosition(),
                }),
            zoomOut: () =>
                cy.zoom({
                    level: cy.zoom() - 0.1,
                    renderedPosition: cy.getElementById(selectedNodeID.value!).renderedPosition(),
                }),
            clearSelection: () => {
                clearClasses(cy, subtype);
                selectedNodeID.value = undefined;
                fit(cy);
            },
            fit: () => fit(cy),
        },
    };
}

/**
 * Transforms an API response containing nodes and edges into
 * Cytoscape-compatible elements with the given subtype.
 *
 * @param response - The API response object containing `nodes` and `edges` arrays.
 * @param subtype - The node subtype, either `FLOW`, `EXECUTION`, or `NAMESPACE`.
 * @returns An array of cytoscape elements with correctly typed nodes and edges.
 */
export function transformResponse(response: {nodes: { uid: string; namespace: string; id: string }[]; edges: { source: string; target: string }[];}, subtype: typeof FLOW | typeof EXECUTION | typeof NAMESPACE): Element[] {
    const nodes: Node[] = response.nodes.map((node) => ({
        id: node.uid,
        type: NODE,
        flow: node.id,
        namespace: node.namespace,
        metadata: {subtype},
    }));
    const edges: Edge[] = response.edges.map((edge) => ({
        id: uuid(),
        type: EDGE,
        source: edge.source,
        target: edge.target,
    }));

    return [
        ...nodes.map((node) => ({data: node}) as Element),
        ...edges.map((edge) => ({data: edge}) as Element),
    ];
}

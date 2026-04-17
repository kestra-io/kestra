import {onBeforeUnmount, onMounted, nextTick, watch, ref, computed} from "vue";

import {useCoreStore} from "../../../stores/core";
import {useFlowStore} from "../../../stores/flow";
import {useExecutionsStore} from "../../../stores/executions";
import {useNamespacesStore} from "override/stores/namespaces";
import {useMiscStore} from "override/stores/misc";

import {useI18n} from "vue-i18n";

import type {Ref, ComputedRef} from "vue";

import type {RouteParams} from "vue-router";

import {v4 as uuid} from "uuid";

import {State, cssVar} from "@kestra-io/ui-design-system";
import type {KsGraphNode, KsGraphEdge} from "@kestra-io/ui-design-system";

import {NODE, EDGE, FLOW, EXECUTION, NAMESPACE, ASSET} from "../utils/types";
import type {Types, Node, Edge, Element} from "../utils/types";

import {getRandomNumber, getDependencies} from "../../../../tests/fixtures/dependencies/getDependencies";

// ─── CSS variable maps ────────────────────────────────────────────────────────

const NODE_BG = {
    default:  "--ks-dependencies-node-background-default",
    faded:    "--ks-dependencies-node-background-faded",
    selected: "--ks-dependencies-node-background-selected",
    assets:   "--ks-dependencies-node-background-assets",
} as const;

const NODE_BORDER = {
    default:  "--ks-dependencies-node-border-default",
    faded:    "--ks-dependencies-node-border-faded",
    selected: "--ks-dependencies-node-border-selected",
    assets:   "--ks-dependencies-node-border-assets",
} as const;

const EDGE_COLOR = {
    default:  "--ks-dependencies-edge-default",
    faded:    "--ks-dependencies-edge-faded",
    selected: "--ks-dependencies-edge-selected",
} as const;

// ─── KsGraph instance contract ────────────────────────────────────────────────

interface KsGraphRef {
    zoomIn(): void;
    zoomOut(): void;
    fit(): void;
    exportAsImage(type: "jpeg" | "png", filename?: string): void;
    getEchartsInstance(): unknown;
}

// ─── Node size helpers ────────────────────────────────────────────────────────

/**
 * Computes per-node symbol sizes based on edge connectivity.
 * Size = baseSize + (connectedEdges * scale), capped at maxSize.
 */
function buildEdgeCounts(elements: Element[]): Map<string, number> {
    const counts = new Map<string, number>();
    elements.forEach((el) => {
        if (el.data.type !== EDGE) return;
        const edge = el.data as Edge;
        counts.set(edge.source, (counts.get(edge.source) ?? 0) + 1);
        counts.set(edge.target, (counts.get(edge.target) ?? 0) + 1);
    });
    return counts;
}

function nodeSize(id: string, edgeCounts: Map<string, number>, base = 20, scale = 2, max = 100): number {
    return Math.min(base + (edgeCounts.get(id) ?? 0) * scale, max);
}

// ─── Element transformation ───────────────────────────────────────────────────

/**
 * Transforms an API response containing nodes and edges into
 * dependency Element[] with the given subtype.
 */
export function transformResponse(
    response: { nodes: { uid: string; namespace: string; id: string }[]; edges: { source: string; target: string }[] },
    subtype: Types,
): Element[] {
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

// ─── Main composable ──────────────────────────────────────────────────────────

/**
 * Manages a KsGraph-based dependency visualization inside a Vue component.
 *
 * @param graphRef    - Template ref pointing to the KsGraph component instance.
 * @param subtype     - Dependency subtype: FLOW, EXECUTION, NAMESPACE, or ASSET.
 * @param initialNodeID - ID of the node to pre-select after the first render.
 * @param params      - Vue Router params (id, namespace, flowId).
 * @param isTesting   - When true, uses generated fixture data instead of the API.
 * @param fetchAssetDependencies - Custom async fetcher for ASSET subtypes.
 */
export function useDependencies(
    graphRef: Ref<KsGraphRef | null>,
    subtype: Types = FLOW,
    initialNodeID: string,
    params: RouteParams,
    isTesting = false,
    fetchAssetDependencies?: () => Promise<{data: Element[]; count: number}>,
) {
    const coreStore = useCoreStore();
    const flowStore = useFlowStore();
    const executionsStore = useExecutionsStore();
    const namespacesStore = useNamespacesStore();
    const miscStore = useMiscStore();

    const {t} = useI18n({useScope: "global"});

    const isLoading = ref(true);
    const isRendering = ref(true);

    const selectedNodeID: Ref<Node["id"] | undefined> = ref(undefined);

    /** IDs of nodes that belong to the current table-filter result (null = no filter). */
    const shownNodeIDs = ref<Set<string> | null>(null);

    const elements = ref<{data: Element[]; count: number}>({data: [], count: 0});

    // ─── Derived graph topology ───────────────────────────────────────────────

    /** Set of node IDs connected to the selected node (includes the selected node itself). */
    const selectedNeighborIDs: ComputedRef<Set<string>> = computed(() => {
        if (!selectedNodeID.value) return new Set();
        const neighbors = new Set<string>([selectedNodeID.value]);
        elements.value.data.forEach((el) => {
            if (el.data.type !== EDGE) return;
            const edge = el.data as Edge;
            if (edge.source === selectedNodeID.value || edge.target === selectedNodeID.value) {
                neighbors.add(edge.source);
                neighbors.add(edge.target);
            }
        });
        return neighbors;
    });

    /** Set of edge IDs connected to the selected node. */
    const selectedEdgeIDs: ComputedRef<Set<string>> = computed(() => {
        if (!selectedNodeID.value) return new Set();
        const ids = new Set<string>();
        elements.value.data.forEach((el) => {
            if (el.data.type !== EDGE) return;
            const edge = el.data as Edge;
            if (edge.source === selectedNodeID.value || edge.target === selectedNodeID.value) {
                ids.add(edge.id);
            }
        });
        return ids;
    });

    // ─── ECharts data (reactive, rebuilt on state changes) ───────────────────

    const graphNodes: ComputedRef<KsGraphNode[]> = computed(() => {
        void miscStore.theme; // recompute cssVar calls when theme switches
        const edgeCounts = buildEdgeCounts(elements.value.data);
        const hasSelection = selectedNodeID.value !== undefined;
        const hasFilter = shownNodeIDs.value !== null;

        return elements.value.data
            .filter((el): el is {data: Node} => el.data.type === NODE)
            .map(({data: node}) => {
                const isSelected  = node.id === selectedNodeID.value;
                const isNeighbor  = hasSelection && selectedNeighborIDs.value.has(node.id);
                const isFaded     = hasSelection && !isSelected && !isNeighbor;
                const isDimmed    = hasFilter && !shownNodeIDs.value!.has(node.id);
                const isAsset     = node.metadata.subtype === ASSET;

                // For EXECUTION subtype, use the execution state color when available.
                const execState   = subtype === EXECUTION
                    ? (node.metadata as {state?: string}).state
                    : undefined;
                const execColor   = execState ? State.getStateColor(execState) : undefined;

                let bgColor: string;
                let borderColor: string;
                let opacity = 1;

                if (isDimmed) {
                    bgColor     = cssVar(NODE_BG.faded);
                    borderColor = cssVar(NODE_BORDER.faded);
                    opacity     = 0.25;
                } else if (isFaded) {
                    bgColor     = cssVar(NODE_BG.faded);
                    borderColor = cssVar(NODE_BORDER.faded);
                    opacity     = 0.75;
                } else if (isSelected || isNeighbor) {
                    bgColor     = execColor ?? cssVar(NODE_BG.selected);
                    borderColor = execColor ?? cssVar(NODE_BORDER.selected);
                } else if (isAsset) {
                    bgColor     = execColor ?? cssVar(NODE_BG.assets);
                    borderColor = execColor ?? cssVar(NODE_BORDER.assets);
                } else {
                    bgColor     = execColor ?? cssVar(NODE_BG.default);
                    borderColor = execColor ?? cssVar(NODE_BORDER.default);
                }

                return {
                    id:         node.id,
                    name:       node.id,
                    symbolSize: nodeSize(node.id, edgeCounts),
                    itemStyle: {
                        color:       bgColor,
                        borderColor,
                        borderWidth: 2,
                        opacity,
                    },
                    label: {
                        show:            true,
                        formatter:       node.flow,
                        position:        "bottom",
                        color:           cssVar("--ks-content-primary", isDimmed ? 0.35 : isFaded ? 0.75 : undefined),
                        fontSize:        10,
                        textBorderWidth: 0,
                    },
                };
            });
    });

    const graphEdges: ComputedRef<KsGraphEdge[]> = computed(() => {
        void miscStore.theme; // recompute cssVar calls when theme switches
        const hasSelection = selectedNodeID.value !== undefined;
        const hasFilter    = shownNodeIDs.value !== null;

        return elements.value.data
            .filter((el): el is {data: Edge} => el.data.type === EDGE)
            .map(({data: edge}) => {
                const isSelected   = selectedEdgeIDs.value.has(edge.id);
                const isFaded      = hasSelection && !isSelected;
                const isEdgeDimmed = hasFilter &&
                    (!shownNodeIDs.value!.has(edge.source) || !shownNodeIDs.value!.has(edge.target));

                // For EXECUTION subtype, color selected edges with the source node's state color.
                const execState    = subtype === EXECUTION && isSelected
                    ? (() => {
                        const src = elements.value.data.find(
                            (el): el is {data: Node} =>
                                el.data.type === NODE && el.data.id === selectedNodeID.value,
                        );
                        return (src?.data.metadata as {state?: string})?.state;
                    })()
                    : undefined;
                const execColor    = execState ? State.getStateColor(execState) : undefined;

                let color: string;
                let opacity = 1;

                if (isEdgeDimmed) {
                    color   = cssVar(EDGE_COLOR.faded);
                    opacity = 0.1;
                } else if (isFaded) {
                    color   = cssVar(EDGE_COLOR.faded);
                    opacity = 0.35;
                } else if (isSelected) {
                    color   = execColor ?? cssVar(EDGE_COLOR.selected);
                } else {
                    color   = cssVar(EDGE_COLOR.default);
                }

                return {
                    source:    edge.source,
                    target:    edge.target,
                    lineStyle: {
                        color,
                        opacity,
                        type:  isSelected ? "dashed" : "solid",
                        width: isSelected ? 2 : 1,
                    },
                };
            });
    });

    // ─── Selection ────────────────────────────────────────────────────────────

    /**
     * Selects a node by ID, updating the visual selection state reactively.
     */
    const selectNode = (id: Node["id"]): void => {
        const exists = elements.value.data.some(
            (el): el is {data: Node} => el.data.type === NODE && el.data.id === id,
        );
        if (!exists) return;
        selectedNodeID.value = id;
    };

    // ─── Data loading ─────────────────────────────────────────────────────────

    onMounted(async () => {
        if (isTesting) {
            elements.value = {data: getDependencies({subtype}), count: getRandomNumber(1, 100)};
            isLoading.value   = false;
            isRendering.value = false;
            if (subtype !== NAMESPACE) selectNode(elements.value.data.find(
                (el): el is {data: Node} => el.data.type === NODE,
            )?.data.id ?? initialNodeID);
        } else {
            try {
                if (fetchAssetDependencies) {
                    const result = await fetchAssetDependencies();
                    elements.value = {data: result.data, count: result.count};
                } else if (subtype === NAMESPACE) {
                    const {data} = await namespacesStore.loadDependencies({namespace: params.id as string});
                    const nodes = data.nodes ?? [];
                    elements.value = {
                        data:  transformResponse(data, NAMESPACE),
                        count: new Set(nodes.map((r: {uid: string}) => r.uid)).size,
                    };
                } else {
                    const result = await flowStore.loadDependencies(
                        {
                            id:       (subtype === FLOW ? params.id : params.flowId) as string,
                            namespace: params.namespace as string,
                            subtype:  subtype === FLOW ? FLOW : EXECUTION,
                        },
                        false,
                    );
                    elements.value = {data: result.data ?? [], count: result.count};
                }
            } catch (error) {
                console.error(`Failed to load ${subtype} dependencies:`, error);
                elements.value = {data: [], count: 0};
            }

            isLoading.value   = false;
            isRendering.value = false;

            if (subtype !== NAMESPACE && elements.value.data.length > 0) {
                // Wait for KsGraph to receive the new nodes prop and render.
                await nextTick();
                selectNode(initialNodeID);
            }
        }

        if (subtype === EXECUTION) nextTick(() => openSSE());
    });

    // ─── SSE (live execution state updates) ──────────────────────────────────

    const sse = ref();
    const messages = ref<Record<string, unknown>[]>([]);

    watch(
        messages,
        (newMessages) => {
            if (!newMessages?.length) return;

            const message = newMessages[newMessages.length - 1] as Record<string, any>;
            const nodeId  = `${message.tenantId}_${message.namespace}_${message.flowId}`;

            const idx = elements.value.data.findIndex(
                (el): el is {data: Node} =>
                    el.data.type === NODE && el.data.id === nodeId,
            );

            if (idx === -1) return;

            const el = elements.value.data[idx] as {data: Node};
            const state = message.state.current as string;

            // Replace the element to ensure Vue picks up the change.
            const updated = {
                data: {
                    ...el.data,
                    metadata: {...el.data.metadata, id: message.executionId, state},
                },
            };
            elements.value.data.splice(idx, 1, updated);
        },
        {deep: true},
    );

    const openSSE = () => {
        if (subtype !== EXECUTION) return;
        closeSSE();
        sse.value = executionsStore.followExecutionDependencies({id: params.id as string, expandAll: true});
        sse.value.onmessage = (event: MessageEvent) => {
            const isEnd = event?.lastEventId === "end-all";
            if (isEnd) closeSSE();
            const message = JSON.parse(event.data);
            if (!message.state) return;
            messages.value.push(message);
        };
        sse.value.onerror = () => {
            coreStore.message = {
                variant: "error",
                title:   t("error"),
                message: t("something_went_wrong.loading_execution"),
            };
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

    // ─── Public API ───────────────────────────────────────────────────────────

    return {
        /** Returns the raw Element[] used by the Table component. */
        getElements: () => elements.value.data,
        /** KsGraphNode[] for the KsGraph :nodes prop. */
        graphNodes,
        /** KsGraphEdge[] for the KsGraph :edges prop. */
        graphEdges,
        isLoading,
        isRendering,
        selectedNodeID,
        selectNode,
        /** Called from the KsGraph @node-click event. */
        handleNodeClick: (node: KsGraphNode) => selectNode(node.id as string),
        handlers: {
            zoomIn:        () => graphRef.value?.zoomIn(),
            zoomOut:       () => graphRef.value?.zoomOut(),
            clearSelection: () => {
                selectedNodeID.value = undefined;
                shownNodeIDs.value   = null;
                graphRef.value?.fit();
            },
            fit:           () => graphRef.value?.fit(),
            highlightShown: (nodeIDs: string[]) => {
                const allNodeCount = elements.value.data.filter((el) => el.data.type === NODE).length;
                shownNodeIDs.value  = nodeIDs.length >= allNodeCount ? null : new Set(nodeIDs);
            },
            exportAsImage: (type: "jpeg" | "png", nodeID?: string) => {
                const ts       = new Date().toISOString().slice(0, 19).replace(/:/g, "-");
                const filename = `dependencies-${nodeID}-${ts}.${type}`;
                graphRef.value?.exportAsImage(type, filename);
            },
        },
    };
}

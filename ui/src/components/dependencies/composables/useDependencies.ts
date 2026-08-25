import {onBeforeUnmount, onMounted, nextTick, watch, ref, computed} from "vue"
import type {Ref, ComputedRef} from "vue"
import type {RouteParams} from "vue-router"
import {useI18n} from "vue-i18n"
import {v4 as uuid} from "uuid"
import {State, cssVar} from "@kestra-io/design-system"
import type {KsGraphNode, KsGraphEdge} from "@kestra-io/design-system"
import {useCoreStore} from "../../../stores/core"
import {useFlowStore} from "../../../stores/flow"
import {useExecutionsStore} from "../../../stores/executions"
import {useNamespacesStore} from "override/stores/namespaces"
import {useMiscStore} from "override/stores/misc"
import {NODE, EDGE, FLOW, EXECUTION, NAMESPACE, ASSET, nodesOf, edgesOf} from "../utils/types"
import type {Types, Node, Edge, Element} from "../utils/types"

const NODE_BG = {
    default:  "--ks-dependencies-node-background-default",
    faded:    "--ks-dependencies-node-background-faded",
    selected: "--ks-dependencies-node-background-selected",
    hovered:  "--ks-dependencies-node-background-hovered",
    assets:   "--ks-dependencies-node-background-assets",
} as const

const NODE_BORDER = {
    default:  "--ks-dependencies-node-border-default",
    faded:    "--ks-dependencies-node-border-faded",
    selected: "--ks-dependencies-node-border-selected",
    hovered:  "--ks-dependencies-node-border-hovered",
    assets:   "--ks-dependencies-node-border-assets",
} as const

const EDGE_COLOR = {
    default:  "--ks-dependencies-edge-default",
    faded:    "--ks-dependencies-edge-faded",
    selected: "--ks-dependencies-edge-selected",
    hovered:  "--ks-dependencies-edge-hovered",
} as const

// Material Design "package-variant-closed" glyph: ECharts symbols are drawn on a canvas and
// cannot mount a Vue component, so the icon is embedded as an SVG `image://` symbol.
const ASSET_ICON_PATH =
    "M21,16.5C21,16.88 20.79,17.21 20.47,17.38L12.57,21.82C12.41,21.94 12.21,22 12,22C11.79,22 11.59,21.94 11.43,21.82L3.53,17.38C3.21,17.21 3,16.88 3,16.5V7.5C3,7.12 3.21,6.79 3.53,6.62L11.43,2.18C11.59,2.06 11.79,2 12,2C12.21,2 12.41,2.06 12.57,2.18L20.47,6.62C20.79,6.79 21,7.12 21,7.5V16.5M12,4.15L10.11,5.22L16,8.61L17.96,7.5L12,4.15M6.04,7.5L12,10.85L13.96,9.75L8.08,6.35L6.04,7.5M5,15.91L11,19.29V12.58L5,9.21V15.91M19,15.91V9.21L13,12.58V19.29L19,15.91Z"

/**
 * ECharts `image://` symbol for an asset node. Colours are baked into the SVG because ECharts
 * ignores itemStyle for image symbols; graphNodes rebuilds it on every state or theme change.
 */
function assetNodeSymbol(bgColor: string, borderColor: string, iconColor: string): string {
    const svg =
        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\">" +
        `<circle cx="12" cy="12" r="11" fill="${bgColor}" stroke="${borderColor}" stroke-width="1.5"/>` +
        `<path transform="translate(12 12) scale(0.55) translate(-12 -12)" fill="${iconColor}" d="${ASSET_ICON_PATH}"/>` +
        "</svg>"
    return `image://data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}


/** Which canvas the asset view shows. The chart only ever renders "force"; DagCanvas owns "dag". */
export type LayoutMode = "force" | "dag"

interface KsGraphRef {
    zoomIn(): void;
    zoomOut(): void;
    fit(): void;
    exportAsImage(type: "jpeg" | "png", filename?: string): void;
    getEchartsInstance(): unknown;
}

function buildEdgeCounts(elements: Element[]): Map<string, number> {
    const counts = new Map<string, number>()
    edgesOf(elements).forEach((edge) => {
        counts.set(edge.source, (counts.get(edge.source) ?? 0) + 1)
        counts.set(edge.target, (counts.get(edge.target) ?? 0) + 1)
    })
    return counts
}

/** Symbol size grows with connectivity, capped so hubs stay readable. */
function nodeSize(id: string, edgeCounts: Map<string, number>, base = 20, scale = 2, max = 100): number {
    return Math.min(base + (edgeCounts.get(id) ?? 0) * scale, max)
}

/** Transforms an API response of nodes and edges into dependency Element[] with the given subtype. */
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
    }))
    const edges: Edge[] = response.edges.map((edge) => ({
        id: uuid(),
        type: EDGE,
        source: edge.source,
        target: edge.target,
    }))

    return [
        ...nodes.map((node) => ({data: node}) as Element),
        ...edges.map((edge) => ({data: edge}) as Element),
    ]
}

/** Manages a KsGraph-based dependency visualization inside a Vue component. */
export function useDependencies(
    graphRef: Ref<KsGraphRef | null>,
    subtype: Types = FLOW,
    initialNodeID: string,
    params: RouteParams,
    fetchAssetDependencies?: () => Promise<{data: Element[]; count: number}>,
    /** Field the graph is grouped by; returns undefined for nodes it says nothing about. */
    groupOf: Ref<((node: Node) => string | undefined) | undefined> = ref(undefined),
    /** True only for the asset view: click-to-clear and dblclick-to-open are asset-only. */
    dagView = false,
) {
    const coreStore = useCoreStore()
    const flowStore = useFlowStore()
    const executionsStore = useExecutionsStore()
    const namespacesStore = useNamespacesStore()
    const miscStore = useMiscStore()

    const {t} = useI18n({useScope: "global"})

    const isLoading = ref(true)
    const isRendering = ref(true)

    const selectedNodeID: Ref<Node["id"] | undefined> = ref(undefined)

    const getChart = (): Record<string, any> | null =>
        graphRef.value?.getEchartsInstance?.() as Record<string, any> | null

    // chartNodes/chartEdges are frozen after the initial render; applyStylesToChart() then updates
    // styles imperatively with layout:"none" so ECharts never re-runs the force simulation.
    const chartNodes = ref<KsGraphNode[] | null>(null)
    const chartEdges = ref<KsGraphEdge[] | null>(null)
    /** Positions read back from ECharts once the force simulation has settled. */
    const capturedPositions = ref(new Map<string, {x: number; y: number}>())
    /** Only focusNode and fitGraph write it; anything else re-asserting it would yank the viewport back. */
    const viewState = ref<{zoom: number; center?: [number, number]}>({zoom: 1})

    /** Set when a node is double-clicked, so the view can open that node's own page. */
    const openedNodeID = ref<Node["id"] | undefined>(undefined)

    /** IDs matching the side table's filters, and IDs matching an isolated group. */
    const tableFilterIDs = ref<Set<string> | null>(null)
    const isolatedIDs = ref<Set<string> | null>(null)

    /** The pinned group, owned here so a canvas click can clear the isolation and the chip state together. */
    const activeGroup = ref<string | undefined>(undefined)

    /** Restricts the graph to one group; undefined shows all of them. */
    const isolateGroup = (key?: string): void => {
        // Only undefined means "no group": the ungrouped bucket is a real group keyed by the empty string.
        if (key === undefined) { isolatedIDs.value = null; return }
        const match = nodesOf(elements.value.data)
            .filter((node) => (laneOf.value?.(node.id) ?? "") === key)
            .map((node) => node.id)
        isolatedIDs.value = match.length ? new Set(match) : null
    }

    const toggleGroup = (key: string): void => {
        activeGroup.value = activeGroup.value === key ? undefined : key
        isolateGroup(activeGroup.value)
    }

    const clearGroup = (): void => {
        activeGroup.value = undefined
        isolateGroup(undefined)
    }

    // Kept apart and intersected: the table rewrites its set on every keystroke, which would
    // otherwise silently clear a group the user had isolated.
    const shownNodeIDs = computed<Set<string> | null>(() => {
        const [table, isolated] = [tableFilterIDs.value, isolatedIDs.value]
        if (!table) return isolated
        if (!isolated) return table
        return new Set([...table].filter((id) => isolated.has(id)))
    })

    const elements = ref<{data: Element[]; count: number}>({data: [], count: 0})

    /** Node ids to their group, when a grouping field is selected. */
    const laneOf = computed(() => {
        const accessor = groupOf.value
        if (!accessor) return undefined

        const byID = new Map(nodesOf(elements.value.data).map((node) => [node.id, accessor(node)]))
        return (id: string) => byID.get(id)
    })

    function neighborIDs(anchorID: string | undefined): Set<string> {
        if (!anchorID) return new Set()
        const neighbors = new Set<string>([anchorID])
        edgesOf(elements.value.data).forEach((edge) => {
            if (edge.source === anchorID || edge.target === anchorID) {
                neighbors.add(edge.source)
                neighbors.add(edge.target)
            }
        })
        return neighbors
    }

    function connectedEdgeIDs(anchorID: string | undefined): Set<string> {
        if (!anchorID) return new Set()
        const ids = new Set<string>()
        edgesOf(elements.value.data).forEach((edge) => {
            if (edge.source === anchorID || edge.target === anchorID) ids.add(edge.id)
        })
        return ids
    }

    /** Set of node IDs connected to the selected node (includes the selected node itself). */
    const selectedNeighborIDs: ComputedRef<Set<string>> = computed(() => neighborIDs(selectedNodeID.value))

    /** Set of edge IDs connected to the selected node. */
    const selectedEdgeIDs: ComputedRef<Set<string>> = computed(() => connectedEdgeIDs(selectedNodeID.value))

    // Hover highlighting is ECharts' own emphasis.focus:"adjacency"; blur.itemStyle mirrors the
    // base style so selection colours survive while another node is hovered.
    const graphNodes: ComputedRef<KsGraphNode[]> = computed(() => {
        void miscStore.theme // recompute cssVar calls when theme switches
        const edgeCounts   = buildEdgeCounts(elements.value.data)
        const hasSelection = selectedNodeID.value !== undefined
        const hasFilter    = shownNodeIDs.value !== null
        const assetIconColor = cssVar("--ks-text-primary")

        return nodesOf(elements.value.data)
            .map((node) => {
                const isSelected = node.id === selectedNodeID.value
                const isNeighbor = hasSelection && selectedNeighborIDs.value.has(node.id) && !isSelected
                const isFaded    = hasSelection && !isSelected && !isNeighbor
                const isDimmed   = hasFilter && !shownNodeIDs.value!.has(node.id)
                const isAsset    = node.metadata.subtype === ASSET

                const execState  = subtype === EXECUTION
                    ? (node.metadata as {state?: string}).state
                    : undefined
                const execColor  = execState ? State.getStateColor(execState) : undefined

                let bgColor: string
                let borderColor: string
                let opacity = 1

                if (isDimmed) {
                    bgColor     = cssVar(NODE_BG.faded)
                    borderColor = cssVar(NODE_BORDER.faded)
                    opacity     = 0.25
                } else if (isSelected || isNeighbor) {
                    bgColor     = execColor ?? cssVar(NODE_BG.selected)
                    borderColor = execColor ?? cssVar(NODE_BORDER.selected)
                } else if (isFaded) {
                    // Selection is a focus inside the filter's scope, so an in-scope node keeps its colour and only softens.
                    const inScope = hasFilter && shownNodeIDs.value!.has(node.id)
                    const scopeBG = isAsset ? NODE_BG.assets : NODE_BG.default
                    const scopeBorder = isAsset ? NODE_BORDER.assets : NODE_BORDER.default

                    bgColor     = inScope ? (execColor ?? cssVar(scopeBG)) : cssVar(NODE_BG.faded)
                    borderColor = inScope ? (execColor ?? cssVar(scopeBorder)) : cssVar(NODE_BORDER.faded)
                    opacity     = 0.75
                } else if (isAsset) {
                    bgColor     = execColor ?? cssVar(NODE_BG.assets)
                    borderColor = execColor ?? cssVar(NODE_BORDER.assets)
                } else {
                    bgColor     = execColor ?? cssVar(NODE_BG.default)
                    borderColor = execColor ?? cssVar(NODE_BORDER.default)
                }

                const baseItemStyle = {color: bgColor, borderColor, borderWidth: 2, opacity}
                const labelColor    = cssVar("--ks-text-primary", isDimmed ? 0.35 : isFaded ? 0.75 : undefined)

                return {
                    id:         node.id,
                    name:       node.id,
                    symbolSize: nodeSize(node.id, edgeCounts),
                    ...(isAsset ? {symbol: assetNodeSymbol(bgColor, borderColor, assetIconColor)} : {}),
                    itemStyle:  baseItemStyle,
                    emphasis: {
                        itemStyle: {
                            color:       cssVar(NODE_BG.hovered),
                            borderColor: cssVar(NODE_BORDER.hovered),
                            borderWidth: 2,
                            opacity:     1,
                        },
                        label: {show: true, color: cssVar("--ks-text-primary")},
                    },
                    blur: {
                        itemStyle: baseItemStyle,
                        label:     {color: cssVar("--ks-text-primary")},
                    },
                    // Asset ids are long, so the asset graph reveals labels on hover only.
                    label: {
                        show:            subtype !== ASSET,
                        formatter:       node.flow,
                        position:        "bottom",
                        color:           labelColor,
                        fontSize:        10,
                        textBorderWidth: 0,
                    },
                }
            })
    })

    const graphEdges: ComputedRef<KsGraphEdge[]> = computed(() => {
        void miscStore.theme // recompute cssVar calls when theme switches
        const hasSelection = selectedNodeID.value !== undefined
        const hasFilter    = shownNodeIDs.value !== null

        // For EXECUTION subtype, selected edges take the selected node's state color.
        const selectedState = subtype === EXECUTION
            ? (nodesOf(elements.value.data).find((node) => node.id === selectedNodeID.value)?.metadata as {state?: string} | undefined)?.state
            : undefined
        const selectedColor = selectedState ? State.getStateColor(selectedState) : undefined

        return edgesOf(elements.value.data)
            .map((edge) => {
                const isSelected   = selectedEdgeIDs.value.has(edge.id)
                const isFaded      = hasSelection && !isSelected
                const isEdgeDimmed = hasFilter &&
                    (!shownNodeIDs.value!.has(edge.source) || !shownNodeIDs.value!.has(edge.target))
                const execColor    = isSelected ? selectedColor : undefined

                let color: string
                let opacity = 1

                if (isEdgeDimmed) {
                    color   = cssVar(EDGE_COLOR.faded)
                    opacity = 0.1
                } else if (isSelected) {
                    color   = execColor ?? cssVar(EDGE_COLOR.selected)
                } else if (isFaded) {
                    color   = cssVar(EDGE_COLOR.faded)
                    opacity = 0.35
                } else {
                    color   = cssVar(EDGE_COLOR.default)
                }

                const baseLineStyle = {
                    color,
                    opacity,
                    type:  isSelected ? "dashed" : "solid",
                    width: isSelected ? 2 : 1,
                }

                return {
                    source:    edge.source,
                    target:    edge.target,
                    lineStyle: baseLineStyle,
                    emphasis:  {lineStyle: {color: cssVar(EDGE_COLOR.hovered), opacity: 1, type: "solid", width: 2}},
                    blur:      {lineStyle: baseLineStyle},
                }
            })
    })

    /** Mirrors a hover in the side table onto the canvas, and clears it when it leaves. */
    const highlightNode = (id?: Node["id"]): void => {
        const chart = getChart()
        if (!chart) return
        chart.dispatchAction({type: "downplay", seriesIndex: 0})
        if (id) chart.dispatchAction({type: "highlight", seriesIndex: 0, name: id})
    }

    const focusNode = (id: Node["id"]): void => {
        if (!id) return
        const pos = capturedPositions.value.get(id)
        if (!pos) return
        const chart = getChart()
        if (!chart) return

        // Clear any stuck hover emphasis (mouseout may not fire when clicking a table row).
        chart.dispatchAction({type: "downplay", seriesIndex: 0})
        // `center` is in data coordinates, so this places the selected node at canvas centre.
        viewState.value = {zoom: 1.8, center: [pos.x, pos.y]}
        applyView(chart)
    }

    const selectNode = (id: Node["id"]): void => {
        if (!nodesOf(elements.value.data).some((node) => node.id === id)) return
        selectedNodeID.value = id
    }

    /** Drops both lenses together: clearing only one desyncs the chip row from what is dimmed. */
    const clearFilters = (): void => {
        tableFilterIDs.value = null
        clearGroup()
    }

    // Also re-frames: the initial auto-selection zooms in, so clearing must undo that.
    const clearSelection = (): void => {
        selectedNodeID.value = undefined
        clearFilters()
        fitGraph()
    }

    /** Camera sync for every view; only the asset view also gets bare-canvas click-to-clear. */
    const bindCanvasClicks = (): void => {
        requestAnimationFrame(() => {
            const chart = getChart()
            const zr = chart?.getZr?.()
            if (!zr || zr.ksDependenciesBound) return
            zr.ksDependenciesBound = true
            chart?.on?.("graphRoam", () => {
                const series = (chart.getOption?.() as Record<string, any> | undefined)?.series?.[0]
                if (series?.zoom !== undefined) viewState.value = {zoom: series.zoom, center: series.center}
            })
            if (!dagView) return
            // Bare-canvas click drops the selection, leaving the viewport untouched; double click
            // opens, the same contract as the side table.
            zr.on("click", (event: {target?: unknown}) => {
                if (!event.target) {
                    selectedNodeID.value = undefined
                    clearGroup()
                }
            })
            chart?.on?.("dblclick", (event: Record<string, any>) => {
                if (event?.dataType === "node") openedNodeID.value = event.data?.id as string
            })
        })
    }

    /** Reads post-simulation positions so style-only updates can use layout:"none". */
    const capturePositions = (): void => {
        const chart = getChart()
        if (!chart) return
        try {
            const data = chart.getModel?.()?.getSeriesByIndex?.(0)?.getData?.()
            if (!data) return
            const positions = new Map<string, {x: number; y: number}>()
            for (let i = 0; i < data.count(); i++) {
                // getName(), not getId(): graph nodes are identified by `name`, which carries node.id.
                const name   = data.getName(i)
                const layout = data.getItemLayout(i) as [number, number] | {x: number; y: number} | undefined
                const x = Array.isArray(layout) ? layout[0] : layout?.x
                const y = Array.isArray(layout) ? layout[1] : layout?.y
                if (name != null && x !== undefined && y !== undefined) {
                    positions.set(String(name), {x, y})
                }
            }
            if (positions.size > 0) capturedPositions.value = positions
        } catch {
            // Internal ECharts API unavailable: style updates will skip layout:"none".
        }
    }

    const applyView = (chart: Record<string, any>): void => {
        chart.setOption({series: [{
            type: "graph",
            zoom: viewState.value.zoom,
            ...(viewState.value.center ? {center: viewState.value.center} : {}),
        }]}, false)
    }

    /** Applies the latest styles directly to ECharts with layout:"none", bypassing the frozen props. */
    const applyStylesToChart = (): void => {
        const chart = getChart()
        if (!chart) return
        bindCanvasClicks()
        const positions    = capturedPositions.value
        const nodesWithPos = graphNodes.value.map((n) => {
            const pos = positions.get(n.id)
            return pos ? {...n, x: pos.x, y: pos.y} : n
        })
        const layout = positions.size > 0 ? "none" : "force"
        chart.setOption({series: [{type: "graph", data: nodesWithPos, links: graphEdges.value, layout}]}, false)
    }

    watch([graphNodes, graphEdges], () => {
        if (chartNodes.value === null) return
        applyStylesToChart()
    })

    /**
     * Polls until the force layout has settled, then centres on the selected node or fits the graph.
     * Polling, not the `finished` event: ECharts renders in the same frame as our first RAF, so the
     * event fires before a listener could be attached and positions would never be captured.
     */
    const captureAndFocusWhenReady = (): void => {
        let attempts = 0
        const MAX_ATTEMPTS = 120 // About 2s at 60fps, bailing where ECharts never initialises (e.g. Storybook stubs).
        const poll = () => {
            const chart = getChart()
            if (!chart) {
                if (++attempts >= MAX_ATTEMPTS) return
                requestAnimationFrame(poll)
                return
            }
            bindCanvasClicks()
            capturePositions()
            if (capturedPositions.value.size > 0) {
                const id = selectedNodeID.value
                // Switch to layout:"none" now so the one-time refit it triggers happens under
                // the initial focus below, rather than under the user's first click.
                applyStylesToChart()
                requestAnimationFrame(() => {
                    if (id) focusNode(id)
                    else fitGraph()
                })
                return
            }
            if (++attempts >= MAX_ATTEMPTS) return
            requestAnimationFrame(poll)
        }
        requestAnimationFrame(poll)
    }

    onMounted(async () => {
        try {
            if (fetchAssetDependencies) {
                const result = await fetchAssetDependencies()
                elements.value = {data: result.data, count: result.count}
            } else if (subtype === NAMESPACE) {
                const {data} = await namespacesStore.loadDependencies({namespace: params.id as string})
                const nodes = data.nodes ?? []
                elements.value = {
                    data:  transformResponse(data as any, NAMESPACE),
                    count: new Set(nodes.map((r: {uid: string}) => r.uid)).size,
                }
            } else {
                const result = await flowStore.loadDependencies(
                    {
                        id:       (subtype === FLOW ? params.id : params.flowId) as string,
                        namespace: params.namespace as string,
                        subtype:  subtype === FLOW ? FLOW : EXECUTION,
                    },
                    false,
                )
                elements.value = {data: result.data ?? [], count: result.count}
            }
        } catch {
            elements.value = {data: [], count: 0}
        }

        isLoading.value   = false
        isRendering.value = false

        if (subtype !== NAMESPACE && elements.value.data.length > 0) {
            // Wait for KsGraph to receive the new nodes prop and render.
            await nextTick()
            selectNode(initialNodeID)
        }
        await nextTick()
        chartNodes.value = graphNodes.value
        chartEdges.value = graphEdges.value
        captureAndFocusWhenReady()

        if (subtype === EXECUTION) nextTick(() => openSSE())
    })

    const sse = ref()

    /** Applies a live execution-state update to its node, replacing the element so Vue picks up the change. */
    const applyExecutionUpdate = (message: Record<string, any>): void => {
        const nodeId = `${message.tenantId}_${message.namespace}_${message.flowId}`
        const idx = elements.value.data.findIndex(
            (el): el is {data: Node} => el.data.type === NODE && el.data.id === nodeId,
        )
        if (idx === -1) return

        const el = elements.value.data[idx] as {data: Node}
        const updated = {
            data: {
                ...el.data,
                metadata: {...el.data.metadata, id: message.executionId, state: message.state.current as string},
            },
        }
        elements.value.data.splice(idx, 1, updated)
    }

    const openSSE = () => {
        if (subtype !== EXECUTION) return
        closeSSE()
        sse.value = executionsStore.followExecutionDependencies({id: params.id as string, expandAll: true})
        sse.value.onmessage = (event: MessageEvent) => {
            if (event?.lastEventId === "end-all") closeSSE()
            const message = JSON.parse(event.data)
            if (message.state) applyExecutionUpdate(message)
        }
        sse.value.onerror = () => {
            coreStore.message = {
                variant: "error",
                title:   t("error"),
                message: t("something_went_wrong.loading_execution"),
            }

            // Close on error: EventSource auto-reconnects unless explicitly closed, and each
            // reconnect leaks a server-side SSE connection. See kestra-io/kestra#16982.
            closeSSE()
        }
    }

    const closeSSE = () => {
        if (!sse.value) return
        sse.value.close()
        sse.value = undefined
    }

    // No resize handling of our own: KsEchart's `autoresize` already covers window resizes and splitter drags.
    onBeforeUnmount(() => {
        if (subtype === EXECUTION) closeSSE()
    })

    const fitGraph = (): void => {
        const chart = getChart()
        const positions = capturedPositions.value
        if (!chart || positions.size === 0) { graphRef.value?.fit(); return }
        const xs = [...positions.values()].map(p => p.x)
        const ys = [...positions.values()].map(p => p.y)
        const padding = 20
        const W = chart.getWidth()  as number
        const H = chart.getHeight() as number
        const spreadX = Math.max(...xs) - Math.min(...xs)
        const spreadY = Math.max(...ys) - Math.min(...ys)

        const zoom = Math.min(
            1,
            (W - padding * 2) / (spreadX || 1),
            (H - padding * 2) / (spreadY || 1),
        )
        const cx = (Math.min(...xs) + Math.max(...xs)) / 2
        const cy = (Math.min(...ys) + Math.max(...ys)) / 2
        viewState.value = {zoom, center: [cx, cy]}
        applyView(chart)
    }

    return {
        /** Returns the raw Element[] used by the Table component. */
        getElements: () => elements.value.data,
        /** Live computed nodes and edges, reflecting selection, filter and theme changes. */
        graphNodes,
        graphEdges,
        /** Frozen snapshots for KsGraph props, set once after the initial render. */
        chartNodes,
        chartEdges,
        /** Intersection of the table filter and any isolated group; dims everything outside it. */
        shownNodeIDs,
        /** Drops the table filter and any pinned group together. */
        clearFilters,
        /** Fades every node outside one group, without pinning it. */
        isolateGroup,
        /** Pins or unpins a group; the chip row reads activeGroup for its active state. */
        toggleGroup,
        clearGroup,
        activeGroup,
        isLoading,
        isRendering,
        selectedNodeID,
        selectNode,
        /** Highlights a node from outside the canvas, e.g. hovering the side table. */
        highlightNode,
        /** Last node double-clicked on the canvas, for the view to navigate to. */
        openedNodeID,
        /** Called from the KsGraph @node-click event. */
        handleNodeClick: (node: KsGraphNode) => {
            selectNode(node.id as string)
        },
        handlers: {
            zoomIn:        () => graphRef.value?.zoomIn(),
            zoomOut:       () => graphRef.value?.zoomOut(),
            clearSelection,
            fit: fitGraph,
            highlightShown: (nodeIDs: string[]) => {
                const allNodeCount = nodesOf(elements.value.data).length
                tableFilterIDs.value = nodeIDs.length >= allNodeCount ? null : new Set(nodeIDs)
            },
            exportAsImage: (type: "jpeg" | "png", nodeID?: string) => {
                const ts       = new Date().toISOString().slice(0, 19).replace(/:/g, "-")
                const filename = `dependencies-${nodeID ? `${nodeID}-` : ""}${ts}.${type}`
                graphRef.value?.exportAsImage(type, filename)
            },
        },
    }
}

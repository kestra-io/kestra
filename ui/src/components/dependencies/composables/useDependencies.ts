import {onBeforeUnmount, onMounted, nextTick, watch, ref, computed} from "vue"

import {useCoreStore} from "../../../stores/core"
import {useFlowStore} from "../../../stores/flow"
import {useExecutionsStore} from "../../../stores/executions"
import {useNamespacesStore} from "override/stores/namespaces"
import {useMiscStore} from "override/stores/misc"

import {useI18n} from "vue-i18n"

import type {Ref, ComputedRef} from "vue"

import type {RouteParams} from "vue-router"

import {v4 as uuid} from "uuid"

import {State, cssVar} from "@kestra-io/design-system"
import type {KsGraphNode, KsGraphEdge} from "@kestra-io/design-system"

import {NODE, EDGE, FLOW, EXECUTION, NAMESPACE, ASSET} from "../utils/types"
import type {Types, Node, Edge, Element} from "../utils/types"

import {computeDagLayout} from "../utils/dagLayout"

import moment from "moment"

// ─── CSS variable maps ────────────────────────────────────────────────────────

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

// ─── Asset node icon ──────────────────────────────────────────────────────────

// Material Design "package-variant-closed" glyph (viewBox 0 0 24 24), used to
// mark asset nodes in the dependency graph. ECharts graph symbols are drawn on a
// canvas and cannot mount a Vue <KsIcon>, so the icon is embedded as an SVG
// `image://` symbol instead — the only way to render a glyph inside a graph node.
const ASSET_ICON_PATH =
    "M21,16.5C21,16.88 20.79,17.21 20.47,17.38L12.57,21.82C12.41,21.94 12.21,22 12,22C11.79,22 11.59,21.94 11.43,21.82L3.53,17.38C3.21,17.21 3,16.88 3,16.5V7.5C3,7.12 3.21,6.79 3.53,6.62L11.43,2.18C11.59,2.06 11.79,2 12,2C12.21,2 12.41,2.06 12.57,2.18L20.47,6.62C20.79,6.79 21,7.12 21,7.5V16.5M12,4.15L10.11,5.22L16,8.61L17.96,7.5L12,4.15M6.04,7.5L12,10.85L13.96,9.75L8.08,6.35L6.04,7.5M5,15.91L11,19.29V12.58L5,9.21V15.91M19,15.91V9.21L13,12.58V19.29L19,15.91Z"

/**
 * Builds an ECharts `image://` symbol for an asset node: a filled circle matching
 * the node's current background/border colours with the packageVariantClosed glyph
 * centred inside. Colours are baked into the SVG because ECharts ignores itemStyle
 * for image symbols; the symbol is rebuilt whenever graphNodes recomputes
 * (selection / filter / theme change), so state and theme stay in sync.
 */
function assetNodeSymbol(bgColor: string, borderColor: string, iconColor: string): string {
    const svg =
        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\">" +
        `<circle cx="12" cy="12" r="11" fill="${bgColor}" stroke="${borderColor}" stroke-width="1.5"/>` +
        `<path transform="translate(12 12) scale(0.55) translate(-12 -12)" fill="${iconColor}" d="${ASSET_ICON_PATH}"/>` +
        "</svg>"
    return `image://data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}

// ─── DAG view ─────────────────────────────────────────────────────────────────

export type LayoutMode = "force" | "dag"

/** Card footprint in graph coordinates; must stay inside the layout row/column gaps. */
const CARD_SIZE = [160, 54]
const HEADER_OFFSET = 64
const HEADER_ID_PREFIX = "dag-column-header-"
const PADDING_ID_PREFIX = "dag-padding-"

// Freshness vocabulary, using the status tokens the rest of the product already
// uses for execution state, always paired with a glyph so colour is never alone.
const STATUS = {
    fresh:   {token: "--ks-status-success", icon: "M21,7L9,19L3.5,13.5L4.91,12.09L9,16.17L19.59,5.59L21,7Z"},
    stale:   {token: "--ks-status-warning", icon: "M12,20A7,7 0 0,1 5,13A7,7 0 0,1 12,6A7,7 0 0,1 19,13A7,7 0 0,1 12,20M19.03,7.39L20.45,5.97C20,5.46 19.55,5 19.04,4.56L17.62,6C16.07,4.74 14.12,4 12,4A9,9 0 0,0 3,13A9,9 0 0,0 12,22C17,22 21,17.97 21,13C21,10.88 20.26,8.93 19.03,7.39M11,14H13V8H11M15,1H9V3H15V1Z"},
    failed:  {token: "--ks-status-error",   icon: "M13,13H11V7H13M13,17H11V15H13M12,2A10,10 0 0,0 2,12A10,10 0 0,0 12,22A10,10 0 0,0 22,12A10,10 0 0,0 12,2Z"},
    never:   {token: "--ks-status-neutral", icon: "M19,13H5V11H19V13Z"},
    unknown: {token: "--ks-status-neutral", icon: "M15.07,11.25L14.17,12.17C13.45,12.89 13,13.5 13,15H11V14.5C11,13.39 11.45,12.39 12.17,11.67L13.41,10.41C13.78,10.05 14,9.55 14,9C14,7.89 13.1,7 12,7A2,2 0 0,0 10,9H8A4,4 0 0,1 12,5A4,4 0 0,1 16,9C16,9.88 15.64,10.67 15.07,11.25M13,19H11V17H13M12,2A10,10 0 0,0 2,12A10,10 0 0,0 12,22A10,10 0 0,0 22,12A10,10 0 0,0 12,2Z"},
} as const

type StatusKey = keyof typeof STATUS

const statusOf = (value?: string): StatusKey => (value && value in STATUS ? value as StatusKey : "unknown")

// Material Design glyphs marking how an asset is materialised. Same `image://`
// trick as the asset symbol above: ECharts draws labels on a canvas and cannot
// mount a Vue <KsIcon>, so the glyph rides in as an SVG data URI.
const KIND_ICONS: Record<string, string> = {
    seed:  "M2,22V20C2,20 7,18 12,18C17,18 22,20 22,20V22H2M11.3,9.1C10.1,5.2 4,6.1 4,6.1C4,6.1 4.2,13.9 9.9,12.7C9.5,9.8 8,9 8,9C10.8,9 11,12.4 11,12.4V17C11.3,17 11.7,17 12,17C12.3,17 12.7,17 13,17V12.8C13,12.8 13,8.9 16,7.9C16,7.9 14,10.9 14,12.9C21,13.6 21,4 21,4C21,4 12.1,3 11.3,9.1Z",
    view:  "M12,9A3,3 0 0,0 9,12A3,3 0 0,0 12,15A3,3 0 0,0 15,12A3,3 0 0,0 12,9M12,17A5,5 0 0,1 7,12A5,5 0 0,1 12,7A5,5 0 0,1 17,12A5,5 0 0,1 12,17M12,4.5C7,4.5 2.73,7.61 1,12C2.73,16.39 7,19.5 12,19.5C17,19.5 21.27,16.39 23,12C21.27,7.61 17,4.5 12,4.5Z",
    table: "M4,3H20A2,2 0 0,1 22,5V19A2,2 0 0,1 20,21H4A2,2 0 0,1 2,19V5A2,2 0 0,1 4,3M4,7V10H8V7H4M10,7V10H14V7H10M20,10V7H16V10H20M4,12V15H8V12H4M4,20H8V17H4V20M10,12V15H14V12H10M10,20H14V17H10V20M20,20V17H16V20H20M20,15V12H16V15H20Z",
    flow:  "M4,2A2,2 0 0,0 2,4V8A2,2 0 0,0 4,10H8A2,2 0 0,0 10,8V7H14V8A2,2 0 0,0 16,10H20A2,2 0 0,0 22,8V4A2,2 0 0,0 20,2H16A2,2 0 0,0 14,4V5H10V4A2,2 0 0,0 8,2H4M4,14A2,2 0 0,0 2,16V20A2,2 0 0,0 4,22H8A2,2 0 0,0 10,20V16A2,2 0 0,0 8,14H4M16,14A2,2 0 0,0 14,16V20A2,2 0 0,0 16,22H20A2,2 0 0,0 22,20V16A2,2 0 0,0 20,14H16Z",
}

/** Inline SVG data URI for a Material path, tinted to the given colour. */
function svgSymbol(path: string, color: string): string {
    const svg =
        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\">" +
        `<path fill="${color}" d="${path}"/>` +
        "</svg>"
    // Plain data URI, not the `image://` form: that prefix is symbol syntax, and a rich
    // text fragment takes the URL directly.
    return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}

/** Glyph for how an asset is materialised, when the kind is one we have an icon for. */
function kindIcon(kind: string, color: string): string | undefined {
    const path = KIND_ICONS[kind]
    return path ? svgSymbol(path, color) : undefined
}

/** Trailing segment of a dotted asset id (`db.schema.stg_customers` → `stg_customers`). */
function shortName(id: string): string {
    const segments = id.split(".")
    return segments[segments.length - 1] || id
}

/** Schema segment of a fully qualified asset id, used for the kind badge and column headers. */
function schemaName(id: string): string | undefined {
    const segments = id.split(".")
    return segments.length >= 3 ? segments[segments.length - 2] : undefined
}

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
    const counts = new Map<string, number>()
    elements.forEach((el) => {
        if (el.data.type !== EDGE) return
        const edge = el.data as Edge
        counts.set(edge.source, (counts.get(edge.source) ?? 0) + 1)
        counts.set(edge.target, (counts.get(edge.target) ?? 0) + 1)
    })
    return counts
}

function nodeSize(id: string, edgeCounts: Map<string, number>, base = 20, scale = 2, max = 100): number {
    return Math.min(base + (edgeCounts.get(id) ?? 0) * scale, max)
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
 * @param layoutMode  - Force simulation (default) or the layered DAG layout.
 */
export function useDependencies(
    graphRef: Ref<KsGraphRef | null>,
    subtype: Types = FLOW,
    initialNodeID: string,
    params: RouteParams,
    fetchAssetDependencies?: () => Promise<{data: Element[]; count: number}>,
    layoutMode: Ref<LayoutMode> = ref<LayoutMode>("force"),
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

    // chartNodes/chartEdges are set once after the initial render and never changed.
    // All subsequent style updates (selection, filter, theme) are applied imperatively
    // via applyStylesToChart(), which uses layout:"none" + stored positions so that
    // ECharts never re-runs the force simulation.
    const chartNodes = ref<KsGraphNode[] | null>(null)
    const chartEdges = ref<KsGraphEdge[] | null>(null)
    /** Positions read back from ECharts once the force simulation has settled. */
    const capturedPositions = ref(new Map<string, {x: number; y: number}>())
    /**
     * The camera, kept in step with the user's own panning and zooming through the
     * graphRoam event. Only focusNode and fitGraph write it deliberately; nothing else
     * may re-assert it, or a style-only update would yank the viewport back.
     */
    const viewState = ref<{zoom: number; center?: [number, number]}>({zoom: 1})

    /** KsGraph's layout prop: "force" only for the very first render, explicit coordinates after. */
    const graphLayout = ref<"force" | "none">("force")
    /**
     * How far ECharts has scaled the layout to fit the pane. Cards are sized in screen
     * pixels, so without this they keep their size while the gaps around them shrink,
     * which is why a narrower pane made them look fatter and eventually collide.
     */
    const fitScale = ref(1)

    /** Set when a node is double-clicked, so the view can open that node's own page. */
    const openedNodeID = ref<Node["id"] | undefined>(undefined)

    /** IDs of nodes that belong to the current table-filter result (null = no filter). */
    const shownNodeIDs = ref<Set<string> | null>(null)

    const elements = ref<{data: Element[]; count: number}>({data: [], count: 0})

    // ─── Layout ───────────────────────────────────────────────────────────────

    const isDag = computed(() => layoutMode.value === "dag")

    const dagLayout = computed(() => {
        const nodes = elements.value.data.filter((el): el is {data: Node} => el.data.type === NODE)
        const flows = new Set(nodes.filter(({data}) => data.metadata.subtype === FLOW).map(({data}) => data.id))

        return computeDagLayout(
            nodes.map(({data}) => data.id),
            elements.value.data
                .filter((el): el is {data: Edge} => el.data.type === EDGE)
                .map(({data}) => data)
                // A flow produces its assets, so it belongs at the head of the graph.
                // Ignoring what feeds it keeps it in the first column instead of the middle.
                .filter((edge) => !flows.has(edge.target)),
                {
                // ECharts fits the layout extent to the canvas while symbols keep their pixel
                // size, and the wider axis sets that scale. Columns are the binding dimension
                // here, so widening them shrinks everything and crowds the rows; the row pitch
                // is free to be generous.
                columnGap: CARD_SIZE[0] + 110,
                rowGap:    CARD_SIZE[1] * 2.2,
                // A flow triggers the graph rather than sitting inside it, so it gets
                // the leading column to itself.
                ownColumn: (id) => flows.has(id),
            },
        )
    })

    /** Column labels: the schema the column's assets share, else a generic layer number. */
    const dagColumnLabels = computed(() => dagLayout.value.columns.map((column, index) => {
        const schemas = new Set(column.map((id) => {
            const node = elements.value.data.find(
                (el): el is {data: Node} => el.data.type === NODE && el.data.id === id,
            )
            return node ? schemaName(node.data.flow) : undefined
        }))
        const [only] = [...schemas]
        return schemas.size === 1 && only ? only : t("dependency.dag.layer", {n: index + 1})
    }))

    /** Node coordinates in play: computed in DAG view, read back from the simulation otherwise. */
    const storedPositions = computed(() => (isDag.value ? dagLayout.value.positions : capturedPositions.value))

    // ─── Derived graph topology ───────────────────────────────────────────────

    function neighborIDs(anchorID: string | undefined): Set<string> {
        if (!anchorID) return new Set()
        const neighbors = new Set<string>([anchorID])
        elements.value.data.forEach((el) => {
            if (el.data.type !== EDGE) return
            const edge = el.data as Edge
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
        elements.value.data.forEach((el) => {
            if (el.data.type !== EDGE) return
            const edge = el.data as Edge
            if (edge.source === anchorID || edge.target === anchorID) ids.add(edge.id)
        })
        return ids
    }

    /** Set of node IDs connected to the selected node (includes the selected node itself). */
    const selectedNeighborIDs: ComputedRef<Set<string>> = computed(() => neighborIDs(selectedNodeID.value))

    /** Set of edge IDs connected to the selected node. */
    const selectedEdgeIDs: ComputedRef<Set<string>> = computed(() => connectedEdgeIDs(selectedNodeID.value))

    // ─── ECharts data (reactive, rebuilt on state changes) ───────────────────
    //
    // Hover highlighting is handled entirely by ECharts' built-in
    // emphasis.focus = "adjacency" (set in KsGraph series config).
    // Each node/edge carries emphasis.itemStyle (hover colour) and
    // blur.itemStyle (= same as base itemStyle) so that selection colours
    // are preserved when another node is hovered.

    const graphNodes: ComputedRef<KsGraphNode[]> = computed(() => {
        void miscStore.theme // recompute cssVar calls when theme switches
        const edgeCounts   = buildEdgeCounts(elements.value.data)
        const hasSelection = selectedNodeID.value !== undefined
        const hasFilter    = shownNodeIDs.value !== null
        // Icon colour for asset nodes — theme-adaptive so it stays legible on the
        // asset background in both light and dark themes.
        const assetIconColor = cssVar("--ks-text-primary")

        const nodes = elements.value.data
            .filter((el): el is {data: Node} => el.data.type === NODE)
            .map(({data: node}) => {
                const isSelected = node.id === selectedNodeID.value
                const isNeighbor = hasSelection && selectedNeighborIDs.value.has(node.id) && !isSelected
                const isFaded    = hasSelection && !isSelected && !isNeighbor
                const isDimmed   = hasFilter && !shownNodeIDs.value!.has(node.id)
                const isAsset    = node.metadata.subtype === ASSET

                // For EXECUTION subtype, use the execution state color when available.
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
                    bgColor     = cssVar(NODE_BG.faded)
                    borderColor = cssVar(NODE_BORDER.faded)
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

                // DAG view draws a card instead of a bubble: the fill stays a neutral
                // surface so the label is legible, and the border carries the state colour.
                if (isDag.value) {
                    const kind = isAsset
                        ? ((node.metadata as {kind?: string}).kind ?? (node.metadata as {system?: string}).system ?? schemaName(node.flow))
                        : node.namespace
                    const updated = (node.metadata as {updated?: string}).updated
                    const glyph = kindIcon(isAsset ? String(kind) : "flow", cssVar("--ks-text-secondary"))
                    const status = statusOf(isAsset ? (node.metadata as {status?: string}).status : undefined)
                    const statusColor = cssVar(STATUS[status].token)
                    const statusGlyph = isAsset ? svgSymbol(STATUS[status].icon, statusColor) : undefined
                    // Kind is spelled out rather than left to the glyph: an icon only reads
                    // to someone who already knows the vocabulary.
                    // One meta row keeps the card long and thin; the second row is what made
                    // it tall enough to crowd its neighbours.
                    const kindLine = [
                        glyph ? "{glyph| }" : "",
                        kind ? `{kindLabel|${String(kind).toUpperCase()}}` : "",
                        isAsset ? "{status| }" : "",
                        isAsset ? `{statusLabel|${t(`dependency.dag.status.${status}`)}}` : "",
                        isAsset && updated ? `{age|${moment(updated).fromNow(true)}}` : "",
                    ].filter(Boolean).join(" ")
                    const cardItemStyle = {
                        // Status owns the border in DAG view: it is the first thing to read.
                        color:       cssVar("--ks-bg-surface"),
                        borderColor: isAsset && !isDimmed && !isFaded ? statusColor : borderColor,
                        borderWidth: isSelected ? 3 : 2,
                        opacity,
                    }

                    return {
                        id:         node.id,
                        name:       node.id,
                        symbol:     "roundRect",
                        symbolSize: CARD_SIZE.map((side) => side * fitScale.value),
                        itemStyle:  cardItemStyle,
                        emphasis:   {itemStyle: {...cardItemStyle, borderColor: cssVar(NODE_BORDER.hovered), opacity: 1}},
                        blur:       {itemStyle: cardItemStyle},
                        label: {
                            show:            true,
                            position:        "inside",
                            formatter:       [
                                `{name|${shortName(node.flow)}}`,
                                kindLine,
                            ].filter(Boolean).join("\n"),
                            textBorderWidth: 0,
                            rich: {
                                name: {
                                    fontSize:   13 * fitScale.value,
                                    fontWeight: "bold",
                                    color:      labelColor,
                                    padding:    [0, 0, 5, 0],
                                    // Long asset names are truncated rather than allowed to
                                    // spill over the card and into the next column.
                                    width:      (CARD_SIZE[0] - 24) * fitScale.value,
                                    overflow:   "truncate",
                                },
                                kindLabel: {
                                    fontSize:        9 * fitScale.value,
                                    fontWeight:      "bold",
                                    color:           cssVar("--ks-text-secondary"),
                                    backgroundColor: cssVar("--ks-bg-tag"),
                                    borderRadius:    3,
                                    padding:         [3, 5],
                                },
                                age: {
                                    fontSize: 10 * fitScale.value,
                                    color:    cssVar("--ks-text-secondary"),
                                    padding:  [3, 0],
                                },
                                glyph: {
                                    height:          12,
                                    width:           12,
                                    backgroundColor: glyph ? {image: glyph} : undefined,
                                },
                                status: {
                                    height:          12,
                                    width:           12,
                                    backgroundColor: statusGlyph ? {image: statusGlyph} : undefined,
                                },
                                statusLabel: {
                                    fontSize:   10 * fitScale.value,
                                    fontWeight: "bold",
                                    color:      statusColor,
                                    padding:    [3, 0],
                                },
                            },
                        },
                    }
                }

                return {
                    id:         node.id,
                    name:       node.id,
                    symbolSize: nodeSize(node.id, edgeCounts),
                    // Asset nodes carry the packageVariantClosed glyph inside the node;
                    // other nodes keep the default ECharts circle symbol.
                    ...(isAsset ? {symbol: assetNodeSymbol(bgColor, borderColor, assetIconColor)} : {}),
                    itemStyle:  baseItemStyle,
                    // Hover colour – applied by ECharts emphasis.focus:"adjacency"
                    emphasis: {
                        itemStyle: {
                            color:       cssVar(NODE_BG.hovered),
                            borderColor: cssVar(NODE_BORDER.hovered),
                            borderWidth: 2,
                            opacity:     1,
                        },
                        label: {show: true, color: cssVar("--ks-text-primary")},
                    },
                    // Blur = same as base so selection colours survive when another node is hovered.
                    // Label uses full opacity so text doesn't dim when a neighbour is hovered.
                    blur: {
                        itemStyle: baseItemStyle,
                        label:     {color: cssVar("--ks-text-primary")},
                    },
                    // Asset ids are long enough that a few dozen of them printed at once is a
                    // pile, so the asset graph reveals them on hover. The flow, execution and
                    // namespace graphs keep their always-on labels.
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

        return isDag.value ? [...nodes, ...columnHeaderNodes(), ...paddingNodes()] : nodes
    })

    /**
     * Invisible nodes just outside the graph's corners. ECharts fits the data extent
     * to the canvas, so padding the extent is what puts breathing room around the
     * layout; insetting the series box would do it too but would shrink the roam area.
     */
    const paddingNodes = (): KsGraphNode[] => {
        const positions = [...dagLayout.value.positions.values()]
        if (!positions.length) return []

        const xs = positions.map((position) => position.x)
        const ys = positions.map((position) => position.y)
        const padX = CARD_SIZE[0] + 40
        const padY = CARD_SIZE[1] * 2 + HEADER_OFFSET

        return [
            {x: Math.min(...xs) - padX, y: Math.min(...ys) - padY},
            {x: Math.max(...xs) + padX, y: Math.max(...ys) + padY},
        ].map((corner, index) => ({
            id:         `${PADDING_ID_PREFIX}${index}`,
            name:       `${PADDING_ID_PREFIX}${index}`,
            ...corner,
            symbolSize: 0,
            silent:     true,
            tooltip:    {show: false},
            label:      {show: false},
        }))
    }

    /**
     * Label-only nodes sitting above each DAG column. They ride the graph's own
     * coordinate space, so headers pan and zoom with the columns they name.
     */
    const columnHeaderNodes = (): KsGraphNode[] => {
        const {positions, columns} = dagLayout.value
        const ys = [...positions.values()].map((position) => position.y)
        const top = (ys.length ? Math.min(...ys) : 0) - HEADER_OFFSET

        return columns.map((column, index) => ({
            id:         `${HEADER_ID_PREFIX}${index}`,
            name:       `${HEADER_ID_PREFIX}${index}`,
            x:          positions.get(column[0])?.x ?? 0,
            y:          top,
            symbol:     "circle",
            symbolSize: 1,
            itemStyle:  {opacity: 0},
            tooltip:    {show: false},
            label: {
                show:            true,
                position:        "top",
                formatter:       dagColumnLabels.value[index],
                color:           cssVar("--ks-text-secondary"),
                fontSize:        11,
                fontWeight:      "bold",
                textBorderWidth: 0,
            },
        }))
    }

    const graphEdges: ComputedRef<KsGraphEdge[]> = computed(() => {
        void miscStore.theme // recompute cssVar calls when theme switches
        const hasSelection = selectedNodeID.value !== undefined
        const hasFilter    = shownNodeIDs.value !== null

        return elements.value.data
            .filter((el): el is {data: Edge} => el.data.type === EDGE)
            .map(({data: edge}) => {
                const isSelected   = selectedEdgeIDs.value.has(edge.id)
                const isFaded      = hasSelection && !isSelected
                const isEdgeDimmed = hasFilter &&
                    (!shownNodeIDs.value!.has(edge.source) || !shownNodeIDs.value!.has(edge.target))

                // For EXECUTION subtype, color selected edges with the source node's state color.
                const execState    = subtype === EXECUTION && isSelected
                    ? (() => {
                        const src = elements.value.data.find(
                            (el): el is {data: Node} =>
                                el.data.type === NODE && el.data.id === selectedNodeID.value,
                        )
                        return (src?.data.metadata as {state?: string})?.state
                    })()
                    : undefined
                const execColor    = execState ? State.getStateColor(execState) : undefined

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

    // ─── Selection ────────────────────────────────────────────────────────────

    /** Mirrors a hover in the side table onto the canvas, and clears it when it leaves. */
    const highlightNode = (id?: Node["id"]): void => {
        const chart = graphRef.value?.getEchartsInstance?.() as Record<string, any> | null
        if (!chart) return
        chart.dispatchAction({type: "downplay", seriesIndex: 0})
        if (id) chart.dispatchAction({type: "highlight", seriesIndex: 0, name: id})
    }

    const focusNode = (id: Node["id"]): void => {
        if (!id) return
        const pos = storedPositions.value.get(id)
        if (!pos) return
        const chart = graphRef.value?.getEchartsInstance?.() as Record<string, any> | null
        if (!chart) return

        // Clear any stuck hover emphasis (mouseout may not fire when clicking a table row).
        chart.dispatchAction({type: "downplay", seriesIndex: 0})
        // For ECharts graph series, `center` is in data coordinates.
        // Setting center=[pos.x, pos.y] places the selected node at canvas centre.
        // DAG cards are sized in pixels, so zooming past 1:1 only pushes them apart.
        viewState.value = {zoom: isDag.value ? 1 : 1.8, center: [pos.x, pos.y]}
        applyView(chart)
    }

    // Trigger focus after all reactive updates (applyStylesToChart) have flushed.
    // Only fires after initial capture (storedPositions populated), so the initial
    // auto-selection on mount is handled by captureAndFocusWhenReady instead.
    // Selecting a node never moves the viewport: the canvas jumping under the cursor
    // costs the user their bearings. Only the initial auto-selection centres the graph,
    // from captureAndFocusWhenReady.

    /**
     * Selects a node by ID, updating the visual selection state reactively.
     */
    const selectNode = (id: Node["id"]): void => {
        const exists = elements.value.data.some(
            (el): el is {data: Node} => el.data.type === NODE && el.data.id === id,
        )
        if (!exists) return
        selectedNodeID.value = id
    }

    // ─── Imperative style updates (post-freeze) ───────────────────────────────

    /**
     * Reads post-simulation node positions from ECharts' internal data store
     * and caches them in storedPositions so subsequent style-only updates can
     * use layout:"none" and avoid re-running the force simulation.
     */
    /**
     * Clearing the selection also re-frames the graph. The initial auto-selection zooms
     * in on one node, so dropping the selection without re-fitting would leave the view
     * stranded at that zoom.
     */
    const clearSelection = (): void => {
        selectedNodeID.value = undefined
        shownNodeIDs.value = null
        fitGraph()
    }

    /** Clicking the canvas away from any node clears the selection, like the back button. */
    const bindCanvasClicks = (): void => {
        requestAnimationFrame(() => {
            const chart = graphRef.value?.getEchartsInstance?.() as Record<string, any> | null
            const zr = chart?.getZr?.()
            if (!zr || zr.__ksDependenciesBound) return
            zr.__ksDependenciesBound = true
            zr.on("click", (event: {target?: unknown}) => {
                // Deselect only: the viewport stays exactly where the user left it, and
                // nothing changes but the node styling.
                if (!event.target) selectedNodeID.value = undefined
            })
            chart?.on?.("graphRoam", () => {
                const series = (chart.getOption?.() as Record<string, any> | undefined)?.series?.[0]
                if (series?.zoom !== undefined) viewState.value = {zoom: series.zoom, center: series.center}
            })
            // Single click selects, double click opens: the same contract as the side table.
            chart?.on?.("dblclick", (params: Record<string, any>) => {
                if (params?.dataType === "node") openedNodeID.value = params.data?.id as string
            })
        })
    }

    const capturePositions = (): void => {
        const chart = graphRef.value?.getEchartsInstance?.() as Record<string, any> | null
        if (!chart) return
        try {
            const data = chart.getModel?.()?.getSeriesByIndex?.(0)?.getData?.()
            if (!data) return
            const positions = new Map<string, {x: number; y: number}>()
            for (let i = 0; i < data.count(); i++) {
                // Use getName() — ECharts graph nodes are identified by `name`, which we set to node.id (UUID).
                // getId() returns an ECharts-internal synthetic ID that won't match our UUID keys.
                const name   = data.getName(i)
                // ECharts graph series returns layout as [x, y] array, not {x, y} object.
                const layout = data.getItemLayout(i) as [number, number] | {x: number; y: number} | undefined
                const x = Array.isArray(layout) ? layout[0] : layout?.x
                const y = Array.isArray(layout) ? layout[1] : layout?.y
                if (name != null && x !== undefined && y !== undefined) {
                    positions.set(String(name), {x, y})
                }
            }
            if (positions.size > 0) capturedPositions.value = positions
        } catch {
            // Internal ECharts API unavailable — style updates will skip layout:none.
        }
    }

    /**
     * Applies the latest graphNodes/graphEdges styles directly to the ECharts
     * instance, bypassing the frozen reactive props. Uses layout:"none" with
     * stored positions so the force simulation never re-runs.
     */
    const applyView = (chart: Record<string, any>): void => {
        chart.setOption({series: [{
            type: "graph",
            zoom: viewState.value.zoom,
            ...(viewState.value.center ? {center: viewState.value.center} : {}),
        }]}, false)
    }

    const applyStylesToChart = (): void => {
        const chart = graphRef.value?.getEchartsInstance?.() as Record<string, any> | null
        if (!chart) return
        bindCanvasClicks()
        const positions    = storedPositions.value
        const nodesWithPos = graphNodes.value.map((n) => {
            const pos = positions.get(n.id)
            return pos ? {...n, x: pos.x, y: pos.y} : n
        })
        const layout = positions.size > 0 ? "none" : "force"
        chart.setOption({series: [{type: "graph", data: nodesWithPos, links: graphEdges.value, layout}]}, false)
    }

    /**
     * Pushes nodes and edges back through KsGraph's props with their coordinates
     * baked in. DAG view renders this way rather than patching the chart in place:
     * positions are known up front, so a plain re-render is enough and no
     * simulation can run.
     */
    const renderGraph = (): void => {
        bindCanvasClicks()
        const positions = storedPositions.value
        chartNodes.value = graphNodes.value.map((node) => {
            const position = positions.get(node.id as string)
            return position ? {...node, x: position.x, y: position.y} : node
        })
        chartEdges.value = graphEdges.value
        graphLayout.value = positions.size > 0 ? "none" : "force"
    }

    watch([graphNodes, graphEdges], () => {
        if (chartNodes.value === null) return
        if (isDag.value) renderGraph()
        else applyStylesToChart()
    })

    // The two layouts occupy very different extents, so re-frame on every switch.
    // The re-fit waits a frame rather than a tick: KsGraph's own prop-driven
    // setOption lands after nextTick and would otherwise reset the zoom we just set,
    // leaving DAG cards overlapping, since ECharts scales positions but not symbols.
    watch(layoutMode, () => {
        renderGraph()
        requestAnimationFrame(() => fitGraph())
    }, {flush: "post"})

    // ─── Data loading ─────────────────────────────────────────────────────────

    /**
     * Polls until ECharts has completed the initial force layout and node positions
     * are available, then centres the view on the selected node (or fits all nodes
     * for NAMESPACE graphs where no node is pre-selected).
     *
     * Why polling instead of listening for the `finished` event:
     * `chartNodes` is set synchronously, which schedules a Vue microtask flush.
     * That flush updates KsGraph's props, VChart calls setOption, and ECharts
     * queues its own RAF for rendering.  `captureAndFocusWhenReady` is called
     * right afterwards and queues *our* RAF.  Because both RAFs are in the same
     * browser frame, ECharts renders (and fires `finished`) before our first poll
     * fires — so the event is missed and positions are never captured.
     * Polling `capturePositions()` every frame avoids that race: positions become
     * non-empty one frame after ECharts renders, and we capture them reliably.
     */
    const captureAndFocusWhenReady = (): void => {
        let attempts = 0
        const MAX_ATTEMPTS = 120 // ~2s at 60fps — bail in environments where ECharts never initialises (e.g. Storybook stubs).
        const poll = () => {
            const chart = graphRef.value?.getEchartsInstance?.() as Record<string, any> | null
            if (!chart) {
                if (++attempts >= MAX_ATTEMPTS) return
                requestAnimationFrame(poll)
                return
            }
            bindCanvasClicks()
            capturePositions()
            if (storedPositions.value.size > 0) {
                const id = selectedNodeID.value
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
        } catch (error) {
            console.error(`Failed to load ${subtype} dependencies:`, error)
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

    // ─── SSE (live execution state updates) ──────────────────────────────────

    const sse = ref()
    const messages = ref<Record<string, unknown>[]>([])

    watch(
        messages,
        (newMessages) => {
            if (!newMessages?.length) return

            const message = newMessages[newMessages.length - 1] as Record<string, any>
            const nodeId  = `${message.tenantId}_${message.namespace}_${message.flowId}`

            const idx = elements.value.data.findIndex(
                (el): el is {data: Node} =>
                    el.data.type === NODE && el.data.id === nodeId,
            )

            if (idx === -1) return

            const el = elements.value.data[idx] as {data: Node}
            const state = message.state.current as string

            // Replace the element to ensure Vue picks up the change.
            const updated = {
                data: {
                    ...el.data,
                    metadata: {...el.data.metadata, id: message.executionId, state},
                },
            }
            elements.value.data.splice(idx, 1, updated)
        },
        {deep: true},
    )

    const openSSE = () => {
        if (subtype !== EXECUTION) return
        closeSSE()
        sse.value = executionsStore.followExecutionDependencies({id: params.id as string, expandAll: true})
        sse.value.onmessage = (event: MessageEvent) => {
            const isEnd = event?.lastEventId === "end-all"
            if (isEnd) closeSSE()
            const message = JSON.parse(event.data)
            if (!message.state) return
            messages.value.push(message)
        }
        sse.value.onerror = () => {
            coreStore.message = {
                variant: "error",
                title:   t("error"),
                message: t("something_went_wrong.loading_execution"),
            }

            // Close on error: EventSource auto-reconnects unless explicitly closed,
            // and each reconnect leaks a server-side SSE connection (Netty direct
            // buffers) over time. See kestra-io/kestra#16982.
            closeSSE()
        }
    }

    const closeSSE = () => {
        if (!sse.value) return
        sse.value.close()
        sse.value = undefined
    }

    const onResize = (): void => {
        requestAnimationFrame(() => {
            // Resize first: until the canvas re-measures, it is only stretched by CSS and
            // the nodes render distorted. Re-fitting afterwards uses the new dimensions.
            const chart = graphRef.value?.getEchartsInstance?.() as Record<string, any> | null
            chart?.resize?.()
            if (isDag.value) fitGraph()
        })
    }

    onMounted(() => window.addEventListener("resize", onResize))

    onBeforeUnmount(() => {
        window.removeEventListener("resize", onResize)
        if (subtype === EXECUTION) closeSSE()
    })

    // ─── Public API ───────────────────────────────────────────────────────────

    const fitGraph = (): void => {
        const chart = graphRef.value?.getEchartsInstance?.() as Record<string, any> | null
        const positions = storedPositions.value
        if (!chart || positions.size === 0) { graphRef.value?.fit(); return }
        const xs = [...positions.values()].map(p => p.x)
        const ys = [...positions.values()].map(p => p.y)
        const padding = 20
        const W = chart.getWidth()  as number
        const H = chart.getHeight() as number
        // Positions are node centres, so DAG cards and their column headers stick out
        // beyond the extent and have to be added back before fitting.
        const spreadX = (Math.max(...xs) - Math.min(...xs)) + (isDag.value ? CARD_SIZE[0] : 0)
        const spreadY = (Math.max(...ys) - Math.min(...ys)) + (isDag.value ? CARD_SIZE[1] + HEADER_OFFSET : 0)
        // ECharts fits the data extent to the canvas, but card symbols keep their pixel
        // size, so any fit below 1:1 slides fixed-size cards into each other. Counteract
        // the fit so one data unit is one pixel: the layout's gaps then hold exactly as
        // designed and a graph larger than the pane is panned rather than shrunk.
        if (isDag.value) {
            // Clamped: below this the label text stops being readable, and past 1 the
            // cards would grow beyond their designed size on a very wide pane.
            const measured = Math.min(1, Math.max(0.65, Math.min(W / (spreadX || 1), H / (spreadY || 1))))
            if (Math.abs(measured - fitScale.value) > 0.02) fitScale.value = measured
        }

        const zoom = isDag.value
            ? 1
            : Math.min(
                1,
                (W - padding * 2) / (spreadX || 1),
                (H - padding * 2) / (spreadY || 1),
            )
        const cx = (Math.min(...xs) + Math.max(...xs)) / 2
        const cy = (Math.min(...ys) + Math.max(...ys)) / 2 - (isDag.value ? HEADER_OFFSET / 2 : 0)
        viewState.value = {zoom, center: [cx, cy]}
        applyView(chart)
    }

    return {
        /** Returns the raw Element[] used by the Table component. */
        getElements: () => elements.value.data,
        /** Live computed nodes — reflects selection/filter/theme changes; used by applyStylesToChart and tests. */
        graphNodes,
        /** Live computed edges — reflects selection/filter/theme changes; used by applyStylesToChart and tests. */
        graphEdges,
        /** Frozen snapshot for KsGraph :nodes — set once after initial render. */
        chartNodes,
        /** Frozen snapshot for KsGraph :edges — set once after initial render. */
        chartEdges,
        /** Layout to hand KsGraph: explicit coordinates once any are known. */
        graphLayout,
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
                const allNodeCount = elements.value.data.filter((el) => el.data.type === NODE).length
                shownNodeIDs.value  = nodeIDs.length >= allNodeCount ? null : new Set(nodeIDs)
            },
            exportAsImage: (type: "jpeg" | "png", nodeID?: string) => {
                const ts       = new Date().toISOString().slice(0, 19).replace(/:/g, "-")
                const filename = `dependencies-${nodeID ? `${nodeID}-` : ""}${ts}.${type}`
                graphRef.value?.exportAsImage(type, filename)
            },
        },
    }
}

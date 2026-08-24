<template>
    <div
        ref="root"
        class="dag-canvas"
        :style="{
            '--ks-dag-card-width': `${DAG_CARD.width}px`,
            '--ks-dag-card-height': `${DAG_CARD.height}px`,
        }"
    >
        <VueFlow
            id="asset-dag"
            :nodes="vfNodes"
            :edges="vfEdges"
            :nodesDraggable="false"
            :nodesConnectable="false"
            :elementsSelectable="false"
            :elevateNodesOnSelect="false"
            :elevateEdgesOnSelect="false"
            :onlyRenderVisibleElements="true"
            :minZoom="0.2"
            :maxZoom="1.5"
            @nodeClick="onNodeClick"
            @nodeDoubleClick="onNodeDoubleClick"
            @nodeMouseEnter="onNodeEnter"
            @nodeMouseLeave="onNodeLeave"
            @paneClick="emit('pane-click')"
        >
            <Background :color="dotColor" :gap="24" :size="1" />

            <template #node-asset="nodeProps">
                <AssetNode :id="nodeProps.id" :data="nodeProps.data" />
            </template>
        </VueFlow>
    </div>
</template>

<script setup lang="ts">
    import {computed, provide, useTemplateRef, watch} from "vue"
    import {useResizeObserver} from "@vueuse/core"

    import {VueFlow, useVueFlow, Position, MarkerType} from "@vue-flow/core"
    import {Background} from "@vue-flow/background"

    import "@vue-flow/core/dist/style.css"
    import "@vue-flow/core/dist/theme-default.css"

    import {cssVar, stringUtils} from "@kestra-io/design-system"

    import {useTheme} from "../../../../utils/utils"

    import AssetNode from "./AssetNode.vue"
    import {computeDagLayout} from "../../utils/dagLayout"
    import {computeTrace, traceEdgeKey} from "../../utils/dagTrace"
    import {
        DAG_CARD,
        DAG_SELECTED,
        DAG_HOVERED,
        DAG_TRACED,
        DAG_DIMMED,
    } from "../../utils/dagConstants"
    import {NODE, EDGE, ASSET} from "../../utils/types"
    import type {Element, Node, Edge} from "../../utils/types"

    const props = defineProps<{
        elements: Element[];
        selected?: string;
        hovered?: string;
        /** Nodes still shown by the table filter; null means no filter. */
        dimmed?: Set<string> | null;
    }>()

    const emit = defineEmits<{
        select: [id: string];
        open: [id: string];
        hover: [id: string | undefined];
        "pane-click": [];
    }>()

    const {fitBounds, zoomIn, zoomOut, viewport} = useVueFlow("asset-dag")

    const nodes = computed(() => props.elements
        .filter((el): el is {data: Node} => el.data.type === NODE)
        .map(({data}) => data))

    const edges = computed(() => {
        // Endpoints must exist: the adapter can emit an edge whose source or target is not a
        // node, and vue-flow drops those with a console warning.
        const known = new Set(nodes.value.map((node) => node.id))
        return props.elements
            .filter((el): el is {data: Edge} => el.data.type === EDGE)
            .map(({data}) => data)
            .filter((edge) => known.has(edge.source) && known.has(edge.target))
    })

    /** A set, not a scan: ownColumn is called once per node. */
    const flowNodeIDs = computed(() => new Set(
        nodes.value.filter((node) => node.metadata.subtype !== ASSET).map((node) => node.id),
    ))

    /**
     * Flows that write at least one asset take the leading column, since the flow triggers the
     * lineage. A producing flow usually also consumes (dbt reads its seeds) and those edges are
     * kept, so pinning it left makes them run right-to-left; `backwards` below styles them.
     *
     * Known limitation: in a multi-flow graph a mid-stream flow (one consuming another flow's
     * output) is pinned too, which reads as less truthful than leaving it in rank order.
     */
    const producingFlowIDs = computed(() => {
        const produced = new Set(edges.value.map((edge) => edge.source))
        return new Set([...flowNodeIDs.value].filter((id) => produced.has(id)))
    })

    const layout = computed(() => computeDagLayout(
        nodes.value.map((node) => node.id),
        edges.value.map(({source, target}) => ({source, target})),
        {
            columnGap: DAG_CARD.width + 120,
            rowGap:    DAG_CARD.height + 32,
            ownColumn: (id) => producingFlowIDs.value.has(id),
        },
    ))

    /** Trailing segment of a dotted asset id, matching the side table's short name. */
    const shortName = (id: string): string => stringUtils.afterLastDot(id) || id

    const vfNodes = computed(() => nodes.value.flatMap((node) => {
        const position = layout.value.positions.get(node.id)
        if (!position) return []
        const metadata = node.metadata as {assetType?: string; producer?: string; status?: string; updated?: string}
        const isAsset = node.metadata.subtype === ASSET

        return [{
            id:   node.id,
            type: "asset",
            // computeDagLayout returns centres; vue-flow positions from the top-left.
            position: {
                x: position.x - DAG_CARD.width / 2,
                y: position.y - DAG_CARD.height / 2,
            },
            sourcePosition: Position.Right,
            targetPosition: Position.Left,
            data: {
                name: shortName(node.flow || node.id),
                // Falls back to the asset type's icon so a never-run asset still gets a real
                // glyph. A flow has no plugin FQCN, so it keeps a material icon.
                iconCls: isAsset ? (metadata.producer ?? metadata.assetType) : undefined,
                isFlow: !isAsset,
                assetType: isAsset ? metadata.assetType : undefined,
                status:  isAsset ? (metadata.status ?? "unknown") : "unknown",
                updated: metadata.updated,
            },
        }]
    }))

    const theme = useTheme()

    /** The node whose chain is lit: a pinned selection, or whatever is hovered. */
    const focusedID = computed(() => props.hovered ?? props.selected)

    // Flows are opaque: one flow writes many unrelated assets, so walking through it would
    // light the whole graph from any node.
    const trace = computed(() => computeTrace(edges.value, focusedID.value, (id) => flowNodeIDs.value.has(id)))

    const vfEdges = computed(() => {
        // cssVar returns a plain string, so only depending on the theme signal re-runs this.
        void theme.value
        const lit = trace.value
        const positions = layout.value.positions
        return edges.value.map((edge) => {
            const key = traceEdgeKey(edge.source, edge.target)
            const onPath = lit?.edges.has(key) ?? false
            const outsideFilter = props.dimmed ? !props.dimmed.has(edge.source) || !props.dimmed.has(edge.target) : false
            // From the laid-out coordinates, not the producer sets: any edge ending left of
            // where it starts is a back-edge, whatever put it there.
            const source = positions.get(edge.source)
            const target = positions.get(edge.target)
            const backwards = !!source && !!target && source.x > target.x

            return {
                id:     edge.id,
                source: edge.source,
                target: edge.target,
                type:   "smoothstep",
                markerEnd: {type: MarkerType.ArrowClosed, color: cssVar(onPath ? "--ks-border-focus" : "--ks-border-default")},
                // Inline, not in a stylesheet: topology.scss sets
                // `.vue-flow__container .vue-flow__edge-path` at a specificity a scoped block
                // cannot beat, and :deep() is banned.
                style: {
                    stroke: cssVar(onPath ? "--ks-border-focus" : "--ks-border-default"),
                    strokeWidth: onPath ? 2 : 1,
                    // Dashed and fainter so a right-to-left sweep reads as intentional; a lit
                    // back-edge keeps full opacity, or tracing stops being legible.
                    strokeDasharray: backwards ? "6 4" : undefined,
                    opacity: outsideFilter || (lit && !onPath) ? 0.4 : backwards && !onPath ? 0.65 : 1,
                },
            }
        })
    })

    // Same token, opacity and pitch as the Tree canvas, so the two read as one surface.
    // `color`, not the deprecated `patternColor`.
    const dotColor = computed(() => cssVar("--ks-topology-dash", "dark" === theme.value ? 0.2 : 0.3))

    const selectedRef = computed(() => props.selected)
    const hoveredRef = computed(() => props.hovered)
    const tracedRef = computed(() => trace.value?.nodes ?? null)
    const dimmedRef = computed(() => props.dimmed ?? null)

    provide(DAG_SELECTED, selectedRef)
    provide(DAG_HOVERED, hoveredRef)
    provide(DAG_TRACED, tracedRef)
    provide(DAG_DIMMED, dimmedRef)

    /** Layout bounds, known exactly from the positions rather than measured from the DOM. */
    const bounds = computed(() => {
        const positions = vfNodes.value.map(({position}) => position)
        if (!positions.length) return null
        const xs = positions.map(({x}) => x)
        const ys = positions.map(({y}) => y)
        const x = Math.min(...xs)
        const y = Math.min(...ys)

        return {
            x,
            y,
            width:  Math.max(...xs) - x + DAG_CARD.width,
            height: Math.max(...ys) - y + DAG_CARD.height,
        }
    })

    const fitToBounds = (): void => {
        if (bounds.value) fitBounds(bounds.value, {padding: 0.1})
    }

    /**
     * fitBounds, not fitView: fitView needs every node measured and `onlyRenderVisibleElements`
     * guarantees they never all are, so `nodesInitialized` never turns true. The layout already
     * knows the exact extent.
     */
    const applyFit = (): void => {
        fitToBounds()
        // While the camera still reads back as the one we set, we own it and may refit; once it
        // differs the user has moved it and it is theirs.
        lastAppliedView = {...viewport.value}
    }

    let lastAppliedView: {x: number; y: number; zoom: number} | null = null

    const ownsViewport = (): boolean => {
        if (!lastAppliedView) return true
        const {x, y, zoom} = viewport.value
        return Math.abs(x - lastAppliedView.x) < 1
            && Math.abs(y - lastAppliedView.y) < 1
            && Math.abs(zoom - lastAppliedView.zoom) < 0.001
    }

    /** A string, not the bounds object: watching a fresh object fires on every recompute. */
    const boundsKey = computed(() => {
        const value = bounds.value
        if (!value) return ""
        return [value.x, value.y, value.width, value.height].map(Math.round).join(":")
    })

    // Refit when the extent genuinely changes, but only while we still own the camera.
    watch(boundsKey, () => {
        requestAnimationFrame(() => {
            if (bounds.value && ownsViewport()) applyFit()
        })
    }, {immediate: true})

    /**
     * The pane keeps growing after the first fit (a flex child of a splitter panel that sizes
     * late) and the splitter resizes it again later. Refitting on resize covers both without a
     * guessed delay.
     */
    const root = useTemplateRef<HTMLElement>("root")
    useResizeObserver(root, () => {
        if (bounds.value && ownsViewport()) applyFit()
    })

    const onNodeClick = ({node}: {node: {id: string}}) => emit("select", node.id)
    const onNodeDoubleClick = ({node}: {node: {id: string}}) => emit("open", node.id)
    const onNodeEnter = ({node}: {node: {id: string}}) => emit("hover", node.id)
    const onNodeLeave = () => emit("hover", undefined)

    defineExpose({
        zoomIn: () => zoomIn(),
        zoomOut: () => zoomOut(),
        // applyFit, not bare fitToBounds: the fit must update the ownership snapshot, or a
        // user-initiated Fit leaves ownsViewport() false and kills resize refits.
        fit: applyFit,
    })
</script>

<style lang="scss" scoped>
    .dag-canvas {
        width: 100%;
        height: 100%;
        min-height: 0;
    }
</style>

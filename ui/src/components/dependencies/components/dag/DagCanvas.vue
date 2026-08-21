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
    import {computed, provide, ref, useTemplateRef, watch} from "vue"
    import {useResizeObserver} from "@vueuse/core"

    import {VueFlow, useVueFlow, Position, MarkerType} from "@vue-flow/core"
    import {Background} from "@vue-flow/background"

    // Imported here rather than relied on transitively: this component must keep working
    // if the page stops importing @kestra-io/topology, which is what pulls these in today.
    import "@vue-flow/core/dist/style.css"
    import "@vue-flow/core/dist/theme-default.css"

    import {cssVar} from "@kestra-io/design-system"

    import {useTheme} from "../../../../utils/utils"

    import AssetNode from "./AssetNode.vue"
    import {computeDagLayout} from "../../utils/dagLayout"
    import {computeTrace, traceEdgeKey} from "../../utils/dagTrace"
    import {
        DAG_CARD,
        DAG_LOD,
        DAG_SELECTED,
        DAG_HOVERED,
        DAG_TRACED,
        DAG_DIMMED,
        DAG_DETAIL,
        type DagDetail,
    } from "../../utils/dagConstants"
    import {NODE, EDGE, ASSET} from "../../utils/types"
    import type {Element, Node, Edge} from "../../utils/types"

    const props = defineProps<{
        elements: Element[];
        selected?: string;
        hovered?: string;
        /** Nodes still shown by the table filter and group isolation; null means no filter. */
        dimmed?: Set<string> | null;
        /** Group index per node, so members sit adjacent within their rank. */
        priorityOf?: (id: string) => number;
    }>()

    const emit = defineEmits<{
        select: [id: string];
        open: [id: string];
        hover: [id: string | undefined];
        "pane-click": [];
    }>()

    const {fitBounds, zoomIn, zoomOut, viewport} = useVueFlow()

    const nodes = computed(() => props.elements
        .filter((el): el is {data: Node} => el.data.type === NODE)
        .map(({data}) => data))

    /**
     * Edges into a flow node are dropped so the producing flow keeps its own leading
     * column: it has outgoing edges only, which ranks it first.
     */
    const edges = computed(() => {
        // Endpoints must exist: the adapter can emit edges whose source or target is not a
        // node (e.g. a dbt manifest parent that is neither a manifest key nor a graph node),
        // and vue-flow drops such edges with a console warning.
        const known = new Set(nodes.value.map((node) => node.id))
        const flowIDs = new Set(nodes.value.filter((node) => node.metadata.subtype !== ASSET).map((node) => node.id))
        return props.elements
            .filter((el): el is {data: Edge} => el.data.type === EDGE)
            .map(({data}) => data)
            .filter((edge) => known.has(edge.source) && known.has(edge.target) && !flowIDs.has(edge.target))
    })

    const layout = computed(() => computeDagLayout(
        nodes.value.map((node) => node.id),
        edges.value.map(({source, target}) => ({source, target})),
        {
            columnGap: DAG_CARD.width + 120,
            rowGap:    DAG_CARD.height + 32,
            priority:  props.priorityOf,
            ownColumn: (id) => nodes.value.some((node) => node.id === id && node.metadata.subtype !== ASSET),
        },
    ))

    /** Trailing segment of a dotted asset id, matching the side table's short name. */
    const shortName = (id: string): string => id.split(".").pop() || id

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
                // The tile shows the producing plugin's logo, falling back to the asset
                // type's own icon so a never-run asset still gets a real glyph rather
                // than a placeholder. A flow has no plugin FQCN, so it keeps a material icon.
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

    const trace = computed(() => computeTrace(edges.value, focusedID.value))

    const vfEdges = computed(() => {
        // cssVar resolves to a plain string, so a theme switch would not re-run this
        // computed on its own; depending on the theme signal keeps edge colours current.
        void theme.value
        const lit = trace.value
        return edges.value.map((edge) => {
            const key = traceEdgeKey(edge.source, edge.target)
            const onPath = lit?.edges.has(key) ?? false
            const outsideFilter = props.dimmed ? !props.dimmed.has(edge.source) || !props.dimmed.has(edge.target) : false

            return {
                id:     edge.id,
                source: edge.source,
                target: edge.target,
                type:   "smoothstep",
                markerEnd: {type: MarkerType.ArrowClosed, color: cssVar(onPath ? "--ks-border-focus" : "--ks-border-default")},
                // Inline rather than in a stylesheet: topology.scss sets
                // `.vue-flow__container .vue-flow__edge-path` at a specificity a scoped
                // block cannot beat, and :deep() is banned.
                style: {
                    stroke: cssVar(onPath ? "--ks-border-focus" : "--ks-border-default"),
                    strokeWidth: onPath ? 2 : 1,
                    opacity: outsideFilter || (lit && !onPath) ? 0.4 : 1,
                },
            }
        })
    })

    // Matches the Tree canvas exactly: same token at the same 30%/20% opacity and the
    // same 24px pitch, so the two layouts read as one surface. `color`, not the
    // deprecated `patternColor`.
    const dotColor = computed(() => cssVar("--ks-topology-dash", "dark" === theme.value ? 0.2 : 0.3))

    const detail = ref<DagDetail>("full")
    const selectedRef = computed(() => props.selected)
    const hoveredRef = computed(() => props.hovered)
    const tracedRef = computed(() => trace.value?.nodes ?? null)
    const dimmedRef = computed(() => props.dimmed ?? null)

    provide(DAG_SELECTED, selectedRef)
    provide(DAG_HOVERED, hoveredRef)
    provide(DAG_TRACED, tracedRef)
    provide(DAG_DIMMED, dimmedRef)
    provide(DAG_DETAIL, detail)

    // Hysteresis, not a single threshold: a pinch that settles on one value would
    // otherwise flip the detail level back and forth every frame.
    watch(() => viewport.value.zoom, (zoom) => {
        if (detail.value === "full" && zoom < DAG_LOD.toCompact) detail.value = "compact"
        else if (detail.value === "compact" && zoom > DAG_LOD.toFull) detail.value = "full"
    })

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
     * fitBounds rather than fitView, because fitView needs every node measured and
     * `onlyRenderVisibleElements` guarantees they never all are: culled nodes get no
     * dimensions, so `nodesInitialized` never turns true and the event never fires. The
     * layout already knows the exact extent, so nothing has to be measured at all.
     */
    const applyFit = (): void => {
        fitToBounds()
        // Remember the camera we just set. While it is still in place we own it and may
        // refit; once the reading differs the user has panned or zoomed, and it is theirs.
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

    /**
     * A rounded string rather than the bounds object: the computed returns a fresh object
     * every evaluation, so watching it directly would fire on every recompute regardless
     * of content (the deep-watch/computed-spread trap). The string only changes when the
     * extent genuinely moves.
     */
    const boundsKey = computed(() => {
        const value = bounds.value
        if (!value) return ""
        return [value.x, value.y, value.width, value.height].map(Math.round).join(":")
    })

    // Refit whenever the extent genuinely changes (first data, a re-grouping that
    // re-stacks columns) — but only while we still own the camera: a viewport the user
    // has panned or zoomed is theirs and must not be yanked back.
    watch(boundsKey, () => {
        requestAnimationFrame(() => {
            if (bounds.value && ownsViewport()) applyFit()
        })
    }, {immediate: true})

    /**
     * The pane keeps growing after the first fit (it is a flex child of a splitter panel
     * that sizes late), and dragging the splitter resizes it again later. Refitting on
     * resize covers both without a guessed delay, and the ownership check means a camera
     * the user has moved is never yanked back.
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
        // applyFit, not the bare fitToBounds: the fit must update the ownership snapshot,
        // or a user-initiated Fit leaves ownsViewport() false and kills resize refits.
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

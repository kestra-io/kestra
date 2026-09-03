<template>
    <div
        ref="root"
        class="canvas"
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
            @nodeClick="({node}) => emit('select', node.id)"
            @nodeDoubleClick="({node}) => emit('open', node.id)"
            @nodeMouseEnter="({node}) => emit('hover', node.id)"
            @nodeMouseLeave="emit('hover', undefined)"
            @paneClick="emit('pane-click')"
        >
            <Background
                :color="cssVar('--ks-topology-dash', theme === 'dark' ? 0.2 : 0.3)"
                :gap="24"
                :size="1"
            />

            <template #node-asset="nodeProps">
                <AssetNode
                    :id="nodeProps.id"
                    :data="nodeProps.data"
                />
            </template>
        </VueFlow>
    </div>
</template>

<script setup lang="ts">
    import {computed, provide, useTemplateRef, watch} from "vue"
    import {useResizeObserver} from "@vueuse/core"
    import {VueFlow, useVueFlow, Position, MarkerType} from "@vue-flow/core"
    import {Background} from "@vue-flow/background"
    import {useScreenshot} from "@kestra-io/topology"
    import {cssVar, stringUtils} from "@kestra-io/design-system"
    import {useTheme} from "../../../../utils/utils"
    import AssetNode from "./AssetNode.vue"
    import {computeDagLayout} from "../../utils/dagLayout"
    import {computeTrace, traceEdgeKey} from "../../utils/dagTrace"
    import {DAG_CARD, DAG_SELECTED, DAG_HOVERED, DAG_TRACED, DAG_SHOWN} from "../../utils/dagConstants"
    import {ASSET, nodesOf, edgesOf} from "../../utils/types"
    import type {Element} from "../../utils/types"

    const props = defineProps<{
        elements: Element[];
        selected?: string;
        hovered?: string;
        priorityOf?: (id: string) => number;
        shown?: Set<string> | null;
    }>()

    const emit = defineEmits<{
        select: [id: string];
        open: [id: string];
        hover: [id: string | undefined];
        "pane-click": [];
    }>()

    const {fitBounds, zoomIn, zoomOut, viewport, vueFlowRef} = useVueFlow("asset-dag")
    const theme = useTheme()
    const {capture} = useScreenshot()

    const nodes = computed(() => nodesOf(props.elements))

    const edges = computed(() => {
        const known = new Set(nodes.value.map((node) => node.id))
        return edgesOf(props.elements).filter((edge) => known.has(edge.source) && known.has(edge.target))
    })

    const flowNodeIDs = computed(() => new Set(
        nodes.value.filter((node) => node.metadata.subtype !== ASSET).map((node) => node.id),
    ))

    const layout = computed(() => {
        const produced = new Set(edges.value.map((edge) => edge.source))

        return computeDagLayout(
            nodes.value.map((node) => node.id),
            edges.value.map(({source, target}) => ({source, target})),
            {
                columnGap: DAG_CARD.width + 120,
                rowGap: DAG_CARD.height + 32,
                priority: props.priorityOf,
                ownColumn: (id) => flowNodeIDs.value.has(id) && produced.has(id),
            },
        )
    })

    const vfNodes = computed(() => nodes.value.flatMap((node) => {
        const position = layout.value.get(node.id)
        if (!position) {
            return []
        }

        const metadata = node.metadata as {
            assetType?: string;
            producer?: string;
            status?: string;
            updated?: string;
        }
        const isAsset = node.metadata.subtype === ASSET
        const label = node.flow || node.id

        return [{
            id: node.id,
            type: "asset",
            position: {
                x: position.x - DAG_CARD.width / 2,
                y: position.y - DAG_CARD.height / 2,
            },
            sourcePosition: Position.Right,
            targetPosition: Position.Left,
            data: {
                name: stringUtils.afterLastDot(label) || label,
                iconCls: isAsset ? (metadata.producer ?? metadata.assetType) : undefined,
                isFlow: !isAsset,
                assetType: isAsset ? metadata.assetType : undefined,
                status: isAsset ? (metadata.status ?? "unknown") : "unknown",
                updated: metadata.updated,
            },
        }]
    }))

    const trace = computed(() => computeTrace(
        edges.value,
        props.hovered ?? props.selected,
        (id) => flowNodeIDs.value.has(id),
    ))

    const vfEdges = computed(() => {
        void theme.value
        const lit = trace.value
        const positions = layout.value

        return edges.value.map((edge) => {
            const onPath = lit?.edges.has(traceEdgeKey(edge.source, edge.target)) ?? false
            const outsideFilter = props.shown
                ? !props.shown.has(edge.source) || !props.shown.has(edge.target)
                : false
            const source = positions.get(edge.source)
            const target = positions.get(edge.target)
            const backwards = !!source && !!target && source.x > target.x
            const stroke = cssVar(onPath ? "--ks-text-link" : "--ks-border-default")

            return {
                id: edge.id,
                source: edge.source,
                target: edge.target,
                type: "smoothstep",
                markerEnd: {type: MarkerType.ArrowClosed, color: stroke},
                style: {
                    stroke,
                    strokeWidth: onPath ? 2 : 1,
                    strokeDasharray: backwards ? "6 4" : undefined,
                    opacity: outsideFilter || (lit && !onPath)
                        ? 0.4
                        : backwards && !onPath ? 0.65 : 1,
                },
            }
        })
    })

    provide(DAG_SELECTED, computed(() => props.selected))
    provide(DAG_HOVERED, computed(() => props.hovered))
    provide(DAG_TRACED, computed(() => trace.value?.nodes ?? null))
    provide(DAG_SHOWN, computed(() => props.shown ?? null))

    const bounds = computed(() => {
        const positions = vfNodes.value.map(({position}) => position)
        if (!positions.length) {
            return null
        }

        const xs = positions.map(({x}) => x)
        const ys = positions.map(({y}) => y)
        const x = Math.min(...xs)
        const y = Math.min(...ys)

        return {
            x,
            y,
            width: Math.max(...xs) - x + DAG_CARD.width,
            height: Math.max(...ys) - y + DAG_CARD.height,
        }
    })

    let lastAppliedView: {x: number; y: number; zoom: number} | null = null

    /**
     * fitBounds, not fitView: fitView needs every node measured, and `onlyRenderVisibleElements`
     * guarantees they never all are. Reading the viewport back marks the camera as ours to refit;
     * once it differs the user has moved it and it is theirs.
     */
    const applyFit = (): void => {
        if (bounds.value) {
            fitBounds(bounds.value, {padding: 0.1})
        }
        lastAppliedView = {...viewport.value}
    }

    const ownsViewport = (): boolean => {
        if (!lastAppliedView) {
            return true
        }
        const {x, y, zoom} = viewport.value
        return Math.abs(x - lastAppliedView.x) < 1
            && Math.abs(y - lastAppliedView.y) < 1
            && Math.abs(zoom - lastAppliedView.zoom) < 0.001
    }

    const refitIfOwned = (): void => {
        if (bounds.value && ownsViewport()) {
            applyFit()
        }
    }

    watch(
        () => (bounds.value ? [bounds.value.x, bounds.value.y, bounds.value.width, bounds.value.height].map(Math.round).join(":") : ""),
        () => requestAnimationFrame(refitIfOwned),
        {immediate: true},
    )

    const root = useTemplateRef<HTMLElement>("root")
    useResizeObserver(root, refitIfOwned)

    defineExpose({
        zoomIn: () => zoomIn(),
        zoomOut: () => zoomOut(),
        fit: applyFit,
        exportAsImage: (type: "jpeg" | "png", fileName?: string): void => {
            if (vueFlowRef.value) {
                capture(vueFlowRef.value, {type, fileName, shouldDownload: true})
            }
        },
    })
</script>

<style lang="scss" scoped>
    .canvas {
        width: 100%;
        height: 100%;
        min-height: 0;
    }
</style>

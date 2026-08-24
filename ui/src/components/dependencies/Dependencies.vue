<template>
    <div class="dependencies-wrapper">
        <div v-if="dagView && !isLoading && getElements().length" class="dag-bar">
            <KsSegmented
                v-model="layoutMode"
                class="layout-toggle"
                size="small"
                :options="layoutOptions"
            />
            <!-- An empty-canvas click clears the selection and the pinned group but deliberately
                 not the field: the canvas is mostly empty space, so a stray click would
                 otherwise throw the whole arrangement away. -->
            <KsSelect
                v-model="groupField"
                class="group-select"
                size="small"
                clearable
                :placeholder="$t('dependency.dag.group_by')"
            >
                <KsOption :label="$t('dependency.dag.group_none')" value="" />
                <KsOption
                    v-for="field in groupFields"
                    :key="field.key"
                    :label="`${field.label} (${field.groups})`"
                    :value="field.key"
                    :disabled="!field.usable"
                />
            </KsSelect>

            <GroupPicker
                v-if="groupChips.length"
                :groups="groupChips"
                :activeGroup="activeGroup"
                @preview="isolateGroup"
                @toggle="toggleGroup"
            />

            <KsText v-if="summary" size="small" class="layout-summary">
                {{ summary }}
            </KsText>
        </div>
        <div v-if="isLoading" v-ks-loading="true" class="h-100" />
        <Empty v-else-if="!getElements().length" :type="`dependencies.${SUBTYPE}`" />
        <KsSplitter v-else class="dependencies">
            <KsSplitterPanel id="graph" v-bind="PANEL">
                <div class="graph-pane">
                    <div class="canvas-stack">
                        <!-- visibility:hidden, not v-if or v-show: remounting re-runs the force
                             simulation and loses the settled positions, and a display:none chart
                             measures 0x0, so a later resize corrupts the Tree view. -->
                        <div class="echarts-layer" :class="{'is-hidden': isDagCanvas}">
                            <KsGraph
                                ref="graphRef"
                                class="graph-canvas"
                                :nodes="chartNodes"
                                :edges="chartEdges"
                                :loading="isRendering"
                                :options="graphOptions"
                                @node-click="handleNodeClick"
                            />
                        </div>

                        <DagCanvas
                            v-if="isDagCanvas"
                            ref="dagCanvasRef"
                            class="dag-layer"
                            :elements="getElements()"
                            :selected="selectedNodeID"
                            :hovered="hoveredNodeID"
                            :dimmed="shownNodeIDs"
                            @select="selectNode"
                            @hover="(id) => (hoveredNodeID = id)"
                            @open="(id) => (openedNodeID = id)"
                            :priorityOf="dagPriority"
                            @pane-click="() => {selectedNodeID = undefined; clearGroup()}"
                        />
                    </div>

                    <div class="controls">
                        <KsButton
                            size="small"
                            :title="$t('dependency.controls.zoom_in')"
                            @click="controls.zoomIn"
                        >
                            <Plus />
                        </KsButton>
                        <KsButton
                            size="small"
                            :title="$t('dependency.controls.zoom_out')"
                            @click="controls.zoomOut"
                        >
                            <Minus />
                        </KsButton>
                        <KsButton
                            size="small"
                            :title="$t('dependency.controls.clear_selection')"
                            @click="controls.clearSelection"
                        >
                            <SelectionRemove />
                        </KsButton>
                        <KsButton
                            size="small"
                            :title="$t('dependency.controls.fit_view')"
                            @click="controls.fit"
                        >
                            <FitToScreenOutline />
                        </KsButton>
                        <!-- Hidden while the vue-flow canvas is up: export still targets the
                             chart, so it would hand back a picture of the wrong graph. -->
                        <KsDropdown v-if="!isDagCanvas">
                            <KsButton size="small" :title="$t('export')">
                                <Download />
                            </KsButton>
                            <template #dropdown>
                                <KsDropdownMenu>
                                    <KsDropdownItem @click="handlers.exportAsImage('jpeg', selectedNodeID)">
                                        {{ $t("export_as", {format: "JPEG"}) }}
                                    </KsDropdownItem>
                                    <KsDropdownItem @click="handlers.exportAsImage('png', selectedNodeID)">
                                        {{ $t("export_as", {format: "PNG"}) }}
                                    </KsDropdownItem>
                                </KsDropdownMenu>
                            </template>
                        </KsDropdown>
                    </div>
                </div>
            </KsSplitterPanel>

            <!-- Absolute floor, not a percentage: at 20% of a small window the detail table
                 wraps values one character per line. -->
            <KsSplitterPanel id="table" min="320px">
                <NodeDetails
                    v-if="selectedNode"
                    :node="selectedNode"
                    :subtype="SUBTYPE"
                    @close="controls.clearSelection"
                />
                <Table
                    v-show="!selectedNode"
                    :elements="getElements()"
                    :highlightShown="handlers.highlightShown"
                    :selected="selectedNodeID"
                    :subtype="SUBTYPE"
                    @select="selectNode"
                    @hover="onHover"
                    @open="openNode"
                />
            </KsSplitterPanel>
        </KsSplitter>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue"

    import Table from "./components/Table.vue"
    import NodeDetails from "./components/NodeDetails.vue"
    import DagCanvas from "./components/dag/DagCanvas.vue"
    import GroupPicker from "./components/dag/GroupPicker.vue"
    import Empty from "../layout/empty/Empty.vue"

    import {KsGraph, stringUtils} from "@kestra-io/design-system"

    import {useI18n} from "vue-i18n"

    import {useDependencies} from "./composables/useDependencies"
    import type {LayoutMode} from "./composables/useDependencies"
    import {FLOW, EXECUTION, NAMESPACE, ASSET} from "./utils/types"
    import {normalizeStatus, compactAge} from "./utils/assetStatus"
    import type {Types, Node} from "./utils/types"

    const {t} = useI18n({useScope: "global"})

    const PANEL = {size: "70%", min: "30%", max: "80%"}

    import {useRoute, useRouter} from "vue-router"
    import {routeFamily} from "../../utils/routeFamily"
    const route = useRoute()

    import Plus from "vue-material-design-icons/Plus.vue"
    import Minus from "vue-material-design-icons/Minus.vue"
    import SelectionRemove from "vue-material-design-icons/SelectionRemove.vue"
    import FitToScreenOutline from "vue-material-design-icons/FitToScreenOutline.vue"
    import Download from "vue-material-design-icons/Download.vue"
    import {use} from "echarts/core"
    import {TitleComponent} from "echarts/components"

    const props = defineProps<{
        fetchAssetDependencies?: () => Promise<{
            data: any[];
            count: number;
        }>;
        /** Opt in to the force / layered-DAG layout toggle. */
        dagView?: boolean;
    }>()

    const layoutMode = ref<LayoutMode>("force")

    const groupField = ref("")

    /**
     * Bucket for nodes the field cannot apply to, kept distinct from Ungrouped: "this flow has
     * no asset type" and "this asset is missing its type" are different facts. Leading space so
     * the key cannot collide with a real value.
     */
    const NOT_APPLICABLE = " not-applicable"

    /**
     * Read off each node so only fields with data are offered. `assetOnly` marks fields a flow
     * cannot have; `namespace` is not one, since grouping flows by namespace is meaningful.
     */
    const GROUP_FIELDS = [
        // The structured `schema` field every plugin emitting a Table sets, so this is
        // plugin-agnostic; the id slice is the fallback when a producer emitted neither.
        {key: "dataset", label: () => t("dependency.dag.group_dataset"), assetOnly: true, of: (node: Node) => (node.metadata as {schema?: string}).schema ?? schemaOf(node.flow)},
        {key: "type", label: () => t("type"), assetOnly: true, of: (node: Node) => typeNameOf((node.metadata as {assetType?: string}).assetType)},
        // Labelled "Plugins" because the keys ARE plugins (dbt, jdbc, scripts).
        {key: "producer", label: () => t("plugins.names"), assetOnly: true, of: (node: Node) => pluginOf((node.metadata as {producer?: string}).producer)},
        {key: "system", label: () => t("dependency.dag.system"), assetOnly: true, of: (node: Node) => (node.metadata as {system?: string}).system},
        // Not assetOnly: a flow has a namespace, so grouping flows by it is meaningful.
        {key: "namespace", label: () => t("namespace"), assetOnly: false, of: (node: Node) => node.namespace},
    ] as const

    const typeNameOf = (type?: string): string | undefined => (type ? stringUtils.afterLastDot(type) : undefined)

    /**
     * Plugin an FQCN belongs to: `io.kestra.plugin.jdbc.duckdb.Query` gives `jdbc`, the artifact
     * rather than the technology. The fourth segment only means anything under
     * `io.kestra.plugin.`, so a third-party FQCN falls back to the package holding the class.
     */
    const pluginOf = (type?: string): string | undefined => {
        if (!type) return undefined
        const segments = type.split(".")
        if (type.startsWith("io.kestra.plugin.")) return segments.length >= 4 ? segments[3] : undefined
        return segments.length >= 2 ? segments[segments.length - 2] : undefined
    }

    const schemaOf = (id: string): string | undefined => {
        const segments = id.split(".")
        return segments.length >= 3 ? segments[segments.length - 2] : undefined
    }

    const graphNodesList = computed(() => getElements()
        .filter((el): el is {data: Node} => el.data.type === "NODE")
        .map(({data}) => data))

    /** The accessor a field groups by, including the flows bucket the chips also count. */
    const accessorFor = (field: typeof GROUP_FIELDS[number]) => (node: Node) =>
        (field.assetOnly && node.metadata.subtype !== ASSET ? NOT_APPLICABLE : field.of(node))

    const groupFields = computed(() => GROUP_FIELDS.map((field) => {
        const accessor = accessorFor(field)
        const groups = new Set(graphNodesList.value.map((node) => accessor(node)).filter(Boolean)).size

        return {
            key:    field.key,
            label:  field.label(),
            groups,
            // One group says nothing, and a lane per node is a diagonal rather than a grouping.
            usable: groups > 1 && groups < graphNodesList.value.length,
        }
    }).filter((field) => field.groups > 0))

    /** One chip per group in the current graph; the two catch-all buckets sort last. */
    const groupChips = computed(() => {
        const accessor = groupOf.value
        if (!accessor) return []

        const counts = new Map<string, number>()
        graphNodesList.value.forEach((node) => {
            const key = accessor(node) ?? ""
            counts.set(key, (counts.get(key) ?? 0) + 1)
        })

        const rank = (key: string): number => (key === "" ? 2 : key === NOT_APPLICABLE ? 1 : 0)
        const labelOf = (key: string): string =>
            (key === NOT_APPLICABLE ? t("flows") : key || t("dependency.dag.ungrouped"))

        return [...counts.entries()]
            .sort(([a], [b]) => (rank(a) - rank(b)) || (a < b ? -1 : 1))
            .map(([key, count]) => ({key, count, label: labelOf(key)}))
    })

    // Switching the grouping field invalidates whichever group was pinned.
    // Wrapped, not passed by reference: clearGroup is destructured from the composable below.
    watch(groupField, () => clearGroup())

    const groupOf = computed(() => {
        const field = GROUP_FIELDS.find((candidate) => candidate.key === groupField.value)
        return field ? accessorFor(field) : undefined
    })

    /**
     * Group index per node, so members sit adjacent within their rank. Chip order is the source
     * of truth, so the canvas and the chip row agree on which group comes first.
     */
    const dagPriority = computed(() => {
        const accessor = groupOf.value
        if (!accessor) return undefined

        const rank = new Map(groupChips.value.map((chip, index) => [chip.key, index]))
        const byNode = new Map(graphNodesList.value.map((node) => [node.id, rank.get(accessor(node) ?? "") ?? 0]))
        return (id: string) => byNode.get(id) ?? 0
    })

    // Pinned to the force arm: the chart only ever renders Tree now, and letting the view
    // toggle reach these options would strip preserveAspect and roamTrigger from a live Tree
    // series, which is the anisotropic fit that renders nodes as ellipses.
    const graphOptions = computed(() => ({
        series: [{
            // Pinned to the whole canvas: ECharts binds roam to the series box and would
            // otherwise size it to the content, leaving a dead border where dragging does
            // nothing.
            left: 0,
            top: 0,
            right: 0,
            bottom: 0,
            // Without this, the layout:"none" switch lets ECharts stretch-fit the bounding rect
            // with independent scaleX/scaleY, squashing every node into an ellipse.
            preserveAspect: true,
            roamTrigger: "global",
            // Asset view only; the three sibling graphs keep the focus:"none" they always had.
            emphasis: {focus: props.dagView ? "adjacency" : "none"},
        }],
    }))

    const layoutOptions = computed(() => [
        {label: t("dependency.dag.force"), value: "force"},
        {label: t("dependency.dag.layered"), value: "dag"},
    ])

    const SUBTYPE: Types = ((): Types => {
        switch (routeFamily(route.name)) {
        case "flows/update": return FLOW
        case "namespaces/update": return NAMESPACE
        case "assets/update": return ASSET
        default: return EXECUTION
        }
    })()

    const graphRef = ref(null)
    const initialNodeID: string = SUBTYPE === FLOW || SUBTYPE === NAMESPACE || SUBTYPE === ASSET ? String(route.params.id || route.params.assetId) : String(route.params.flowId)

    use([TitleComponent])

    const {
        getElements,
        chartNodes,
        chartEdges,
        isLoading,
        isRendering,
        selectedNodeID,
        selectNode,
        highlightNode,
        openedNodeID,
        handleNodeClick,
        handlers,
        shownNodeIDs,
        clearFilters,
        isolateGroup,
        toggleGroup,
        clearGroup,
        activeGroup,
    } = useDependencies(graphRef, SUBTYPE, initialNodeID, route.params, props.fetchAssetDependencies, groupOf, Boolean(props.dagView))

    const dagCanvasRef = ref<{zoomIn: () => void; zoomOut: () => void; fit: () => void} | null>(null)
    const hoveredNodeID = ref<string | undefined>(undefined)

    /** DAG renders through vue-flow; Tree and the three sibling views stay on the chart. */
    const isDagCanvas = computed(() => Boolean(props.dagView) && layoutMode.value === "dag")

    /**
     * Identical to `handlers` unless the vue-flow canvas is up. The DAG arm rebuilds
     * clearSelection rather than delegating: `handlers.clearSelection` also refits the chart.
     */
    const controls = computed(() => (isDagCanvas.value
        ? {
            zoomIn:  () => dagCanvasRef.value?.zoomIn(),
            zoomOut: () => dagCanvasRef.value?.zoomOut(),
            fit:     () => dagCanvasRef.value?.fit(),
            clearSelection: () => {
                selectedNodeID.value = undefined
                clearFilters()
                dagCanvasRef.value?.fit()
            },
        }
        : handlers))

    /** Table-row hover drives the vue-flow trace, or the chart's own emphasis. Asset view only. */
    const onHover = (id?: string): void => {
        if (isDagCanvas.value) hoveredNodeID.value = id
        else if (props.dagView) highlightNode(id)
    }

    const router = useRouter()

    /** Double click opens the node's own page; single click only selects. Asset view only. */
    const openNode = (node: Node): void => {
        // The flow, execution and namespace views never navigated on double click.
        if (!props.dagView) return
        const {subtype} = node.metadata
        const tenant = route.params.tenant

        if (subtype === ASSET) {
            router.push({name: "assets/update", params: {tenant, assetId: node.flow}})
            return
        }

        // An execution node belongs to an execution, not to the flow that produced it.
        if (subtype === EXECUTION && "id" in node.metadata && node.metadata.id) {
            router.push({
                name: "executions/update",
                params: {tenant, namespace: node.namespace, flowId: node.flow, id: node.metadata.id},
            })
            return
        }

        router.push({name: "flows/update", params: {tenant, namespace: node.namespace, id: node.flow}})
    }

    watch(openedNodeID, (id) => {
        if (!props.dagView || !id) return
        const element = getElements().find((el): el is {data: Node} => el.data.type === "NODE" && el.data.id === id)
        if (element) openNode(element.data)
        // Released, or the watch latches on that id and every later double click is a no-op.
        openedNodeID.value = undefined
    })

    /** One line answering "is anything wrong here" before the user reads a single node. */
    const summary = computed(() => {
        if (!props.dagView) return ""

        const assets = getElements()
            .filter((el): el is {data: Node} => el.data.type === "NODE" && el.data.metadata.subtype === ASSET)
            .map(({data}) => data.metadata as {status?: string; updated?: string})

        if (!assets.length) return ""

        // Parsed epochs, not string order: mixed UTC offsets sort wrongly as strings.
        const at = (value?: string): number => (value ? Date.parse(value) : Number.NaN)
        const counts: Record<string, number> = {}
        let latest: string | undefined
        assets.forEach((asset) => {
            const state = normalizeStatus(asset.status)
            counts[state] = (counts[state] ?? 0) + 1
            if (!Number.isNaN(at(asset.updated)) && (!latest || at(asset.updated) > at(latest))) latest = asset.updated
        })
        const {fresh = 0, stale = 0, failed = 0, unknown: untracked = 0} = counts

        const parts = []
        if (failed) parts.push(t("dependency.dag.summary.failed", {n: failed}))
        if (stale) parts.push(t("dependency.dag.summary.issues", {n: stale}))
        // "All" has to mean all of them, not just all of the ones we could judge.
        if (!parts.length && fresh === assets.length) parts.push(t("dependency.dag.summary.fresh", {n: fresh}))
        if (untracked) parts.push(t("dependency.dag.summary.unknown", {n: untracked}))
        if (latest) parts.push(t("dependency.dag.summary.last_run", {ago: compactAge(latest)}))

        return parts.join(" · ")
    })

    /** DAG view swaps the table for the selected node's details, keeping the canvas in place. */
    const selectedNode = computed(() => {
        if (!props.dagView || !selectedNodeID.value) return undefined
        const element = getElements()
            .find((el): el is {data: Node} => el.data.type === "NODE" && el.data.id === selectedNodeID.value)
        return element?.data
    })

</script>

<style scoped lang="scss">
.dependencies-wrapper {
    display: flex;
    flex-direction: column;
    width: 100%;
    height: 100%;
}

// align-items: stretch, not center: the design system sizes a small KsSegmented and a
// KsButton differently, so centring leaves them visibly unequal in height.
.dag-bar {
    display: flex;
    align-items: stretch;
    flex-wrap: wrap;
    gap: var(--ks-spacing-2);
    padding: var(--ks-spacing-2);
}

.layout-toggle {
    flex: 0 0 auto;
}

// The summary answers "is anything wrong here" and is the highest-priority text on the row, so
// it never shrinks; the group select absorbs the loss instead.
.group-select {
    flex: 0 1 11rem;
    min-width: 0;
}

.layout-summary {
    flex: 0 1 auto;
    min-width: 12rem;
    margin-left: auto;
    color: var(--ks-text-secondary);
    text-align: right;
}

.dependencies {
    display: flex;
    width: 100%;
    flex: 1;
    min-height: 0;

    & div#graph {
        // The splitter panel itself stays static, so overlays anchor to .graph-pane.

        & .graph-pane {
            position: relative; // anchors the zoom controls
            height: 100%;
            // The toolbar is a docked row, not an overlay; the controls stay absolute,
            // bottom-anchored and floating over the canvas by design.
            display: flex;
            flex-direction: column;
            min-height: 0;
        }

        & .canvas-stack {
            position: relative;
            flex: 1;
            min-height: 0;
        }

        & .echarts-layer,
        & .dag-layer {
            position: absolute;
            inset: 0;
        }

        // visibility, not opacity: an opacity-0 canvas still hit-tests and eats events.
        & .echarts-layer.is-hidden {
            visibility: hidden;
        }

        & .dag-layer {
            z-index: 1;
        }

        & .graph-canvas {
            width: 100%;
            height: 100%;
            overflow: hidden;
            background-color: transparent;
            background-image: radial-gradient(circle, color-mix(in srgb, var(--ks-topology-dash) 30%, transparent) 1px, transparent 1px);
            background-repeat: repeat;
            background-size: 24px 24px;

            .dark & {
                background-image: radial-gradient(circle, color-mix(in srgb, var(--ks-topology-dash) 20%, transparent) 1px, transparent 1px);
            }
        }

        & .controls {
            position: absolute;
            z-index: 2;
            bottom: var(--ks-spacing-4);
            left: var(--ks-spacing-3);
            display: flex;
            flex-direction: column;
            justify-content: flex-end;
            gap: var(--ks-spacing-1);

            & button {
                width: 2rem;
                height: 2rem;
                margin: 0;
            }
        }
    }

    & div#table {
        display: flex;
        flex-direction: column;
        height: 100%;
        overflow-y: auto;
    }
}
</style>

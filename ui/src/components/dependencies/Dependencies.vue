<template>
    <div class="dependencies-wrapper">
        <DagToolbar
            v-if="showToolbar"
            v-model:layoutMode="layoutMode"
            v-model:groupField="groupField"
            :nodes="graphNodesList"
            :groupFields="groupFields"
            :groupChips="groupChips"
            :activeGroup="activeGroup"
            @preview="isolateGroup"
            @toggle="toggleGroup"
        />
        <div
            v-if="isLoading"
            v-ks-loading="true"
            class="h-100"
        />
        <Empty
            v-else-if="!hasElements"
            :type="`dependencies.${SUBTYPE}`"
        />
        <KsSplitter v-else class="dependencies">
            <KsSplitterPanel id="graph" v-bind="PANEL">
                <div class="graph-pane">
                    <div class="canvas-stack">
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
                            :shown="shownNodeIDs"
                            :priorityOf="dagPriority"
                            @select="selectNode"
                            @hover="(id) => (hoveredNodeID = id)"
                            @open="(id) => (openedNodeID = id)"
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
                        <KsDropdown>
                            <KsButton size="small" :title="$t('export')">
                                <Download />
                            </KsButton>
                            <template #dropdown>
                                <KsDropdownMenu>
                                    <KsDropdownItem @click="controls.exportAsImage('jpeg', selectedNodeID)">
                                        {{ $t("export_as", {format: "JPEG"}) }}
                                    </KsDropdownItem>
                                    <KsDropdownItem @click="controls.exportAsImage('png', selectedNodeID)">
                                        {{ $t("export_as", {format: "PNG"}) }}
                                    </KsDropdownItem>
                                </KsDropdownMenu>
                            </template>
                        </KsDropdown>
                    </div>
                </div>
            </KsSplitterPanel>

            <KsSplitterPanel id="table" min="320px">
                <NodeDetails
                    v-if="selectedNode"
                    :node="selectedNode"
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
    import {useRoute, useRouter} from "vue-router"
    import {use} from "echarts/core"
    import {TitleComponent} from "echarts/components"
    import {KsGraph} from "@kestra-io/design-system"
    import Plus from "vue-material-design-icons/Plus.vue"
    import Minus from "vue-material-design-icons/Minus.vue"
    import SelectionRemove from "vue-material-design-icons/SelectionRemove.vue"
    import FitToScreenOutline from "vue-material-design-icons/FitToScreenOutline.vue"
    import Download from "vue-material-design-icons/Download.vue"
    import Table from "./components/Table.vue"
    import NodeDetails from "./components/NodeDetails.vue"
    import DagCanvas from "./components/dag/DagCanvas.vue"
    import DagToolbar from "./components/dag/DagToolbar.vue"
    import Empty from "../layout/empty/Empty.vue"
    import {useDependencies} from "./composables/useDependencies"
    import type {LayoutMode} from "./composables/useDependencies"
    import {useDagGrouping} from "./composables/useDagGrouping"
    import {routeFamily} from "../../utils/routeFamily"
    import {FLOW, EXECUTION, NAMESPACE, ASSET, nodesOf} from "./utils/types"
    import type {Types, Node, Element} from "./utils/types"

    const props = defineProps<{
        fetchAssetDependencies?: () => Promise<{
            data: Element[];
            count: number;
        }>;
        /** Opt in to the force / layered-DAG layout toggle. */
        dagView?: boolean;
    }>()

    const route = useRoute()
    const router = useRouter()

    use([TitleComponent])

    const PANEL = {size: "70%", min: "30%", max: "80%"}

    const layoutMode = ref<LayoutMode>("force")

    const {
        nodes: graphNodesList,
        groupField,
        groupFields,
        groupOf,
        groupChips,
        dagPriority,
    } = useDagGrouping(() => nodesOf(getElements()))

    const graphOptions = computed(() => ({
        series: [{
            // Pinned to the whole canvas: ECharts otherwise binds roam to the content box, leaving a dead border.
            left: 0,
            top: 0,
            right: 0,
            bottom: 0,
            // Without preserveAspect the layout:"none" switch stretch-fits with independent scaleX/scaleY, squashing nodes into ellipses.
            preserveAspect: true,
            roamTrigger: "global",
            // Asset view only; the three sibling graphs keep the focus:"none" they always had.
            emphasis: {focus: props.dagView ? "adjacency" : "none"},
        }],
    }))

    const SUBTYPE: Types = ((): Types => {
        switch (routeFamily(route.name)) {
        case "flows/update": return FLOW
        case "namespaces/update": return NAMESPACE
        case "assets/update": return ASSET
        default: return EXECUTION
        }
    })()

    const graphRef = ref(null)
    const initialNodeID: string = SUBTYPE === EXECUTION
        ? String(route.params.flowId)
        : String(route.params.id || route.params.assetId)

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

    const dagCanvasRef = ref<{
        zoomIn: () => void;
        zoomOut: () => void;
        fit: () => void;
        exportAsImage: (type: "jpeg" | "png", fileName?: string) => void;
    } | null>(null)
    const hoveredNodeID = ref<string | undefined>(undefined)

    /** DAG renders through vue-flow; Tree and the three sibling views stay on the chart. */
    const isDagCanvas = computed(() => Boolean(props.dagView) && layoutMode.value === "dag")

    const hasElements = computed(() => getElements().length > 0)

    const showToolbar = computed(() => Boolean(props.dagView) && !isLoading.value && hasElements.value)

    /** The DAG arm rebuilds clearSelection rather than delegating: `handlers.clearSelection` also refits the chart. */
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
            exportAsImage: (type: "jpeg" | "png", nodeID?: string) => {
                const ts = new Date().toISOString().slice(0, 19).replace(/:/g, "-")
                dagCanvasRef.value?.exportAsImage(type, `dependencies-${nodeID ? `${nodeID}-` : ""}${ts}`)
            },
        }
        : handlers))

    /** Table-row hover drives the vue-flow trace, or the chart's own emphasis. Asset view only. */
    const onHover = (id?: string): void => {
        if (isDagCanvas.value) hoveredNodeID.value = id
        else if (props.dagView) highlightNode(id)
    }

    /** Double click opens the node's own page; the flow, execution and namespace views never navigated on it. */
    const openNode = (node: Node): void => {
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

    watch(groupField, () => clearGroup())

    watch(openedNodeID, (id) => {
        if (!props.dagView || !id) return
        const node = nodesOf(getElements()).find((candidate) => candidate.id === id)
        if (node) openNode(node)
        // Released, or the watch latches on that id and every later double click is a no-op.
        openedNodeID.value = undefined
    })

    /** DAG view swaps the table for the selected node's details, keeping the canvas in place. */
    const selectedNode = computed(() => {
        if (!props.dagView || !selectedNodeID.value) return undefined
        return nodesOf(getElements()).find((node) => node.id === selectedNodeID.value)
    })
</script>

<style scoped lang="scss">
.dependencies-wrapper {
    display: flex;
    flex-direction: column;
    width: 100%;
    height: 100%;
}

.dependencies {
    display: flex;
    width: 100%;
    flex: 1;
    min-height: 0;

    & div#graph {
        & .graph-pane {
            position: relative;
            height: 100%;
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

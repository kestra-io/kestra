<template>
    <div class="dependencies-wrapper">
        <div v-if="showExecutionChart" class="chart-header">
            <ChartDurationSelect v-model="chartDuration" />
            <TimeSeries
                v-show="chartHasData"
                ref="chartRef"
                :chart="chartDefinition"
                :filters="chartFilters()"
                showDefault
                execution
            />
        </div>
        <div v-if="isLoading" v-ks-loading="true" class="h-100" />
        <Empty v-else-if="!getElements().length" :type="`dependencies.${SUBTYPE}`" />
        <KsSplitter v-else class="dependencies">
            <KsSplitterPanel id="graph" v-bind="PANEL">
                <div class="graph-pane">
                    <KsGraph
                        ref="graphRef"
                        class="graph-canvas"
                        :nodes="chartNodes"
                        :edges="chartEdges"
                        :loading="isRendering"
                        :layout="graphLayout"
                        :options="graphOptions"
                        @node-click="handleNodeClick"
                    />

                    <div v-if="dagView" class="dag-bar">
                        <KsSegmented
                            v-model="layoutMode"
                            class="layout-toggle"
                            size="small"
                            :options="layoutOptions"
                        />
                        <KsSelect
                            v-model="groupField"
                            class="group-select"
                            size="small"
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

                        <KsText v-if="summary" size="small" class="layout-summary">
                            {{ summary }}
                        </KsText>
                    </div>

                    <div v-if="groupChips.length" class="group-chips">
                        <button
                            v-for="chip in groupChips"
                            :key="chip.key"
                            type="button"
                            :class="['group-chip', {'is-active': chip.key === activeGroup}]"
                            @mouseenter="isolateGroup(chip.key)"
                            @mouseleave="isolateGroup(activeGroup)"
                            @click="toggleGroup(chip.key)"
                        >
                            {{ chip.label }} ({{ chip.count }})
                        </button>
                    </div>

                    <div v-if="dagView && layoutMode === 'dag'" class="dag-legend">
                        <span v-for="state in presentStatuses" :key="state" class="legend-item">
                            <i :class="`legend-swatch status-${state}`" />
                            <KsText size="small">{{ $t(`dependency.dag.status.${state}`) }}</KsText>
                        </span>
                    </div>

                    <div class="controls">
                        <KsButton
                            size="small"
                            :title="$t('dependency.controls.zoom_in')"
                            @click="handlers.zoomIn"
                        >
                            <Plus />
                        </KsButton>
                        <KsButton
                            size="small"
                            :title="$t('dependency.controls.zoom_out')"
                            @click="handlers.zoomOut"
                        >
                            <Minus />
                        </KsButton>
                        <KsButton
                            size="small"
                            :title="$t('dependency.controls.clear_selection')"
                            @click="handlers.clearSelection"
                        >
                            <SelectionRemove />
                        </KsButton>
                        <KsButton
                            size="small"
                            :title="$t('dependency.controls.fit_view')"
                            @click="handlers.fit"
                        >
                            <FitToScreenOutline />
                        </KsButton>
                        <KsDropdown>
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

            <KsSplitterPanel id="table">
                <NodeDetails
                    v-if="selectedNode"
                    :node="selectedNode"
                    :subtype="SUBTYPE"
                    @close="handlers.clearSelection"
                />
                <Table
                    v-else
                    :elements="getElements()"
                    :highlightShown="handlers.highlightShown"
                    :selected="selectedNodeID"
                    :subtype="SUBTYPE"
                    @select="selectNode"
                    @hover="highlightNode"
                    @open="openNode"
                />
            </KsSplitterPanel>
        </KsSplitter>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, useTemplateRef, watch} from "vue"

    import Table from "./components/Table.vue"
    import NodeDetails from "./components/NodeDetails.vue"
    import Empty from "../layout/empty/Empty.vue"
    import TimeSeries from "../dashboard/sections/TimeSeries.vue"
    import ChartDurationSelect from "../executions/date-select/ChartDurationSelect.vue"
    import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"

    import {KsGraph} from "@kestra-io/design-system"
    import {QueryFilter} from "@kestra-io/kestra-sdk"

    import {useI18n} from "vue-i18n"
    import moment from "moment"

    import {useDependencies} from "./composables/useDependencies"
    import type {LayoutMode} from "./composables/useDependencies"
    import {FLOW, EXECUTION, NAMESPACE, ASSET} from "./utils/types"
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

    const LEGEND = ["fresh", "stale", "failed", "never", "unknown"] as const

    const groupField = ref("")

    /** Legend lists only the statuses on screen: a permanent five-entry key is noise. */
    const presentStatuses = computed(() => {
        const present = new Set(graphNodesList.value
            .filter((node) => node.metadata.subtype === ASSET)
            .map((node) => (node.metadata as {status?: string}).status ?? "unknown"))
        return LEGEND.filter((state) => present.has(state))
    })

    /** Grouping fields, read off each node so only ones with data are offered. */
    const GROUP_FIELDS = [
        {key: "dataset", label: () => t("dependency.dag.group_dataset"), of: (node: Node) => schemaOf(node.flow)},
        {key: "kind", label: () => t("dependency.dag.kind"), of: (node: Node) => (node.metadata as {kind?: string}).kind},
        {key: "system", label: () => t("dependency.dag.system"), of: (node: Node) => (node.metadata as {system?: string}).system},
        {key: "namespace", label: () => t("namespace"), of: (node: Node) => node.namespace},
    ] as const

    const schemaOf = (id: string): string | undefined => {
        const segments = id.split(".")
        return segments.length >= 3 ? segments[segments.length - 2] : undefined
    }

    const graphNodesList = computed(() => getElements()
        .filter((el): el is {data: Node} => el.data.type === "NODE")
        .map(({data}) => data))

    const groupFields = computed(() => GROUP_FIELDS.map((field) => {
        const values = graphNodesList.value.map(field.of).filter(Boolean)
        const groups = new Set(values).size

        return {
            key:    field.key,
            label:  field.label(),
            groups,
            // One group says nothing, and a lane per node is a diagonal rather than a grouping.
            usable: groups > 1 && groups < graphNodesList.value.length,
        }
    }).filter((field) => field.groups > 0))

    /** One chip per group in the current graph, ungrouped last. */
    const groupChips = computed(() => {
        const accessor = groupOf.value
        if (!accessor) return []

        const counts = new Map<string, number>()
        graphNodesList.value.forEach((node) => {
            const key = accessor(node) ?? ""
            counts.set(key, (counts.get(key) ?? 0) + 1)
        })

        return [...counts.entries()]
            .sort(([a], [b]) => (a === "" ? 1 : b === "" ? -1 : a < b ? -1 : 1))
            .map(([key, count]) => ({key, count, label: key || t("dependency.dag.ungrouped")}))
    })

    // Switching the grouping field invalidates whichever group was pinned.
    watch(() => groupField.value, () => clearGroup())

    const groupOf = computed(() => {
        const field = GROUP_FIELDS.find((candidate) => candidate.key === groupField.value)
        return field ? (node: Node) => field.of(node) : undefined
    })

    /**
     * DAG view insets the series box so the graph never touches the toolbar, the
     * legend or the pane edges, and clamps roam so cards cannot be zoomed into a
     * pile: ECharts scales positions but not symbol size.
     */
    const graphOptions = computed(() => ({
        series: [{
            // Both layouts share this canvas, so they share its fixes. The series box is
            // pinned to the whole canvas because ECharts binds roam to that box and would
            // otherwise size it to the content, leaving a dead border where dragging does
            // nothing. DAG margins come from spacer nodes padding the data extent instead.
            left: 0,
            top: 0,
            right: 0,
            bottom: 0,
            // Only DAG needs a floor: its cards are sized in pixels, so zooming out piles
            // them up. A large force graph may legitimately need to zoom out further.
            ...(layoutMode.value === "dag"
                ? {scaleLimit: {min: 0.4, max: 2.5}}
                // Tree captures force positions and switches to layout:"none"; without this
                // ECharts stretch-fits that bounding rect onto the box with independent
                // scaleX/scaleY, which squashes every node into an ellipse. roamTrigger keeps
                // dragging on the whole canvas once the fit is aspect-contained.
                : {preserveAspect: true, roamTrigger: "global"}),
            // Tree view dims everything but the hovered node and its neighbours, which is
            // what makes a dense force graph readable; DAG columns already read on their own.
            emphasis: {focus: layoutMode.value === "dag" ? "none" : "adjacency"},
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
        graphLayout,
        isLoading,
        isRendering,
        selectedNodeID,
        selectNode,
        highlightNode,
        isolateGroup,
        toggleGroup,
        clearGroup,
        activeGroup,
        openedNodeID,
        handleNodeClick,
        handlers,
    } = useDependencies(graphRef, SUBTYPE, initialNodeID, route.params, props.fetchAssetDependencies, layoutMode, groupOf)

    const router = useRouter()

    /** Double click opens the node's own page; single click only selects. */
    const openNode = (node: Node): void => {
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
        if (!id) return
        const element = getElements().find((el): el is {data: Node} => el.data.type === "NODE" && el.data.id === id)
        if (element) openNode(element.data)
    })

    /** One line answering "is anything wrong here" before the user reads a single node. */
    const summary = computed(() => {
        if (!props.dagView) return ""

        const assets = getElements()
            .filter((el): el is {data: Node} => el.data.type === "NODE" && el.data.metadata.subtype === ASSET)
            .map(({data}) => data.metadata as {status?: string; updated?: string})

        if (!assets.length) return ""

        const stale = assets.filter((asset) => asset.status === "stale").length
        const failed = assets.filter((asset) => asset.status === "failed").length
        const latest = assets.reduce<string | undefined>(
            (newest, asset) => (asset.updated && (!newest || asset.updated > newest) ? asset.updated : newest),
            undefined,
        )

        const fresh = assets.filter((asset) => asset.status === "fresh").length
        const never = assets.filter((asset) => asset.status === "never").length
        const untracked = assets.filter((asset) => !asset.status || asset.status === "unknown").length

        const parts = []
        if (failed) parts.push(t("dependency.dag.summary.failed", {n: failed}))
        if (stale) parts.push(t("dependency.dag.summary.issues", {n: stale}))
        if (never) parts.push(t("dependency.dag.summary.never", {n: never}))
        // "All" has to mean all of them, not just all of the ones we could judge.
        if (!parts.length && fresh === assets.length) parts.push(t("dependency.dag.summary.fresh", {n: fresh}))
        if (untracked) parts.push(t("dependency.dag.summary.unknown", {n: untracked}))
        if (latest) parts.push(t("dependency.dag.summary.last_run", {ago: moment(latest).fromNow()}))

        return parts.join(" · ")
    })

    /** DAG view swaps the table for the selected node's details, keeping the canvas in place. */
    const selectedNode = computed(() => {
        if (!props.dagView || !selectedNodeID.value) return undefined
        const element = getElements()
            .find((el): el is {data: Node} => el.data.type === "NODE" && el.data.id === selectedNodeID.value)
        return element?.data
    })

    const showExecutionChart = computed(() => SUBTYPE === FLOW || SUBTYPE === NAMESPACE)

    const chartDuration = ref("PT336H") // default: 14 days

    const chartRef = useTemplateRef<InstanceType<typeof TimeSeries>>("chartRef")

    const chartHasData = computed(() => (chartRef.value?.total ?? 0) > 0)

    watch(chartDuration, () => void chartRef.value?.refresh(), {flush: "post"})

    interface ChartDefinition {
        id: string;
        type: string;
        chartOptions: {
            displayName: string;
            description: string;
            legend: {enabled: boolean};
            column: string;
            colorByColumn: string;
            width: number;
        };
        data: {
            type: string;
            columns: {
                date: {field: string; displayName: string};
                state: {field: string};
                total: {displayName: string; agg: string; graphStyle: string};
                duration: {field: string; displayName: string; agg: string; graphStyle: string};
            };
            where: {field: string; type: string; value: string}[];
        };
        content?: string;
        [key: string]: unknown;
    }

    const chartDefinition = computed<ChartDefinition>(() => {
        const where = SUBTYPE === FLOW
            ? [
                {field: "NAMESPACE", type: "EQUAL_TO", value: String(route.params.namespace)},
                {field: "FLOW_ID", type: "EQUAL_TO", value: String(route.params.id)},
            ]
            : [
                {field: "NAMESPACE", type: "EQUAL_TO", value: String(route.params.id)},
            ]

        const definition: ChartDefinition = {
            id: "dependencies_executions_timeseries",
            type: "io.kestra.plugin.core.dashboard.chart.TimeSeries",
            chartOptions: {
                displayName: "Total Executions",
                description: "Executions duration and count per date",
                legend: {enabled: false},
                column: "date",
                colorByColumn: "state",
                width: 12,
            },
            data: {
                type: "io.kestra.plugin.core.dashboard.data.Executions",
                columns: {
                    date: {field: "START_DATE", displayName: "Date"},
                    state: {field: "STATE"},
                    total: {displayName: "Executions", agg: "COUNT", graphStyle: "BARS"},
                    duration: {field: "DURATION", displayName: "Duration", agg: "SUM", graphStyle: "LINES"},
                },
                where,
            },
        }

        definition.content = YAML_UTILS.stringify(definition)

        return definition
    })

    function chartFilters() {
        return [{
            field: "timeRange",
            value: chartDuration.value,
            operation: "EQUALS",
        } satisfies QueryFilter]
    }
</script>

<style scoped lang="scss">
.dependencies-wrapper {
    display: flex;
    flex-direction: column;
    width: 100%;
    height: 100%;
}

.chart-header {
    display: flex;
    flex-direction: column;
    flex-shrink: 0;
    gap: var(--ks-spacing-2);
    padding: var(--ks-spacing-2);
    padding-bottom: 0;
}

.dependencies {
    display: flex;
    width: 100%;
    flex: 1;
    min-height: 0;

    & div#graph {
        // The splitter panel itself stays static, so overlays anchor to .graph-pane.

        & .graph-pane {
            position: relative; // anchors the layout bar and the zoom controls
            height: 100%;
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

        & .dag-bar {
            position: absolute;
            top: var(--ks-spacing-2);
            left: var(--ks-spacing-2);
            right: var(--ks-spacing-2);
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: var(--ks-spacing-3);
            pointer-events: none; // the canvas keeps its own drag and zoom
        }

        & .layout-toggle {
            pointer-events: auto;
        }

        & .group-select {
            pointer-events: auto;
            width: 11rem;
        }

        & .layout-summary {
            color: var(--ks-text-secondary);
            text-align: right;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        & .group-chips {
            position: absolute;
            top: var(--ks-spacing-8);
            left: var(--ks-spacing-2);
            display: flex;
            flex-wrap: wrap;
            gap: var(--ks-spacing-2);
            pointer-events: auto;
        }

        & .group-chip {
            padding: var(--ks-spacing-1) var(--ks-spacing-3);
            border: 1px solid var(--ks-border-subtle);
            border-radius: var(--ks-radius-base);
            background: var(--ks-bg-surface);
            color: var(--ks-text-secondary);
            font-size: var(--ks-font-size-xs);
            cursor: pointer;
        }

        & .group-chip:hover {
            color: var(--ks-text-primary);
            border-color: var(--ks-border-default);
        }

        & .group-chip.is-active {
            border-color: var(--ks-border-focus);
            color: var(--ks-text-primary);
        }

        & .dag-legend {
            position: absolute;
            bottom: var(--ks-spacing-3);
            left: 50%;
            transform: translateX(-50%);
            display: flex;
            flex-wrap: wrap;
            justify-content: center;
            gap: var(--ks-spacing-1) var(--ks-spacing-4);
            padding: var(--ks-spacing-2) var(--ks-spacing-4);
            border: 1px solid var(--ks-border-subtle);
            border-radius: var(--ks-radius-base);
            background: var(--ks-bg-surface);
        }

        & .legend-item {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-2);
        }

        & .legend-swatch {
            width: 0.625rem;
            height: 0.625rem;
            border-radius: var(--ks-radius-xs);
        }

        & .legend-swatch.status-fresh {
            background: var(--ks-status-success);
        }

        & .legend-swatch.status-stale {
            background: var(--ks-status-warning);
        }

        & .legend-swatch.status-failed {
            background: var(--ks-status-error);
        }

        & .legend-swatch.status-never,
        & .legend-swatch.status-unknown {
            background: var(--ks-status-neutral);
        }

        & .controls {
            position: absolute;
            bottom: 16px;
            left: 10px;
            display: flex;
            flex-direction: column;
            justify-content: flex-end;
            gap: 0.25rem;

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

<template>
    <div class="dependencies-wrapper">
        <div v-if="showExecutionChart" class="chart-header">
            <ChartDurationSelect v-model="chartDuration" />
            <TimeSeries
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
                <KsGraph
                    ref="graphRef"
                    class="graph-canvas"
                    :nodes="chartNodes"
                    :edges="chartEdges"
                    :loading="isRendering"
                    :options="{series: [{emphasis: {focus: 'none'}}]}"
                    @node-click="handleNodeClick"
                />

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
            </KsSplitterPanel>

            <KsSplitterPanel id="table">
                <Table
                    :elements="getElements()"
                    :highlightShown="handlers.highlightShown"
                    :selected="selectedNodeID"
                    :subtype="SUBTYPE"
                    @select="selectNode"
                />
            </KsSplitterPanel>
        </KsSplitter>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue"

    import Table from "./components/Table.vue"
    import Empty from "../layout/empty/Empty.vue"
    import TimeSeries from "../dashboard/sections/TimeSeries.vue"
    import ChartDurationSelect from "../executions/date-select/ChartDurationSelect.vue"
    import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"

    import {KsGraph} from "@kestra-io/design-system"
    import {QueryFilter} from "@kestra-io/kestra-sdk"

    import {useDependencies} from "./composables/useDependencies"
    import {FLOW, EXECUTION, NAMESPACE, ASSET} from "./utils/types"
    import type {Types} from "./utils/types"

    const PANEL = {size: "70%", min: "30%", max: "80%"}

    import {useRoute} from "vue-router"
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
    }>()

    const SUBTYPE: Types = route.name === "flows/update" ? FLOW : route.name === "namespaces/update" ? NAMESPACE : route.name === "assets/update" ? ASSET : EXECUTION

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
        handleNodeClick,
        handlers,
    } = useDependencies(graphRef, SUBTYPE, initialNodeID, route.params, props.fetchAssetDependencies)

    const showExecutionChart = computed(() => SUBTYPE === FLOW || SUBTYPE === NAMESPACE)

    const chartDuration = ref("PT336H") // default: 14 days

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
                total: {displayName: string; agg: string};
                duration: {field: string; displayName: string; agg: string};
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
                    total: {displayName: "Executions", agg: "COUNT"},
                    duration: {field: "DURATION", displayName: "Duration", agg: "SUM"},
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
    gap: var(--ks-spacing-2);
}

.chart-header {
    display: flex;
    flex-direction: column;
    flex-shrink: 0;
    gap: var(--ks-spacing-2);
    padding: var(--ks-spacing-2) 0;
}

.dependencies {
    display: flex;
    width: 100%;
    flex: 1;
    min-height: 0;

    & div#graph {
        position: relative; // for absolute positioning of controls

        & .graph-canvas {
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

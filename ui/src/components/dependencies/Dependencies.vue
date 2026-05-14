<template>
    <div v-if="!TESTING && isLoading" v-ks-loading="true" class="h-100" />
    <Empty v-else-if="!TESTING && !getElements().length" :type="`dependencies.${SUBTYPE}`" />
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
</template>

<script setup lang="ts">
    import {ref} from "vue"

    import Table from "./components/Table.vue"
    import Empty from "../layout/empty/Empty.vue"

    import {KsGraph} from "@kestra-io/design-system"

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
    const TESTING = false // When true, bypasses API data fetching and uses mock/test data.

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
    } = useDependencies(graphRef, SUBTYPE, initialNodeID, route.params, TESTING, props.fetchAssetDependencies)
</script>

<style scoped lang="scss">
.dependencies {
    display: flex;
    width: 100%;
    height: calc(100vh - 145px);

    & div#graph {
        position: relative; // for absolute positioning of controls

        & .graph-canvas {
            height: 100%;
            overflow: hidden;
            background-color: transparent;
            background-image: radial-gradient(circle, var(--ks-dots-topology) 1px, transparent 1px);
            background-repeat: repeat;
            background-size: 24px 24px;
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

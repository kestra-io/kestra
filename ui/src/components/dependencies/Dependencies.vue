<template>
    <div v-if="isLoading" v-ks-loading="true" class="h-100" />

    <!-- FIX #2: Use reactive `elements` computed ref — not getElements() method -->
    <Empty v-else-if="!elements.length" :type="`dependencies.${SUBTYPE}`" />

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
                <KsButton size="small" :title="$t('dependency.controls.zoom_in')" @click="handlers.zoomIn">
                    <Plus />
                </KsButton>
                <KsButton size="small" :title="$t('dependency.controls.zoom_out')" @click="handlers.zoomOut">
                    <Minus />
                </KsButton>
                <KsButton size="small" :title="$t('dependency.controls.clear_selection')" @click="handlers.clearSelection">
                    <SelectionRemove />
                </KsButton>
                <KsButton size="small" :title="$t('dependency.controls.fit_view')" @click="handlers.fit">
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
            <!-- FIX #2: reactive `elements` ref — consistent, cached, no re-computation -->
            <Table
                :elements="elements"
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

    // FIX #3: Added ALL missing Ks* component imports
    // Only KsGraph was imported before — the rest were silently unresolved
    import {
        KsGraph,
        KsSplitter,
        KsSplitterPanel,
        KsButton,
        KsDropdown,
        KsDropdownMenu,
        KsDropdownItem,
    } from "@kestra-io/design-system"

    // FIX #6: REMOVED unused `QueryFilter` import
    // import {QueryFilter} from "@kestra-io/kestra-sdk"

    import {useDependencies} from "./composables/useDependencies"
    import {FLOW, EXECUTION, NAMESPACE, ASSET} from "./utils/types"
    import type {Types} from "./utils/types"

    const PANEL = {size: "70%", min: "30%", max: "80%"}

    import {useRoute} from "vue-router"
    import {routeFamily} from "../../utils/routeFamily"
    const route = useRoute()

    import Plus from "vue-material-design-icons/Plus.vue"
    import Minus from "vue-material-design-icons/Minus.vue"
    import SelectionRemove from "vue-material-design-icons/SelectionRemove.vue"
    import FitToScreenOutline from "vue-material-design-icons/FitToScreenOutline.vue"
    import Download from "vue-material-design-icons/Download.vue"

    // FIX #7: NOTE — ideally move `use([TitleComponent])` to main.ts to run only once.
    // Calling it here runs on every component mount (harmless but anti-pattern).
    import {use} from "echarts/core"
    import {TitleComponent} from "echarts/components"
    use([TitleComponent])

    const props = defineProps<{
        fetchAssetDependencies?: () => Promise<{
            data: any[];
            count: number;
        }>;
    }>()

    const SUBTYPE: Types = ((): Types => {
        switch (routeFamily(route.name)) {
        case "flows/update":      return FLOW
        case "namespaces/update": return NAMESPACE
        case "assets/update":     return ASSET
        default:                  return EXECUTION
        }
    })()

    const graphRef = ref(null)

    // FIX #5: Explicit per-subtype switch for correct ID resolution
    // BUG was: `route.params.id || route.params.assetId`
    // `||` picks `route.params.id` from a parent layout route, shadowing assetId
    // causing WRONG dependencies to be fetched for ASSET subtype
    const initialNodeID: string = (() => {
        switch (SUBTYPE) {
        case FLOW:      return String(route.params.id)
        case NAMESPACE: return String(route.params.id)
        case ASSET:     return String(route.params.assetId)
        default:        return String(route.params.flowId)  // EXECUTION
        }
    })()

    // FIX #4: Guard fetchAssetDependencies — only passed when actually needed
    // Prevents the composable from receiving/calling an undefined function
    const fetchFn = SUBTYPE === ASSET ? props.fetchAssetDependencies : undefined

    // FIX #2: Destructure `elements` as computed ref (not `getElements` method)
    // useDependencies must expose: elements = computed(() => rawData.value)
    const {
        elements,        // computed<T[]> — replaces unsafe getElements() calls
        chartNodes,
        chartEdges,
        isLoading,
        isRendering,
        selectedNodeID,
        selectNode,
        handleNodeClick,
        handlers,
    } = useDependencies(graphRef, SUBTYPE, initialNodeID, route.params, fetchFn)
</script>

<style scoped lang="scss">
.dependencies {
    display: flex;
    width: 100%;
    flex: 1;
    min-height: 0;

    & div#graph {
        position: relative;

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
<template>
    <Empty v-if="!loading && !getElements().length" type="dependencies" />
    <el-splitter v-else class="dependencies">
        <el-splitter-panel id="graph" v-bind="PANEL">
            <div v-loading="loading" ref="container" />

            <div class="controls">
                <el-button size="small" :title="t('dependency.controls.zoom_in')" @click="handlers.zoomIn">
                    <Plus />
                </el-button>
                <el-button size="small" :title="t('dependency.controls.zoom_out')" @click="handlers.zoomOut">
                    <Minus />
                </el-button>
                <el-button size="small" :title="t('dependency.controls.clear_selection')" @click="handlers.clearSelection">
                    <SelectionRemove />
                </el-button>
                <el-button size="small" :title="t('dependency.controls.fit_view')" @click="handlers.fit">
                    <FitToScreenOutline />
                </el-button>
            </div>
        </el-splitter-panel>

        <el-splitter-panel id="table">
            <Table :elements="getElements()" @select="selectNode" :selected="selectedNodeID" />
        </el-splitter-panel>
    </el-splitter>
</template>

<script setup lang="ts">
    import {ref} from "vue";

    import Table from "./components/Table.vue";
    import Empty from "../layout/empty/Empty.vue";

    import {useDependencies} from "./composables/useDependencies";
    import {FLOW, EXECUTION} from "./utils/types";

    const PANEL = {size: "70%", min: "30%", max: "80%"};

    import {useRoute} from "vue-router";
    const route = useRoute();

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import Plus from "vue-material-design-icons/Plus.vue";
    import Minus from "vue-material-design-icons/Minus.vue";
    import SelectionRemove from "vue-material-design-icons/SelectionRemove.vue";
    import FitToScreenOutline from "vue-material-design-icons/FitToScreenOutline.vue";

    const SUBTYPE = route.name === "flows/update" ? FLOW : EXECUTION;

    const container = ref(null);
    const initialNodeID: string = SUBTYPE === FLOW ? String(route.params.id) : String(route.params.flowId);

    const TESTING = false; // When true, bypasses API data fetching and uses mock/test data.

    const {getElements, loading, selectedNodeID, selectNode, handlers} = useDependencies(container, SUBTYPE, initialNodeID, route.params, TESTING);
</script>

<style scoped lang="scss">
.dependencies {
    display: flex;
    width: 100%;
    height: calc(100vh - 135px);

    & div#graph {
        position: relative; // for absolute positioning of controls

        & > div:not(.controls) {
            height: 100%;
            overflow: hidden scroll;
            background-color: transparent;
            background-image: radial-gradient(circle, var(--ks-dots-topology) 1px, transparent 1px);
            background-repeat: repeat;
            background-size: 24px 24px;
        }

        & .controls {
            position: absolute;
            bottom: 10px;
            left: 10px;
            display: flex;
            flex-direction: column;
            justify-content: flex-end;
            gap: 0.25rem;

            & > button {
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
    }
}
</style>

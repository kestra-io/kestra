<template>
    <el-splitter class="default-theme" v-bind="$attrs" @resize-end="onResize">
        <el-splitter-panel
            min="10%"
            key="sideBar"
            :size="sideBarSize"
        >
            <FileExplorer
                :currentNS="namespace"
                style="width: 100%;height: 100%;"
            />
        </el-splitter-panel>
        <el-splitter-panel
            min="20%"
            key="editor"
            :size="editorSize"
        >
            <MultiPanelTabs v-if="mounted" v-model="panels" />
        </el-splitter-panel>
    </el-splitter>
</template>

<script setup lang="ts">
    import {computed, watch} from "vue";
    import {useMounted, useStorage} from "@vueuse/core";
    import FileExplorer from "../../inputs/FileExplorer.vue";
    import MultiPanelTabs from "../../MultiPanelTabs.vue";
    import {CODE_PREFIX, getTabFromFilesTab, getTabPropsFromFilePath, useFilesPanels} from "../../flows/useFilesPanels";
    import {useFlowStore} from "../../../stores/flow";
    import {useStoredPanels} from "../../../composables/useStoredPanels";

    const mounted = useMounted()

    const props = defineProps<{
        namespace: string
    }>();

    const flowStore = useFlowStore();

    watch(() => props.namespace, (newVal) => {
        flowStore.flow = {
            namespace: newVal,
            id: "",
            revision: 0,
            source: `namespace: ${newVal}\n`,
            errors: []
        }
    }, {immediate: true})

    const sideBarSize = useStorage("namespace-files-editor-view-sidebar-size", 1)
    const editorSize = useStorage("namespace-files-editor-view-editor-size", 4)

    function onResize(_index: number, sizes: number[]) {
        sideBarSize.value = sizes[0]
        editorSize.value = sizes[1]
    }

    const {panels} = useStoredPanels(
        `namespace-files-editor-view-panels-${props.namespace}`,
        [{
            deserialize: (value: string) => {
                if(value.startsWith(`${CODE_PREFIX}-`)){
                    value = value.slice(5)
                } else {
                    // not a file tab
                    return
                }
                const tabProps = getTabPropsFromFilePath(value, false);
                if(!tabProps) return

                return getTabFromFilesTab(tabProps)
            }
        }]

    );

    useFilesPanels(panels, computed(() => props.namespace))
</script>
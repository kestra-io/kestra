<template>
    <el-splitter class="default-theme" v-bind="$attrs" @resize-end="onResize">
        <el-splitter-panel
            min="10%"
            key="sideBar"
            :size="sideBarSize"
        >
            <EditorSidebar :currentNS="namespace" style="width: 100%;height: 100%;" />
        </el-splitter-panel>
        <el-splitter-panel
            min="20%"
            key="editor"
            :size="editorSize"
        >
            <MultiPanelTabs v-if="mounted" v-model="panels" @remove-tab="onRemoveTab" />
        </el-splitter-panel>
    </el-splitter>
</template>

<script setup lang="ts">
    import {watch} from "vue";
    import {useMounted, useStorage} from "@vueuse/core";
    // @ts-expect-error no types on editor sidebar
    import EditorSidebar from "../../inputs/EditorSidebar.vue";
    import MultiPanelTabs from "../../MultiPanelTabs.vue";
    import {getTabFromFilesTab, getTabPropsFromFilePath, useFilesPanels} from "../../flows/useFilesPanels";
    import {useFlowStore} from "../../../stores/flow";
    import {useEditorStore} from "../../../stores/editor";
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

    const editorStore = useEditorStore();

    const panels = useStoredPanels(
        `namespace-files-editor-view-panels-${props.namespace}`,
        [{
            deserialize: (value: string) => {
                if(value.startsWith("code-")){
                    value = value.slice(5)
                } else {
                    // not a file tab
                    return
                }
                const tabProps = getTabPropsFromFilePath(value, false);
                editorStore.openTab(tabProps)
                if(!tabProps) return

                return getTabFromFilesTab(tabProps)
            }
        }]

    );

    const {onRemoveTab} = useFilesPanels(panels, true);
</script>
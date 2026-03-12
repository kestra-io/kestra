<template>
    <div class="main-editor">
        <MultiPanelEditorTabs :tabs="editorElements" @update:tabs="setTabValue" :openTabs="openTabs">
            <slot name="actions" />
        </MultiPanelEditorTabs>
        <div class="editor-wrapper">
            <el-splitter class="default-theme editor-panels" layout="vertical">
                <el-splitter-panel>
                    <MultiPanelTabs v-model="panels" @remove-tab="onRemoveTab" />
                </el-splitter-panel>
                <el-splitter-panel v-if="bottomVisible && slots['bottom-panel']">
                    <slot name="bottom-panel" />
                </el-splitter-panel>
            </el-splitter>
        </div>
        <slot name="footer" />
    </div>
</template>

<script lang="ts" setup>
    import {computed, useSlots} from "vue";
    import MultiPanelEditorTabs from "./MultiPanelEditorTabs.vue";
    import MultiPanelTabs from "./MultiPanelTabs.vue";
    import {EditorElement, Panel} from "../utils/multiPanelTypes";
    import {useStoredPanels} from "../composables/useStoredPanels";

    const props = withDefaults(defineProps<{
        editorElements: EditorElement[];
        defaultActiveTabs: string[];
        saveKey?: string;
        bottomVisible?: boolean;
        preSerializePanels?: (panels: Panel[]) => any;
    }>(), {
        bottomVisible: false,
        preSerializePanels: undefined,
        saveKey: undefined,
    });

    const slots = useSlots();

    const defaultPanelSize = computed(() => panels.value.length ? panels.value.reduce((acc, panel) => acc + panel.size, 0) / panels.value.length : 1);

    function focusTab(tabValue: string){
        for(const panel of panels.value){
            const t = panel.tabs.find(e => e.uid === tabValue);
            if(t) panel.activeTab = t;
        }
    }

    function getPanelFromValue(value: string): {panel: Panel, prepend: boolean} | undefined {
        for (const element of props.editorElements) {
            const deserializedTab = element.deserialize(value, true);
            if (deserializedTab) {
                return {
                    panel: {
                        activeTab: deserializedTab,
                        tabs: [deserializedTab],
                        size: defaultPanelSize.value,
                    },
                    prepend: element.prepend ?? false
                };
            }
        }
    };

    const {panels, saveState} = useStoredPanels(
        props.saveKey, 
        props.editorElements, 
        props.defaultActiveTabs, 
        props.preSerializePanels,
    );

    const emit = defineEmits<{
        (e: "set-tab-value", tabValue: string): void | false;
        (e: "remove-tab", tabValue: string): void;
    }>();

    function setTabValue(tabValue: string){
        if(emit("set-tab-value", tabValue) === false) {
            return;
        }

        if(openTabs.value.includes(tabValue)){
            onRemoveTab(tabValue);
            return;
        }

        const panel = getPanelFromValue(tabValue);
        if(panel){
            if(panel.prepend){
                panels.value.unshift(panel.panel);
            } else {
                panels.value.push(panel.panel);
            }
        }
    }



    const openTabs = computed(() => panels.value.flatMap(p => p.tabs.map(t => t.uid)));

    function onRemoveTab(tabValue: string) {
        const panel = panels.value.find(p => p.tabs.some(t => t.uid === tabValue))
        if (panel) {
            panel.tabs = panel.tabs.filter(t => t.uid !== tabValue)
            if (panel.activeTab.uid === tabValue) {
                panel.activeTab = panel.tabs[0]
            }
        }
        emit("remove-tab", tabValue);
    }

    defineExpose({
        panels,
        openTabs,
        focusTab,
        setTabValue,
        saveState,
    });
</script>

<style lang="scss" scoped>
    .main-editor{
        display: grid;
        grid-template-rows: auto 1fr;
        height: 100%;

        .editor-wrapper {
            position: relative;
            height: 100%;
        }
    }

    :deep(.editor-panels){
        position: absolute;
    }
    :deep(.el-splitter-bar){
        width: 2px !important;
    }

    .default-theme{
        :deep(.el-splitter-panel) {
            background-color: var(--ks-background-panel);
        }

        :deep(.el-splitter__splitter){
            border-top-color: var(--ks-border-primary);
            background-color: var(--ks-background-panel);
            &:before, &:after{
                background-color: var(--ks-content-secondary);
            }
        }
    }
</style>
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
    import {useStorage} from "@vueuse/core";
    import MultiPanelEditorTabs from "./MultiPanelEditorTabs.vue";
    import MultiPanelTabs from "./MultiPanelTabs.vue";
    import {DeserializableEditorElement, Panel, Tab} from "../utils/multiPanelTypes";

    const props = withDefaults(defineProps<{
        editorElements: DeserializableEditorElement[];
        defaultActiveTabs: string[];
        saveKey: string;
        bottomVisible?: boolean;
        preSerializePanels?: (panels: Panel[]) => any;
    }>(), {
        bottomVisible: false,
        preSerializePanels: (ps: Panel[]) => ps.map(p => ({
            tabs: p.tabs.map(t => t.value),
            activeTab: p.activeTab?.value,
            size: p.size,
        }))
    });

    const slots = useSlots();

    const defaultPanelSize = computed(() => panels.value.length ? panels.value.reduce((acc, panel) => acc + panel.size, 0) / panels.value.length : 1);

    function focusTab(tabValue: string){
        for(const panel of panels.value){
            const t = panel.tabs.find(e => e.value === tabValue);
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

    /**
     * function called on mount to deserialize tabs from storage
     * NOTE: if a tab is not relevant anymore, it will be ignored
     * hence the "allowCreate = false".
     * @param tags
     */
    function deserializeTabTags(tags: string[]): Tab[] {
        return tags.map(tag => {
            for (const element of props.editorElements) {
                const deserializedTab = element.deserialize(tag, false);
                if (deserializedTab) {
                    return deserializedTab;
                }
            }
        }).filter(t => t !== undefined) as Tab[];
    }

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

    const panels = useStorage<Panel[]>(
        props.saveKey,
        deserializeTabTags(props.defaultActiveTabs).map((t) => {
            return {
                activeTab: t,
                tabs: [t],
                size: 100 / props.defaultActiveTabs.length
            };
        }),
        undefined,
        {
            serializer: {
                write(v: Panel[]){
                    return JSON.stringify(props.preSerializePanels(v));
                },
                read(v?: string) {
                    if(!v) return null;
                    const panels = JSON.parse(v);
                    return panels
                        .filter((p: any) => p.tabs.length)
                        .map((p: {tabs: string[], activeTab: string, size: number}):Panel => {
                            const tabs = deserializeTabTags(p.tabs);
                            const activeTab = tabs.find((t: any) => t.value === p.activeTab) ?? tabs[0];
                            return {
                                activeTab,
                                tabs,
                                size: p.size
                            };
                        });
                }
            },
        }
    );

    const openTabs = computed(() => panels.value.flatMap(p => p.tabs.map(t => t.value)));

    function onRemoveTab(tabValue: string) {
        const panel = panels.value.find(p => p.tabs.some(t => t.value === tabValue))
        if (panel) {
            panel.tabs = panel.tabs.filter(t => t.value !== tabValue)
            if (panel.activeTab.value === tabValue) {
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
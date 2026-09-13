<template>
    <div class="main-editor">
        <MultiPanelEditorTabs :tabs="editorElements" @update:tabs="setTabValue" :openTabs="openTabs">
            <div class="tabs-actions">
                <KsButton
                    v-if="bottomVisible && slots['bottom-panel']"
                    :icon="splitOrientation === 'vertical' ? ViewSplitVertical : ViewSplitHorizontal"
                    :tooltip="splitOrientation === 'vertical' ? $t('split_horizontal') : $t('split_vertical')"
                    class="orientation-toggle"
                    @click="toggleOrientation"
                />
                <slot name="actions" />
            </div>
        </MultiPanelEditorTabs>
        <div class="editor-wrapper">
            <KsSplitter class="default-theme editor-panels" :layout="splitOrientation">
                <KsSplitterPanel min="100">
                    <MultiPanelTabs v-model="panels" @remove-tab="onRemoveTab" />
                </KsSplitterPanel>
                <KsSplitterPanel v-if="bottomVisible && slots['bottom-panel']" size="30%" min="100">
                    <slot name="bottom-panel" />
                </KsSplitterPanel>
            </KsSplitter>
        </div>
        <slot name="footer" />
    </div>
</template>

<script lang="ts" setup>
    import {computed, useSlots} from "vue"
    import {useStorage} from "@vueuse/core"
    import ViewSplitVertical from "vue-material-design-icons/ViewSplitVertical.vue"
    import ViewSplitHorizontal from "vue-material-design-icons/ViewSplitHorizontal.vue"
    import MultiPanelEditorTabs from "./MultiPanelEditorTabs.vue"
    import MultiPanelTabs from "./MultiPanelTabs.vue"
    import {EditorElement, Panel} from "../utils/multiPanelTypes"
    import {useStoredPanels} from "../composables/useStoredPanels"

    const splitOrientation = useStorage<"vertical" | "horizontal">("editor-split-orientation", "vertical")

    function toggleOrientation() {
        splitOrientation.value = splitOrientation.value === "vertical" ? "horizontal" : "vertical"
    }

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
    })

    const slots = useSlots()

    const defaultPanelSize = computed(() => panels.value.length ? panels.value.reduce((acc, panel) => acc + panel.size, 0) / panels.value.length : 1)

    function focusTab(tabValue: string){
        for(const panel of panels.value){
            const t = panel.tabs.find(e => e.uid === tabValue)
            if(t) panel.activeTab = t
        }
    }

    function getPanelFromValue(value: string): {panel: Panel, prepend: boolean, preferredSize?: number} | undefined {
        for (const element of props.editorElements) {
            const deserializedTab = element.deserialize(value, true)
            if (deserializedTab) {
                return {
                    panel: {
                        activeTab: deserializedTab,
                        tabs: [deserializedTab],
                        size: element.preferredSize ?? defaultPanelSize.value,
                    },
                    prepend: element.prepend ?? false,
                    preferredSize: element.preferredSize,
                }
            }
        }
    };

    // Panel sizes are shares the splitter normalizes, so inserting a 25 next to two 50s yields 20.
    // The existing panels give up exactly the requested share, keeping their ratio to each other.
    function makeRoomFor(preferredSize: number) {
        const currentTotal = panels.value.reduce((acc, p) => acc + p.size, 0)
        if (currentTotal <= 0) return
        const remaining = 100 - preferredSize
        panels.value.forEach(p => {
            p.size = (p.size / currentTotal) * remaining
        })
    }

    const {panels, saveState} = useStoredPanels(
        props.saveKey,
        props.editorElements,
        props.defaultActiveTabs,
        props.preSerializePanels,
    )

    const emit = defineEmits<{
        (e: "set-tab-value", tabValue: string): void | false;
        (e: "remove-tab", tabValue: string): void;
    }>()

    function setTabValue(tabValue: string){
        if(props.editorElements.find(e => e.uid === tabValue)?.button.disabled){
            return
        }

        if(emit("set-tab-value", tabValue) === false) {
            return
        }

        if(openTabs.value.includes(tabValue)){
            onRemoveTab(tabValue)
            return
        }

        const panel = getPanelFromValue(tabValue)
        if(panel){
            if(panel.preferredSize !== undefined){
                makeRoomFor(panel.preferredSize)
            }
            if(panel.prepend){
                panels.value.unshift(panel.panel)
            } else {
                panels.value.push(panel.panel)
            }
        }
    }

    const openTabs = computed(() => panels.value.flatMap(p => p.tabs.map(t => t.uid)))

    function onRemoveTab(tabValue: string) {
        const panel = panels.value.find(p => p.tabs.some(t => t.uid === tabValue))
        if (panel) {
            panel.tabs = panel.tabs.filter(t => t.uid !== tabValue)
            if (panel.activeTab.uid === tabValue) {
                panel.activeTab = panel.tabs[0]
            }
        }
        emit("remove-tab", tabValue)
    }

    defineExpose({
        panels,
        openTabs,
        focusTab,
        setTabValue,
        saveState,
        splitOrientation,
    })
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

    .tabs-actions {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        padding: var(--ks-spacing-2) var(--ks-spacing-4);
        flex-shrink: 0;
    }

    :deep(.editor-panels){
        position: absolute;
    }

    .default-theme{
        :deep(.kel-splitter__horizontal > .kel-splitter-bar){
            width: 2px !important;
        }

        :deep(.kel-splitter__vertical > .kel-splitter-bar){
            height: 4px !important;
            width: 100% !important;
            cursor: ns-resize;
        }

        :deep(.kel-splitter-panel) {
            background-color: var(--ks-bg-surface);
        }

        :deep(.kel-splitter__splitter){
            border-top-color: var(--ks-border-default);
            background-color: var(--ks-bg-surface);
            &:before, &:after{
                background-color: var(--ks-text-secondary);
            }
        }
    }
</style>

<template>
    <div class="multi-panel-editor-wrapper">
        <MultiPanelEditorTabs :class="{playgroundMode}" :tabs="EDITOR_ELEMENTS" @update:tabs="setTabValue" :open-tabs="openTabs">
            <EditorButtonsWrapper />
        </MultiPanelEditorTabs>
        <div class="editor-wrapper">
            <el-splitter class="default-theme editor-panels" layout="vertical">
                <el-splitter-panel>
                    <MultiPanelTabs v-model="panels" @remove-tab="onRemoveTab" />
                </el-splitter-panel>
                <el-splitter-panel v-if="playgroundMode">
                    <FlowPlayground />
                </el-splitter-panel>
            </el-splitter>
        </div>
        <KeyShortcuts />
    </div>
</template>

<script setup lang="ts">
    import {computed, markRaw, onMounted, onUnmounted, ref, watch} from "vue";
    import {useStorage} from "@vueuse/core";
    import {useI18n} from "vue-i18n";
    import {useCoreStore} from "../../stores/core";
    import {usePlaygroundStore} from "../../stores/playground";
    import {useEditorStore} from "../../stores/editor";

    import MultiPanelTabs, {Panel, Tab} from "../MultiPanelTabs.vue";
    import MultiPanelEditorTabs from "../MultiPanelEditorTabs.vue";
    import FlowPlayground from "./FlowPlayground.vue";
    import EditorButtonsWrapper from "../inputs/EditorButtonsWrapper.vue";
    import KeyShortcuts from "../inputs/KeyShortcuts.vue";
    import NoCode from "../code/NoCode.vue";
    import {DEFAULT_ACTIVE_TABS, EDITOR_ELEMENTS} from "override/components/flows/panelDefinition";
    import {useCodePanels, useInitialCodeTabs} from "./useCodePanels";
    import {useTopologyPanels} from "./useTopologyPanels";
    import {useKeyShortcuts} from "../../utils/useKeyShortcuts";

    import {setupInitialNoCodeTab, setupInitialNoCodeTabIfExists, useNoCodeHandlers, useNoCodePanels} from "./useNoCodePanels";
    import {useFlowStore} from "../../stores/flow";
    import {trackTabOpen} from "../../utils/tabTracking";

    function isTabFlowRelated(element: Tab){
        return ["code", "nocode", "topology"].includes(element.value)
            // when the flow file is dirty all the nocode tabs get splashed
            || element.value.startsWith("nocode-")
    }

    const RawNoCode = markRaw(NoCode)

    const coreStore = useCoreStore()
    const flowStore = useFlowStore()
    const {showKeyShortcuts} = useKeyShortcuts()

    onMounted(() => {
        useEditorStore().explorerVisible = false
    })

    const playgroundStore = usePlaygroundStore()

    const playgroundMode = computed(() => playgroundStore.enabled)

    onUnmounted(() => {
        playgroundStore.enabled = false
        playgroundStore.clearExecutions()
    })

    /**
     * Focus or activate a tab from it's value
     * @param tabValue
     */
    function focusTab(tabValue: string){
        for(const panel of panels.value){
            const t = panel.tabs.find(e => e.value === tabValue)
            if(t) panel.activeTab = t
        }
    }

    const openTabs = ref<string[]>([])

    function setTabValue(tabValue: string){
        // Show dialog instead of creating panel
        if(tabValue === "keyshortcuts"){
            showKeyShortcuts();
            return;
        }

        if(openTabs.value.includes(tabValue)){
            focusTab(tabValue)
            return
        }
        const {prepend, panel} = getPanelFromValue(tabValue)
        
        trackTabOpen(panel.activeTab);
        
        if(prepend){
            panels.value.unshift(panel)
        }else{
            panels.value.push(panel)
        }
    }

    const {t} = useI18n()


    function getPanelFromValue(value: string, dirtyFlow = false): {prepend: boolean, panel: Panel}{
        const tab = setupInitialNoCodeTab(RawNoCode, value, t, noCodeHandlers, flowStore.flowYaml ?? "")
        return staticGetPanelFromValue(value, tab, dirtyFlow)
    }

    function staticGetPanelFromValue(value: string, tab?: Tab, dirtyFlow = false): {prepend: boolean, panel: Panel}{
        const element: Tab = tab ?? EDITOR_ELEMENTS.find(e => e.value === value)!

        if(isTabFlowRelated(element)){
            element.dirty = dirtyFlow
        }
        return {
            prepend: "files" === value,
            panel:{
                activeTab: element,
                tabs: [element]
            }
        }
    }

    const {setupInitialCodeTab} = useInitialCodeTabs()

    const isTourRunning = computed(() => coreStore.guidedProperties?.tourStarted)
    const DEFAULT_TOUR_TABS = [
        {tabs: ["code"], activeTab: "code", size: 1},
        {tabs: ["topology"], activeTab: "topology", size: 1}
    ];

    function cleanupNoCodeTabKey(key: string): string {
        // remove the number for "nocode-1234-" prefix from the key
        return /^nocode-\d{4}/.test(key) ? key.slice(0, 6) + key.slice(11) : key
    }

    function serializePanel(v:Panel[]){
        return v.map(p => ({
            tabs: p.tabs.map(t => t.value),
            activeTab: cleanupNoCodeTabKey(p.activeTab?.value),
            size: p.size,
        }))
    }

    /**
     * these actions are placeholders
     * that will be replaced later on
     */
    const tempActions = {
        openAddTaskTab(){},
        openEditTaskTab(){},
        closeTaskTab(){}
    } as ReturnType<typeof useNoCodePanels>

    const noCodeHandlers = useNoCodeHandlers(openTabs, focusTab, tempActions)

    const panels = useStorage<Panel[]>(
        `flow-${flowStore.flow?.namespace}-${flowStore.flow?.id}`,
        DEFAULT_ACTIVE_TABS
            .map((t) => staticGetPanelFromValue(t).panel),
        undefined,
        {
            serializer: {
                write(v: Panel[]){
                    return JSON.stringify(serializePanel(v))
                },
                read(v?: string) {
                    if (v) {
                        const panels: { tabs: string[], activeTab: string, size: number }[] = isTourRunning.value ? DEFAULT_TOUR_TABS : JSON.parse(v);
                        return panels
                            .filter((p) => p.tabs.length)
                            .map((p): Panel => {
                                const tabs: Tab[] = p.tabs.map((tab) =>
                                    setupInitialCodeTab(tab)
                                    ?? setupInitialNoCodeTabIfExists(RawNoCode, tab, t, noCodeHandlers, flowStore.flowYaml ?? "")
                                    ?? EDITOR_ELEMENTS.find(e => e.value === tab)!
                                )
                                    // filter out any tab that may have disappeared
                                    .filter(t => t !== undefined);
                                const activeTab = tabs.find(t => cleanupNoCodeTabKey(t.value) === p.activeTab) ?? tabs[0];
                                return {
                                    activeTab,
                                    tabs,
                                    size: p.size
                                };
                            });
                    } else {
                        return [];
                    }
                }
            },
        },
    )

    // we maintain openTabs using watcher to avoid circular references
    // The obvious choice would have been to have a computed,
    // but this would have required panels to be defined before openTabs.
    // We need openTabs for noCodeHandlers and the latter for panels
    // deserialization/initialization
    // openTabs -> noCodeHandlers -> panels -> openTabs
    watch(panels, (ps) => {
        openTabs.value = ps.flatMap(p => p.tabs.map(t => t.value))
    }, {deep: true, immediate: true})

    // Track initial tabs opened while editing or creating flow.
    let hasTrackedInitialTabs = false;
    watch(panels, (newPanels) => {
        if (!hasTrackedInitialTabs && newPanels && newPanels.length > 0) {
            hasTrackedInitialTabs = true;
            const allTabs = newPanels.flatMap(panel => panel.tabs);
            allTabs.forEach(tab => trackTabOpen(tab));
        }
    }, {immediate: true});

    const {onRemoveTab: onRemoveCodeTab, isFlowDirty} = useCodePanels(panels)

    const actions = useNoCodePanels(RawNoCode, panels, openTabs, focusTab)

    tempActions.openAddTaskTab = actions.openAddTaskTab
    tempActions.openEditTaskTab = actions.openEditTaskTab
    tempActions.closeTaskTab = actions.closeTaskTab

    function onRemoveTab(tab: string){
        onRemoveCodeTab(tab)
    }

    useTopologyPanels(panels, actions.openAddTaskTab, actions.openEditTaskTab)

    watch(isFlowDirty, (dirty) => {
        for(const panel of panels.value){
            if(panel.activeTab && isTabFlowRelated(panel.activeTab)){
                panel.activeTab.dirty = dirty
            }
            for(const tab of panel.tabs){
                if(isTabFlowRelated(tab)){
                    tab.dirty = dirty
                }
            }
        }
    })
</script>

<style lang="scss" scoped>
    @use "@kestra-io/ui-libs/src/scss/color-palette.scss" as colorPalette;
    .multi-panel-editor-wrapper{
        display: grid;
        grid-template-rows: auto 1fr;
        height: 100%;
    }

    .editor-wrapper{
        position: relative;
    }

    :deep(.editor-panels){
        position: absolute;
    }

    .playgroundMode {
        #{--el-color-primary}: colorPalette.$base-blue-500;
        color: colorPalette.$base-white;
        background-position: 10% 0;
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

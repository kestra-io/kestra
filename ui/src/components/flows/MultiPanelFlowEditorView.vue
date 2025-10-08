<template>
    <MultiPanelGenericEditorView
        ref="editorView"
        :class="{playgroundMode}"
        :editorElements="EDITOR_ELEMENTS"
        :defaultActiveTabs="TABS"
        :saveKey="`el-fl-${flowStore.flow?.namespace ?? `creation-${flowStore.creationId}`}${flowStore.flow?.id ? `-${flowStore.flow?.id}` : ''}`"
        :preSerializePanels="preSerializePanels"
        @set-tab-value="setTabValue"
        @remove-tab="onRemoveTab"
    >
        <template #actions>
            <EditorButtonsWrapper />
        </template>
        <template #bottom-panel>
            <FlowPlayground v-if="playgroundMode" />
        </template>
        <template #footer>
            <KeyShortcuts />
        </template>
    </MultiPanelGenericEditorView>
</template>

<script setup lang="ts">
    import {computed, markRaw, onMounted, onUnmounted, ref, watch} from "vue";
    import {useRoute} from "vue-router";
    import Utils from "../../utils/utils";
    import {useI18n} from "vue-i18n";
    import {useCoreStore} from "../../stores/core";
    import {usePlaygroundStore} from "../../stores/playground";
    import {useEditorStore} from "../../stores/editor";

    import FlowPlayground from "./FlowPlayground.vue";
    import EditorButtonsWrapper from "../inputs/EditorButtonsWrapper.vue";
    import KeyShortcuts from "../inputs/KeyShortcuts.vue";
    import NoCode from "../no-code/NoCode.vue";
    import {DEFAULT_ACTIVE_TABS, EDITOR_ELEMENTS} from "override/components/flows/panelDefinition";
    import {useFilesPanels, useInitialFilesTabs} from "./useFilesPanels";
    import {useTopologyPanels} from "./useTopologyPanels";
    import {useKeyShortcuts} from "../../utils/useKeyShortcuts";

    import {setupInitialNoCodeTab, setupInitialNoCodeTabIfExists, useNoCodeHandlers, useNoCodePanels} from "./useNoCodePanels";
    import {useFlowStore} from "../../stores/flow";
    import {trackTabOpen} from "../../utils/tabTracking";
    import {Panel, Tab} from "../../utils/multiPanelTypes";
    import MultiPanelGenericEditorView from "../MultiPanelGenericEditorView.vue";

    function isTabFlowRelated(element: Tab){
        return ["code", "nocode", "topology"].includes(element.value)
            // when the flow file is dirty all the nocode tabs get splashed
            || element.value.startsWith("nocode-")
    }

    const RawNoCode = markRaw(NoCode)

    const coreStore = useCoreStore()
    const flowStore = useFlowStore()
    const {showKeyShortcuts} = useKeyShortcuts()

    const route = useRoute();
    const editorView = ref<InstanceType<typeof MultiPanelGenericEditorView> | null>(null)

    onMounted(() => {
        useEditorStore().explorerVisible = false
        // Ensure the Flow Code panel is open and focused when arriving with ai=open
        if(route.query.ai === "open"){
            editorView.value?.setTabValue("code")
        }
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
        editorView.value?.setTabValue(tabValue)
    }

    function setTabValue(tabValue: string) {
        // Show dialog instead of creating panel
        if(tabValue === "keyshortcuts"){
            showKeyShortcuts();
            return false;
        }
    }

    const {t} = useI18n()

    const {setupInitialCodeTab} = useInitialFilesTabs()

    const codeElement = EDITOR_ELEMENTS.find(e => e.value === "code")!
    codeElement!.deserialize = (value: string) => setupInitialCodeTab(value, codeElement)


    const isTourRunning = computed(() => coreStore.guidedProperties?.tourStarted)
    const DEFAULT_TOUR_TABS = ["code", "topology"];

    function cleanupNoCodeTabKey(key: string): string {
        // remove the number for "nocode-1234-" prefix from the key
        return /^nocode-\d{4}/.test(key) ? key.slice(0, 6) + key.slice(11) : key
    }

    function preSerializePanels(v:Panel[]){
        return v.map(p => ({
            tabs: p.tabs.map(t => t.value),
            activeTab: cleanupNoCodeTabKey(p.activeTab?.value),
            size: p.size,
        }))
    }

    const openTabs = computed(() => {
        return editorView.value?.openTabs ?? []
    })



    const panels = computed<Panel[]>(() => editorView.value?.panels ?? [])

    // Track initial tabs opened while editing or creating flow.
    let hasTrackedInitialTabs = false;
    watch(panels, (newPanels) => {
        if (!hasTrackedInitialTabs && newPanels && newPanels.length > 0) {
            hasTrackedInitialTabs = true;
            const allTabs = newPanels.flatMap(panel => panel.tabs);
            allTabs.forEach(tab => trackTabOpen(tab));
        }
    }, {immediate: true});

    const {onRemoveTab: onRemoveCodeTab, isFlowDirty} = useFilesPanels(panels)

    const actions = useNoCodePanels(RawNoCode, panels, openTabs, focusTab)

    const noCodeHandlers = useNoCodeHandlers(openTabs, focusTab, actions)

    const noCodeElement = EDITOR_ELEMENTS.find(e => e.value === "nocode")!
    noCodeElement!.deserialize = (value: string, allowCreate: boolean) => {
        return allowCreate
            ? setupInitialNoCodeTab(RawNoCode, value, t, noCodeHandlers, flowStore.flowYaml ?? "")
            : setupInitialNoCodeTabIfExists(RawNoCode, value, t, noCodeHandlers, flowStore.flowYaml ?? "")
    }

    const TABS = isTourRunning.value ? DEFAULT_TOUR_TABS : DEFAULT_ACTIVE_TABS;

    flowStore.creationId = flowStore.creationId ?? Utils.uid()

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

<style scoped lang="scss">
    @use "@kestra-io/ui-libs/src/scss/color-palette.scss" as colorPalette;

    .playgroundMode :deep(.tabs-wrapper) {
        #{--el-color-primary}: colorPalette.$base-blue-500;
        color: colorPalette.$base-white;
        background-position: 10% 0;
    }
</style>

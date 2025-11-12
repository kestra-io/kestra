<template>
    <MultiPanelGenericEditorView
        ref="editorView"
        :class="{playgroundMode}"
        :editorElements="EDITOR_ELEMENTS"
        :defaultActiveTabs="TABS"
        :saveKey
        :preSerializePanels="preSerializePanels"
        :bottomVisible="playgroundMode"
        @set-tab-value="setTabValue"
    >
        <template #actions>
            <EditorButtonsWrapper :haveChange />
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
    import {useCoreStore} from "../../stores/core";
    import {usePlaygroundStore} from "../../stores/playground";

    import FlowPlayground from "./FlowPlayground.vue";
    import EditorButtonsWrapper from "../inputs/EditorButtonsWrapper.vue";
    import KeyShortcuts from "../inputs/KeyShortcuts.vue";
    import NoCode from "../no-code/NoCode.vue";
    import {DEFAULT_ACTIVE_TABS, EDITOR_ELEMENTS} from "override/components/flows/panelDefinition";
    import {useFilesPanels, useInitialFilesTabs} from "./useFilesPanels";
    import {useTopologyPanels} from "./useTopologyPanels";
    import {useKeyShortcuts} from "../../utils/useKeyShortcuts";

    import {useNoCodePanelsFull} from "./useNoCodePanels";
    import {useFlowStore} from "../../stores/flow";
    import {trackTabOpen} from "../../utils/tabTracking";
    import {Panel, Tab} from "../../utils/multiPanelTypes";
    import MultiPanelGenericEditorView from "../MultiPanelGenericEditorView.vue";

    function isTabFlowRelated(element: Tab){
        return ["code", "nocode", "topology"].includes(element.uid)
            // when the flow file is dirty all the nocode tabs get splashed
            || element.uid.startsWith("nocode-")
    }

    const RawNoCode = markRaw(NoCode)

    const coreStore = useCoreStore()
    const flowStore = useFlowStore()
    const {showKeyShortcuts} = useKeyShortcuts()

    const alwaysSaveKey = computed(() => `el-fl-${flowStore.flow?.namespace}-${flowStore.flow?.id}`);
    const saveKey = computed(() => flowStore.isCreating ? undefined : alwaysSaveKey.value);

    watch(() => flowStore.isCreating, (isCreating) => {
        if(!isCreating){
            // when switching from creating to editing, ensure the saveKey is updated
            editorView.value?.saveState(alwaysSaveKey.value);
        }
    })

    const route = useRoute();
    const editorView = ref<InstanceType<typeof MultiPanelGenericEditorView> | null>(null)

    onMounted(() => {
        // Ensure the Flow Code panel is open and focused when arriving with ai=open
        if(route.query.ai === "open"){
            if(!editorView.value?.openTabs.includes("code")) editorView.value?.setTabValue("code")
            else editorView.value?.focusTab("code")
        }
    })

    const playgroundStore = usePlaygroundStore()

    const playgroundMode = computed(() => playgroundStore.enabled)

    onUnmounted(() => {
        playgroundStore.enabled = false
        playgroundStore.clearExecutions()
    })

    function setTabValue(tabValue: string) {
        // Show dialog instead of creating panel
        if(tabValue === "keyshortcuts"){
            showKeyShortcuts();
            return false;
        }
    }

    useInitialFilesTabs(EDITOR_ELEMENTS)

    const isTourRunning = computed(() => coreStore.guidedProperties?.tourStarted)
    const DEFAULT_TOUR_TABS = ["code", "topology"];

    function cleanupNoCodeTabKey(key: string): string {
        // remove the number for "nocode-1234-" prefix from the key
        return /^nocode-\d{4}/.test(key) ? key.slice(0, 6) + key.slice(11) : key
    }

    function preSerializePanels(v:Panel[]){
        return v.map(p => ({
            tabs: p.tabs.map(t => t.uid),
            activeTab: cleanupNoCodeTabKey(p.activeTab?.uid),
            size: p.size,
        }))
    }

    const haveChange = computed(() => flowStore.haveChange || panels.value.some(panel =>
        panel.tabs.some(tab => tab.dirty)
    ))

    const {panels, actions} = useNoCodePanelsFull({
        RawNoCode,
        editorView,
        editorElements: EDITOR_ELEMENTS,
        source: computed(() => flowStore.flowYaml),
    });

    const TABS = isTourRunning.value ? DEFAULT_TOUR_TABS : DEFAULT_ACTIVE_TABS;

    flowStore.creationId = flowStore.creationId ?? Utils.uid()

    useFilesPanels(panels, computed(() => flowStore.flowParsed?.namespace))

    useTopologyPanels(panels, actions.openAddTaskTab, actions.openEditTaskTab)

    watch(() => flowStore.haveChange, (dirty) => {
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

    // Track initial tabs opened while editing or creating flow.
    let hasTrackedInitialTabs = false;
    watch(panels, (newPanels) => {
        if (!hasTrackedInitialTabs && newPanels && newPanels.length > 0) {
            hasTrackedInitialTabs = true;
            const allTabs = newPanels.flatMap(panel => panel.tabs);
            allTabs.forEach(tab => trackTabOpen(tab));
        }
    }, {immediate: true});
</script>

<style lang="scss" scoped>
    @use "@kestra-io/ui-libs/src/scss/color-palette.scss" as colorPalette;

    .playgroundMode :deep(.tabs-wrapper) {
        #{--el-color-primary}: colorPalette.$base-blue-500;
        color: colorPalette.$base-white;
        background-position: 10% 0;
    }
</style>

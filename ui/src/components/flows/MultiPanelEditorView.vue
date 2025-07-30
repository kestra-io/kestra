<template>
    <div class="multi-panel-editor-wrapper">
        <div class="tabs-wrapper" :class="{playgroundMode}">
            <div class="tabs">
                <button
                    v-for="element of EDITOR_ELEMENTS"
                    :key="element.value"
                    :class="{active: openTabs.includes(element.value)}"
                    @click="setTabValue(element.value)"
                >
                    <component class="tabs-icon" :is="element.button.icon" />
                    {{ element.button.label }}
                </button>
            </div>
            <EditorButtonsWrapper />
        </div>
        <div class="editor-wrapper">
            <Splitpanes class="default-theme editor-panels" horizontal>
                <Pane>
                    <MultiPanelTabs v-model="panels" @remove-tab="onRemoveTab" />
                </Pane>
                <Pane v-if="playgroundMode">
                    <FlowPlayground />
                </Pane>
            </Splitpanes>
        </div>
        <KeyShortcuts />
    </div>
</template>

<script setup lang="ts">
    import {computed, onMounted, onUnmounted, Ref, watch} from "vue";
    import {useStorage} from "@vueuse/core";
    import {useStore} from "vuex";
    import {useI18n} from "vue-i18n";
    import {Splitpanes, Pane} from "splitpanes"
    import {useCoreStore} from "../../stores/core";
    import {usePlaygroundStore} from "../../stores/playground";

    import MultiPanelTabs, {Panel, Tab} from "../MultiPanelTabs.vue";
    import FlowPlayground from "./FlowPlayground.vue";
    import EditorButtonsWrapper from "../inputs/EditorButtonsWrapper.vue";
    import KeyShortcuts from "../inputs/KeyShortcuts.vue";
    import {DEFAULT_ACTIVE_TABS, EDITOR_ELEMENTS} from "override/components/flows/panelDefinition";
    import {useCodePanels, useInitialCodeTabs} from "./useCodePanels";
    import {useTopologyPanels} from "./useTopologyPanels";
    import {useKeyShortcuts} from "../../utils/useKeyShortcuts";

    import {getCreateTabKey, getEditTabKey, setupInitialNoCodeTab, setupInitialNoCodeTabIfExists, useNoCodePanels} from "./useNoCodePanels";

    function isTabFlowRelated(element: Tab){
        return ["code", "nocode", "topology"].includes(element.value)
            // when the flow file is dirty all the nocode tabs get splashed
            || element.value.startsWith("nocode-")
    }

    const store = useStore()
    const coreStore = useCoreStore()
    const {showKeyShortcuts} = useKeyShortcuts()
    const flow = computed(() => store.state.flow.flow)

    onMounted(() => {
        store.state.editor.explorerVisible = false
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
        if(prepend){
            panels.value.unshift(panel)
        }else{
            panels.value.push(panel)
        }
    }



    const noCodeHandlers: Parameters<typeof setupInitialNoCodeTab>[2] = {
        onCreateTask(opener, parentPath, blockSchemaPath, refPath, position){
            const createTabId = getCreateTabKey({
                parentPath,
                refPath,
                position,
            }, 0).slice(12)

            const tAdd = openTabs.value.find(t => t.endsWith(createTabId))

            // if the tab is already open and has no data, to avoid conflicting data
            // focus it and don't open a new one
            if(tAdd && tAdd.startsWith("nocode-")){
                focusTab(tAdd)
                return false
            }

            openAddTaskTab(opener, parentPath, blockSchemaPath, refPath, position, isFlowDirty.value)
            return false
        },
        onEditTask(...args){
            // if the tab is already open, focus it
            // and don't open a new one)
            const [
                ,
                parentPath,
                _blockSchemaPath,
                refPath,
            ] = args
            const editKey = getEditTabKey({
                parentPath,
                refPath
            }, 0).slice(12)

            const tEdit = openTabs.value.find(t => t.endsWith(editKey))
            if(tEdit && tEdit.startsWith("nocode-")){
                focusTab(tEdit)
                return false
            }
            openEditTaskTab(...args, isFlowDirty.value)
            return false
        },
        onCloseTask(...args){
            closeTaskTab(...args)
            return false
        },
    }

    const {t} = useI18n()
    function getPanelFromValue(value: string, dirtyFlow = false): {prepend: boolean, panel: Panel}{
        const tab = setupInitialNoCodeTab(value, t, noCodeHandlers, flow.value)
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

    const panels: Ref<Panel[]> = useStorage<any>(
        `flow-${flow.value.namespace}-${flow.value.id}`,
        DEFAULT_ACTIVE_TABS
            .map((t):Panel => getPanelFromValue(t).panel),
        undefined,
        {
            serializer: {
                write(v: Panel[]){
                    return JSON.stringify(v.map(p => ({
                        tabs: p.tabs.map(t => t.value),
                        activeTab: cleanupNoCodeTabKey(p.activeTab?.value),
                        size: p.size,
                    })))
                },
                read(v?: string) {
                    if(v){
                        const panels: {tabs: string[], activeTab: string, size: number}[] = isTourRunning.value ? DEFAULT_TOUR_TABS : JSON.parse(v)
                        return panels
                            .filter((p) => p.tabs.length)
                            .map((p):Panel => {
                                const tabs = p.tabs.map((tab) =>
                                    setupInitialCodeTab(tab)
                                    ?? setupInitialNoCodeTabIfExists(store.state.flow.flowYaml, tab, t, noCodeHandlers)
                                    ?? EDITOR_ELEMENTS.find(e => e.value === tab)!
                                )
                                    // filter out any tab that may have disappeared
                                    .filter(Boolean)
                                const activeTab = tabs.find(t => cleanupNoCodeTabKey(t.value) === p.activeTab) ?? tabs[0]
                                return {
                                    activeTab,
                                    tabs,
                                    size: p.size
                                }
                            })
                    }else{
                        return null
                    }
                }
            },
        },
    )

    const {openAddTaskTab, openEditTaskTab, closeTaskTab} = useNoCodePanels(panels, noCodeHandlers)

    const openTabs = computed(() => panels.value.flatMap(p => p.tabs.map(t => t.value)))

    const {onRemoveTab: onRemoveCodeTab, isFlowDirty} = useCodePanels(panels)

    function onRemoveTab(tab: string){
        onRemoveCodeTab(tab)
    }

    useTopologyPanels(panels, openAddTaskTab, openEditTaskTab)

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

    .tabs-wrapper{
        display:flex;
        align-items: center;
        justify-content: space-between;
        border-bottom: 1px solid var(--ks-border-primary);
        background-image: linear-gradient(
                to right,
                colorPalette.$base-blue-400 0%,
                colorPalette.$base-blue-500 50%,
                transparent 50%,
                transparent 100%
            );
        .dark{
            background-image: linear-gradient(
                to right,
                colorPalette.$base-blue-500 0%,
                colorPalette.$base-blue-700 30%,
                transparent 50%,
                transparent 100%
            );
        }
        background-size: 220% 100%;
        background-position: 100% 0;
        transition: background-position .2s;
    }
    .tabs{
        padding: .5rem 1rem;

        > button{
            background: none;
            border: none;
            padding: .5rem;
            font-size: .8rem;
            color: var(--ks-color-text-primary);
            display: inline-flex;
            align-items: center;
            justify-content: center;
            transition: opacity .2s;
            gap: .25rem;
            opacity: .5;

            &:hover{
                color: var(--ks-color-text-secondary);
                opacity: 1;
            }

            &.active{
                color: var(--ks-color-text-primary);
                opacity: 1;
            }
        }
    }

    .tabs-icon {
        margin-right: .25rem;
        vertical-align: bottom;
    }

    .playgroundMode {
        #{--el-color-primary}: colorPalette.$base-blue-500;
        color: colorPalette.$base-white;
        background-position: 0 0;
    }

    .default-theme{
        .splitpanes__pane {
            background-color: var(--ks-background-panel);
        }

        :deep(.splitpanes__splitter){
            border-top-color: var(--ks-border-primary);
            background-color: var(--ks-background-panel);
            &:before, &:after{
                background-color: var(--ks-content-secondary);
            }
        }
    }
</style>

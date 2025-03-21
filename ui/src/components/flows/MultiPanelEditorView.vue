<template>
    <div class="multi-panel-editor-wrapper">
        <div class="tabs-wrapper">
            <div class="tabs">
                <button
                    v-for="element of EDITOR_ELEMENTS"
                    :key="element.value"
                    :class="{active: activeTabs.includes(element.value)}"
                    @click="setTabValue(element.value)"
                >
                    <component class="tabs-icon" :is="element.button.icon" />
                    {{ element.button.label }}
                </button>
            </div>
            <EditorButtonsWrapper />
        </div>
        <div class="editor-wrapper">
            <MultiPanelTabs v-model="panels" class="editor-panels" @remove-tab="onRemoveTab" />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, Ref, watch} from "vue";
    import {useStorage} from "@vueuse/core";
    import {useStore} from "vuex";

    import MultiPanelTabs, {Panel, Tab} from "../MultiPanelTabs.vue";
    import EditorButtonsWrapper from "../inputs/EditorButtonsWrapper.vue";
    import {DEFAULT_ACTIVE_TABS, EDITOR_ELEMENTS} from "./panelDefinition";
    import {FLOW_RELATED_TABS, useCodePanels, useInitialCodeTabs} from "./useCodePanels";

    const store = useStore()
    const flow = computed(() => store.state.flow.flow)

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
        if(activeTabs.value.includes(tabValue)){
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

    function getPanelFromValue(value: string, dirtyFlow = false): {prepend: boolean, panel: Panel}{
        const element: Tab = EDITOR_ELEMENTS.find(e => e.value === value)!
        if(FLOW_RELATED_TABS.includes(element.value)){
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

    const panels: Ref<Panel[]> = useStorage<any>(
        `panels-${flow.value.namespace}-${flow.value.id}`,
        DEFAULT_ACTIVE_TABS
            .map((t):Panel =>
                ({
                    ...getPanelFromValue(t).panel,
                    size: 100 / DEFAULT_ACTIVE_TABS.length
                })),
        undefined,
        {
            serializer: {
                write(v: Panel[]){
                    return JSON.stringify(v.map(p => ({
                        tabs: p.tabs.map(t => t.value),
                        activeTab: p.activeTab?.value,
                        size: p.size,
                    })))
                },
                read(v?: string) {
                    if(v){
                        const panels: {tabs: string[], activeTab: string, size: number}[] = JSON.parse(v)
                        return panels
                            .filter((p) => p.tabs.length)
                            .map((p):Panel => {
                                const tabs = p.tabs.map(t => setupInitialCodeTab(t) ?? EDITOR_ELEMENTS.find(e => e.value === t)!)
                                const activeTab = tabs.find(t => t.value === p.activeTab)!
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

    const activeTabs = computed(() => panels.value.flatMap(p => p.tabs.map(t => t.value)))

    const {onRemoveTab, isFlowDirty} = useCodePanels(panels)

    watch(isFlowDirty, (dirty) => {
        for(const panel of panels.value){
            if(panel.activeTab && FLOW_RELATED_TABS.includes(panel.activeTab.value)){
                panel.activeTab.dirty = dirty
            }
            for(const tab of panel.tabs){
                if(FLOW_RELATED_TABS.includes(tab.value)){
                    tab.dirty = dirty
                }
            }
        }
    })
</script>

<style lang="scss" scoped>

    .multi-panel-editor-wrapper{
        display: flex;
        flex-direction: column;
        height: 100%;
    }

    .editor-wrapper{
        flex: 1;
        position: relative;
    }

    .editor-panels{
        position: absolute;
    }

    .tabs-wrapper{
        display:flex;
        align-items: center;
        justify-content: space-between;
        border-bottom: 1px solid var(--ks-border-primary);
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
</style>

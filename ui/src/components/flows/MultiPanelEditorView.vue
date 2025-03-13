<template>
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
    <MultiPanelTabs v-model="panels" @remove-tab="removeTab" />
</template>

<script setup lang="ts">
    import {ref, watch} from "vue";

    import MultiPanelTabs, {Panel} from "../MultiPanelTabs.vue";
    import EditorButtonsWrapper from "../inputs/EditorButtonsWrapper.vue";
    import {DEFAULT_ACTIVE_TABS, EDITOR_ELEMENTS} from "./panelDefinition";
    import {useCodePanels} from "./useCodePanels";

    const previousActiveTabs = ref(DEFAULT_ACTIVE_TABS)
    const activeTabs = ref(DEFAULT_ACTIVE_TABS)

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

        activeTabs.value = [...activeTabs.value, tabValue]
    }

    function getPanelFromValue(value: string): {prepend: boolean, panel: Panel}{
        const element = EDITOR_ELEMENTS.find(e => e.value === value)!
        return {
            prepend: "files" === value,
            panel:{
                activeTab: element,
                tabs: [element]
            }
        }
    }

    const panels = ref<Panel[]>(activeTabs.value.map((t: string) =>
        getPanelFromValue(t)).sort((a) => a.prepend ? -1 : 1).map(p => p.panel))

    const {onRemoveTab} = useCodePanels(panels)

    function removeTab(tab: string){
        activeTabs.value = activeTabs.value.filter(t => t !== tab)
        onRemoveTab(tab)
    }

    watch(activeTabs, (newVal) => {
        const previous = previousActiveTabs.value

        const tabIdsToAdd = newVal.filter(t => !previous.includes(t))

        for(const t of tabIdsToAdd){
            const {panel, prepend} = getPanelFromValue(t)
            if(prepend){
                panels.value.unshift(panel)
            }else{
                panels.value.push(panel)
            }
        }

        previousActiveTabs.value = newVal
    })
</script>

<style lang="scss" scoped>
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
            font-size: 1rem;
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

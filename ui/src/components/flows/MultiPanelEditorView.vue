<template>
    <div style="display:flex; align-items: center; justify-content: space-between;">
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
    import {ref, watch, computed, markRaw, h} from "vue";
    import {useStore} from "vuex";

    import {useRouteQuery} from "@vueuse/router";
    import MultiPanelTabs, {Panel} from "../MultiPanelTabs.vue";
    import EditorButtonsWrapper from "../inputs/EditorButtonsWrapper.vue";
    import EditorWrapper from "../inputs/EditorWrapper.vue";
    import {DEFAULT_ACTIVE_TABS, EDITOR_ELEMENTS} from "./panelDefinition";

    const store = useStore()

    const codeEditorTabs = computed(() => store.state.editor.tabs.filter((t:any) => !t.flow))

    const activeTabsUrl = useRouteQuery("activeTabs", DEFAULT_ACTIVE_TABS)
    const previousActiveTabs = ref(activeTabsUrl.value)
    const activeTabs = computed({
        get: () => Array.isArray(activeTabsUrl.value) ? activeTabsUrl.value : [activeTabsUrl.value],
        set: (value) => activeTabsUrl.value = value
    })

    function setTabValue(tabValue: string){
        if(activeTabs.value.includes(tabValue)){
            // here we could add a way to focus the tab
            return
        }

        activeTabs.value = [...activeTabs.value, tabValue]
    }

    interface EditorTab {
        name: string,
        path: string,
        extension: string,
        persistent?: boolean,
        dirty?: boolean,
        flow?: boolean

    }

    function getPanelsFromCodeEditorTabs(codeTabs: EditorTab[]){
        const tabs = codeTabs.map(t => ({
            value: `code-${t.name}`,
            button: {
                label: t.name,
                icon: "FileIcon"
            },
            component: () => h(markRaw(EditorWrapper), {...t, flow: false})
        }))

        return {
            activeTab: tabs[0],
            tabs
        }
    }

    watch(codeEditorTabs, (newVal) => {
        const codeTabs = getPanelsFromCodeEditorTabs(newVal)

        // get all the tabs to add since they are not already part of the panels tabs
        const toAdd = codeTabs.tabs.filter(t => !panels.value.some(p => p.tabs.some(pt => t.value === pt.value)))

        if(toAdd.length === 0){
            return
        }

        // find the first panel where there is already a code tab
        const firstPanelWithCodeTab = panels.value.find(p => p.tabs.some(t => t.value.startsWith("code")))
        if(firstPanelWithCodeTab){
            // add the tabs to the first panel with a code tab
            firstPanelWithCodeTab.tabs.push(...toAdd)
            firstPanelWithCodeTab.activeTab = toAdd[0]
        }else{
            // find the panel where the files tab is
            const filesPanel = panels.value.findIndex(p => p.tabs.some(t => t.value === "files"))
            if(filesPanel >= 0){
                // add the code panel after the files tab
                panels.value.splice(filesPanel + 1, 0, codeTabs)
            }else{
                // add the code tabs at the end
                panels.value.push(codeTabs)
            }
        }
    })

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

    function removeTab(tab: string){
        activeTabs.value = activeTabs.value.filter(t => t !== tab)

        if(tab.startsWith("code-")){
            store.commit("editor/changeOpenedTabs", {
                action: "close",
                name: tab.substring(5),
            });
        }
    }

    watch(activeTabs, (newVal) => {
        const previous = previousActiveTabs.value

        // get the tabs to add
        const toAdd = newVal.filter(t => !previous.includes(t))

        // add the tabs
        for(const t of toAdd){
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
    .tabs{
        padding: .5rem 1rem;
        border-bottom: 1px solid var(--ks-border-primary);

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

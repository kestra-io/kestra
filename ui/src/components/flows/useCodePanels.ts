import {computed, h, markRaw, Ref, watch} from "vue"
import {useStore} from "vuex"
import type {Panel} from "../MultiPanelTabs.vue";
import EditorWrapper from "../inputs/EditorWrapper.vue";
import TypeIcon from "../utils/icons/Type.vue";

interface EditorTab {
    name: string,
    path: string,
    extension: string,
    persistent?: boolean,
    dirty?: boolean,
    flow?: boolean
}

export const FLOW_RELATED_TABS = ["code", "nocode", "topology"]

export function useCodePanels(panels: Ref<Panel[]>) {
    const store = useStore()

    const codeEditorTabs = computed<EditorTab[]>(() => store.state.editor.tabs.filter((t:any) => !t.flow))
    const isFlowDirty = computed(() => store.state.editor.tabs.some((t:any) => t.flow && t.dirty))

    function getPanelsFromCodeEditorTabs(codeTabs: EditorTab[]){
        const tabs = codeTabs.map(t => ({
            value: `code-${t.path}`,
            button: {
                label: t.name,
                icon: () => h(TypeIcon, {name:t.name})
            },
            component: () => h(markRaw(EditorWrapper), {...t, flow: false}),
            dirty: t.dirty,
        }))

        return {
            activeTab: tabs[0],
            tabs
        }
    }


    watch(isFlowDirty, (newVal) => {
        for(const p of panels.value){
            for(const t of p.tabs){
                if(FLOW_RELATED_TABS.includes(t.value)){
                    t.dirty = newVal
                }
            }
        }
    }, {immediate: true})

    const dirtyTabs = computed(() => codeEditorTabs.value.filter(t => t.dirty).map(t => t.path))

    // maintain sync between dirty states of tabs
    watch(dirtyTabs, (newVal) => {
        for(const p of panels.value) {
            for(const t of p.tabs) {
                if(t.value.startsWith("code-") && newVal.includes(t.value.substring(5))){
                    t.dirty = true
                }else{
                    t.dirty = false
                }
            }
        }
    })

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

    function onRemoveTab(tabId: string){
        if(tabId.startsWith("code-")){
            store.dispatch("editor/closeTab", {
                action: "close",
                path: tabId.substring(5),
            });
        }
    }

    return {onRemoveTab, isFlowDirty}
}
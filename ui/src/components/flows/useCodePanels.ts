import {computed, h, markRaw, Ref, watch} from "vue"
import {useStore} from "vuex"
import type {Panel} from "../MultiPanelTabs.vue";
import EditorWrapper, {EditorTabProps} from "../inputs/EditorWrapper.vue";
import TypeIcon from "../utils/icons/Type.vue";

const CODE_PREFIX = "code"

export function getTabFromCodeTab(tab: EditorTabProps){
    return {
        value: `${CODE_PREFIX}-${tab.path}`,
        button: {
            label: tab.name,
            icon: () => h(TypeIcon, {name:tab.name})
        },
        component: () => h(markRaw(EditorWrapper), {...tab, flow: false}),
        dirty: tab.dirty,
    }
}

export function useInitialCodeTabs(){
    const store = useStore()

    function setupInitialCodeTab(tab: string){
        if(!tab.startsWith(`${CODE_PREFIX}-`)){
            return
        }
        const filePath = tab.substring(5)
        const editorTab: EditorTabProps = {
            name: filePath.split("/").pop()!,
            path: filePath,
            extension: filePath.split(".").pop()!,
            flow: false,
            dirty: false
        }
        store.dispatch("editor/openTab", editorTab)
        return getTabFromCodeTab(editorTab)
    }

    return {setupInitialCodeTab}
}

export function useCodePanels(panels: Ref<Panel[]>) {
    const store = useStore()

    const codeEditorTabs = computed<EditorTabProps[]>(() => store.state.editor.tabs.filter((t:any) => !t.flow))
    /**
     * If the flow tab has recorded changes, show all representations as dirty
     */
    const isFlowDirty = computed(() => store.state.editor.tabs.some((t:any) => t.flow && t.dirty))
    const currentTab = computed(() => store.state.editor.current?.path)

    function getPanelsFromCodeEditorTabs(codeTabs: EditorTabProps[]){
        const tabs = codeTabs.map(getTabFromCodeTab)

        return {
            activeTab: tabs[0],
            tabs
        }
    }

    watch(currentTab, (newVal) => {
        // when the current tab changes make sure
        // the corresponding tab is active
        for(const p of panels.value){
            for(const t of p.tabs){
                if(t.value === `${CODE_PREFIX}-${newVal}`){
                    p.activeTab = t
                }
            }
        }
    })

    const dirtyTabs = computed(() => codeEditorTabs.value.filter(t => t.dirty).map(t => t.path))

    // maintain sync between dirty states of tabs
    watch(dirtyTabs, (newVal) => {
        for(const p of panels.value) {
            for(const t of p.tabs) {
                if(t.value.startsWith("code-")){
                    if(newVal.includes(t.value.substring(5))){
                        t.dirty = true
                    }else{
                        t.dirty = false
                    }
                }
            }
        }
    })

    watch(codeEditorTabs, (newVal) => {
        const codeTabs = getPanelsFromCodeEditorTabs(newVal)

        // Loop through tabs to see if any code tab should be removed due to file deletion
        const openedTabs = new Set(codeTabs.tabs.map(tab => tab.value))
        panels.value.forEach((panel) => {
            panel.tabs = panel.tabs.filter(tab => {
                return !tab.value.startsWith("code-") || openedTabs.has(tab.value)
            })
        })

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
        if(tabId.startsWith(`${CODE_PREFIX}-`)){
            store.dispatch("editor/closeTab", {
                action: "close",
                path: tabId.substring(5),
            });
        }
    }

    return {onRemoveTab, isFlowDirty}
}
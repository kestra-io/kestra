import {computed, h, markRaw, Ref, watch} from "vue"
import EditorWrapper from "../inputs/EditorWrapper.vue";
import TypeIcon from "../utils/icons/Type.vue";
import {EditorTabProps, useEditorStore} from "../../stores/editor";
import {EditorElement, Panel, Tab} from "../../utils/multiPanelTypes";

export const CODE_PREFIX = "code"

export function getTabFromFilesTab(tab: EditorTabProps): Tab {
    return {
        uid: `${CODE_PREFIX}-${tab.path}`,
        button: {
            label: tab.name,
            icon: () => h(TypeIcon, {name:tab.name}),
        },
        component: () => h(markRaw(EditorWrapper), tab)
    } satisfies Tab
}

export function getTabPropsFromFilePath(filePath: string, flow: boolean = false): EditorTabProps {
    return {
        name: filePath.split("/").pop()!,
        path: filePath,
        extension: filePath.split(".").pop()!,
        flow,
        dirty: false
    }
}

export function useInitialFilesTabs(EDITOR_ELEMENTS: EditorElement[]){
    const editorStore = useEditorStore()

    const codeElement = EDITOR_ELEMENTS.find(e => e.uid === CODE_PREFIX)!
    codeElement.deserialize = (value: string) => setupInitialCodeTab(value, codeElement)

    function setupInitialCodeTab(tab: string, codeElement: EditorElement){
        const flow = CODE_PREFIX === tab
        if(!flow && !tab.startsWith(`${CODE_PREFIX}-`)){
            return
        }
        const filePath = flow ? "Flow.yaml" : tab.substring(5)
        const editorTab = getTabPropsFromFilePath(filePath, flow)
        editorStore.openTab(editorTab)
        return flow ? codeElement : getTabFromFilesTab(editorTab)
    }

    return {setupInitialCodeTab}
}

export function useFilesPanels(panels: Ref<Panel[]>, namespaceFiles = false) {
    const editorStore = useEditorStore()

    const codeEditorTabs = computed(() => editorStore.tabs.filter((t) => !t.flow))
    /**
     * If the flow tab has recorded changes, show all representations as dirty
     */
    const isFlowDirty = computed(() => editorStore.tabs.some((t:any) => t.flow && t.dirty))
    const currentTab = computed(() => editorStore.current?.path)
    const defaultSize = computed(() => panels.value.length === 0 ? 1 : (panels.value.reduce((acc, p) => acc + (p.size ?? 0), 0) * 100 / panels.value.length))

    function getPanelsFromCodeEditorTabs(codeTabs: EditorTabProps[]){
        const tabs = codeTabs.map(getTabFromFilesTab)

        return {
            activeTab: tabs[0],
            tabs,
            size: defaultSize.value,
        }
    }

    watch(currentTab, (newVal) => {
        // when the current tab changes make sure
        // the corresponding tab is active
        for(const p of panels.value){
            for(const t of p.tabs){
                if(t.uid === `${CODE_PREFIX}-${newVal}`){
                    p.activeTab = t
                }
            }
        }
    })

    watch(codeEditorTabs, (newVal) => {
        const codeTabs = getPanelsFromCodeEditorTabs(newVal.map(tab => ({...tab, namespaceFiles})))

        // Loop through tabs to see if any code tab should be removed due to file deletion
        const openedTabs = new Set(codeTabs.tabs.map(tab => tab.uid))
        panels.value.forEach((panel) => {
            panel.tabs = panel.tabs.filter(tab => {
                return !tab.uid.startsWith("code-") || openedTabs.has(tab.uid)
            })
        })

        // get all the tabs to add since they are not already part of the panels tabs
        const toAdd = codeTabs.tabs.filter(t => !panels.value.some(p => p.tabs.some(pt => t.uid === pt.uid)))

        if(toAdd.length === 0){
            return
        }

        // find the first panel where there is already a code tab
        const firstPanelWithCodeTab = panels.value.find(p => p.tabs.some(t => t.uid.startsWith("code")))
        if(firstPanelWithCodeTab){
            // add the tabs to the first panel with a code tab
            firstPanelWithCodeTab.tabs.push(...toAdd)
            firstPanelWithCodeTab.activeTab = toAdd[0]
        }else{
            // find the panel where the files tab is
            const filesPanel = panels.value.findIndex(p => p.tabs.some(t => t.uid === "files"))
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
            editorStore.closeTab({
                path: tabId.substring(5),
            });
        }
    }

    return {onRemoveTab, isFlowDirty}
}
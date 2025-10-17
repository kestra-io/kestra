import {computed, h, markRaw,provide,ref, Ref} from "vue"
import EditorWrapper from "../inputs/EditorWrapper.vue";
import TypeIcon from "../utils/icons/Type.vue";
import {EditorTabProps} from "../../stores/editor";
import {EditorElement, Panel, Tab} from "../../utils/multiPanelTypes";
import {FILES_CLOSE_TAB_INJECTION_KEY, FILES_OPEN_TAB_INJECTION_KEY} from "../inputs/EditorSidebar.vue";

export const CODE_PREFIX = "code"

function generateUid(tab: Pick<EditorTabProps, "path">){
    return `${CODE_PREFIX}-${tab.path}`
}

export function getTabFromFilesTab(tab: EditorTabProps): Tab {
    return {
        uid: generateUid(tab),
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
    const codeElement = EDITOR_ELEMENTS.find(e => e.uid === CODE_PREFIX)!
    codeElement.deserialize = (value: string) => setupInitialCodeTab(value, codeElement)

    function setupInitialCodeTab(tab: string, codeElement: EditorElement){
        const flow = CODE_PREFIX === tab
        if(!flow && !tab.startsWith(`${CODE_PREFIX}-`)){
            return
        }
        const filePath = flow ? "Flow.yaml" : tab.substring(5)
        const editorTab = getTabPropsFromFilePath(filePath, flow)
        return flow ? codeElement : getTabFromFilesTab(editorTab)
    }

    return {setupInitialCodeTab}
}

export function useFilesPanels(panels: Ref<Panel[]>) {

    function focusTab(tabValue: string){
        for(const panel of panels.value){
            const t = panel.tabs.find(e => e.uid === tabValue);
            if(t) panel.activeTab = t;
        }
    }

    const codeEditorTabs = ref<EditorTabProps[]>([])

    provide(FILES_OPEN_TAB_INJECTION_KEY, (tab: EditorTabProps) => {
        if(!tab.path){
            return
        }
        const existing = codeEditorTabs.value.find(t => t.path === tab.path)
        if(!existing){
            codeEditorTabs.value.push(tab)
            const codeTabs = getPanelsFromCodeEditorTabs([tab])
            const firstPanelWithCodeTab = panels.value.find(p => p.tabs.some(t => t.uid.startsWith("code")))
            if(firstPanelWithCodeTab){
                firstPanelWithCodeTab.tabs.push(codeTabs.tabs[0])
                firstPanelWithCodeTab.activeTab = codeTabs.tabs[0]
            }else{
                panels.value.push(codeTabs)
            }
        }
        focusTab(generateUid(tab))
    })

    provide(FILES_CLOSE_TAB_INJECTION_KEY, (tab) => {
        onRemoveTab(generateUid(tab))
    })

    /**
     * If the flow tab has recorded changes, show all representations as dirty
     */
    const isFlowDirty = ref(false)
    const defaultSize = computed(() => panels.value.length === 0 ? 1 : (panels.value.reduce((acc, p) => acc + (p.size ?? 0), 0) * 100 / panels.value.length))

    function getPanelsFromCodeEditorTabs(codeTabs: EditorTabProps[]){
        const tabs = codeTabs.map(getTabFromFilesTab)

        return {
            activeTab: tabs[0],
            tabs,
            size: defaultSize.value,
        }
    }

    function onRemoveTab(tabId: string){
        if(tabId.startsWith(`${CODE_PREFIX}-`)){
            codeEditorTabs.value = codeEditorTabs.value.filter(t => generateUid(t) !== tabId)
        }
    }

    return {onRemoveTab, isFlowDirty}
}
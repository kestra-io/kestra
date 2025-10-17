import {computed, h, markRaw,provide,ref, Ref} from "vue"
import EditorWrapper, {FILES_SET_DIRTY_INJECTION_KEY} from "../inputs/EditorWrapper.vue";
import TypeIcon from "../utils/icons/Type.vue";
import {EditorTabProps} from "../../stores/editor";
import {EditorElement, Panel, Tab, TabLive} from "../../utils/multiPanelTypes";
import {FILES_CLOSE_TAB_INJECTION_KEY, FILES_OPEN_TAB_INJECTION_KEY} from "../inputs/EditorSidebar.vue";
import {FILES_SAVE_ALL_INJECTION_KEY} from "../inputs/EditorButtonsWrapper.vue";

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

    provide(FILES_OPEN_TAB_INJECTION_KEY, (tab) => {
        if(!tab.path){
            return
        }
        const uid = generateUid(tab)
        const existing = panels.value.some(p => p.tabs.some(t => t.uid === uid))
        if(!existing){
            const panelTab = getTabFromFilesTab(tab)
            const firstPanelWithCodeTab = panels.value.find(p => p.tabs.some(t => t.uid.startsWith("code")))
            if(firstPanelWithCodeTab){
                firstPanelWithCodeTab.tabs.push(panelTab)
                firstPanelWithCodeTab.activeTab = panelTab
            }else{
                panels.value.push({
                    activeTab: panelTab,
                    tabs: [panelTab],
                    size: defaultSize.value,
                })
            }
        }
        focusTab(generateUid(tab))
    })

    provide(FILES_CLOSE_TAB_INJECTION_KEY, (tab) => {
        const uid = generateUid(tab)
        for(const panel of panels.value){
            if(panel.tabs.some(e => e.uid === uid)){
                panel.tabs = panel.tabs.filter(e => e.uid !== uid);
            }
        }
    })

    provide(FILES_SET_DIRTY_INJECTION_KEY, ({path, dirty}) => {
        const uid = generateUid({path})
        const tab = panels.value.flatMap(p => p.tabs).find(t => t.uid === uid) as TabLive
        if(tab){
            tab.dirty = dirty
        }
    })

    // on save all files, set all tabs as not dirty
    provide(FILES_SAVE_ALL_INJECTION_KEY, () => {
        panels.value.forEach(panel => {
            panel.tabs.forEach((tab: TabLive) => {
                tab.dirty = false;
            });
        });
    });

    /**
     * If the flow tab has recorded changes, show all representations as dirty
     */
    const isFlowDirty = ref(false)
    const defaultSize = computed(() => panels.value.length === 0 ? 1 : (panels.value.reduce((acc, p) => acc + (p.size ?? 0), 0) * 100 / panels.value.length))

    return {isFlowDirty}
}
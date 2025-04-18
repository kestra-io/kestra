import {h, markRaw, Ref} from "vue"
import MouseRightClickIcon from "vue-material-design-icons/MouseRightClick.vue";
import type {Panel} from "../MultiPanelTabs.vue";
import NoCodeWrapper, {NoCodeProps} from "../code/NoCodeWrapper.vue";
import {EDITOR_ELEMENTS} from "./panelDefinition";

const NOCODE_PREFIX = "nocode"

export function getTabFromNoCodeTab(tab: NoCodeProps){
    return tab.taskId?.length ?{
        value: `${NOCODE_PREFIX}-${tab.section}-${tab.taskId}`,
        button: {
            label: `${tab.section}-${tab.taskId}`,
            icon:  markRaw(MouseRightClickIcon),
        },
        component: () => h(markRaw(NoCodeWrapper), tab),
        dirty: false,
    }: EDITOR_ELEMENTS.find((e) => e.value === NOCODE_PREFIX)!
}

export function setupInitialCodeTab(tab: string){
    if(!tab.startsWith(`${NOCODE_PREFIX}-`)){
        return
    }
    const filePath = tab.substring(7)
    const section = filePath.split("-").shift() ?? ""
    const editorTab: NoCodeProps = {
        section: section,
        taskId: filePath.substring(section.length + 1),
    }
    return getTabFromNoCodeTab(editorTab)
}

export function useNoCodePanels(panels: Ref<Panel[]>) {
    function openAddTaskTab(
        opener: {
            panelIndex: number,
            tabIndex: number
        },
        section: string,
        taskId: string,
        position: "before" | "after" = "after"
    ) {
        const tab = getTabFromNoCodeTab({
            section,
            taskId,
            position
        })
        panels.value[opener.panelIndex]?.tabs.splice(opener.tabIndex, 1, tab)
    }

    function openEditTaskTab(opener: {panelIndex: number, tabIndex: number}, section: string, taskId: string) {
        const tab = getTabFromNoCodeTab({
            section,
            taskId
        })
        const openerPanel = panels.value[opener.panelIndex]
        if (!openerPanel) {
            return
        }
        openerPanel.tabs.splice(opener.tabIndex, 1, tab)
        openerPanel.activeTab = tab
    }

    return {
        openAddTaskTab,
        openEditTaskTab
    }
}
import {h, markRaw, Ref} from "vue"
import MouseRightClickIcon from "vue-material-design-icons/MouseRightClick.vue";
import type {Panel} from "../MultiPanelTabs.vue";
import NoCodeWrapper, {NoCodeProps} from "../code/NoCodeWrapper.vue";

const NOCODE_PREFIX = "nocode"

export function getTabFromNoCodeTab(tab: NoCodeProps, handlers?: {onCreateTask: (section: string) => boolean, onEditTask: (section: string, taskId: string) => boolean}){
    const {onCreateTask, onEditTask} = handlers ?? {}
    return tab?.taskId?.length ? {
        value: `${NOCODE_PREFIX}-${tab.section}-${tab.taskId}`,
        button: {
            label: `${tab.section}-${tab.taskId}`,
            icon:  markRaw(MouseRightClickIcon),
        },
        component: () => h(markRaw(NoCodeWrapper), tab),
        dirty: false,
    } : tab?.section?.length ? {
        value: `${NOCODE_PREFIX}-${tab.section}`,
        button: {
            label: `New ${tab.section}`,
            icon:  markRaw(MouseRightClickIcon),
        },
        component: () => h(markRaw(NoCodeWrapper), tab),
        dirty: false,
    } : {
        button: {
            icon: markRaw(MouseRightClickIcon),
            label: "No-code"
        },
        value: NOCODE_PREFIX,
        component: () => h(NoCodeWrapper, {
            onCreateTask,
            onEditTask
        }),
        dirty: false,
    }
}

export function setupInitialNoCodeTab(tab: string, handlers:{
    onCreateTask: ( section: string) => boolean,
    onEditTask: ( section: string, taskId: string) => boolean,
}){
    if(tab == NOCODE_PREFIX){
        const {onCreateTask, onEditTask} = handlers ?? {}
        return getTabFromNoCodeTab({}, {
            onCreateTask,
            onEditTask
        })
    }
    if(!tab.startsWith(`${NOCODE_PREFIX}-`)){
        return
    }
    const taskInfoPath = tab.substring(7)
    const section = taskInfoPath.split("-").shift() ?? ""
    const editorTab: NoCodeProps = {
        section: section,
        taskId: taskInfoPath.substring(section.length + 1),
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
        position: "before" | "after" = "after"
    ) {
        const tab = getTabFromNoCodeTab({
            section,
            position,
            creatingTask: true
        })
        panels.value[opener.panelIndex]?.tabs.splice(opener.tabIndex + 1, 0, tab)

        const openerPanel = panels.value[opener.panelIndex]
        if (!openerPanel) {
            return
        }
        openerPanel.activeTab = tab
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
        openerPanel.tabs.splice(opener.tabIndex + 1, 0, tab)
        openerPanel.activeTab = tab
    }

    return {
        openAddTaskTab,
        openEditTaskTab
    }
}
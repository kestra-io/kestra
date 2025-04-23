import {h, markRaw, Ref} from "vue"
import MouseRightClickIcon from "vue-material-design-icons/MouseRightClick.vue";
import type {Panel} from "../MultiPanelTabs.vue";
import NoCodeWrapper, {NoCodeProps} from "../code/NoCodeWrapper.vue";

const NOCODE_PREFIX = "nocode"

export function getTabFromNoCodeTab(tab: NoCodeProps, handlers?: {onCreateTask: (section: string) => boolean, onEditTask: (section: string, taskId: string) => boolean}){
    const {onCreateTask, onEditTask} = handlers ?? {}
    return tab?.taskId?.length ? {
        value: `${NOCODE_PREFIX}-edit-${tab.section}-${tab.taskId}`,
        button: {
            label: `${tab.section}-${tab.taskId}`,
            icon:  markRaw(MouseRightClickIcon),
        },
        component: () => h(NoCodeWrapper, tab),
        dirty: false,
    } : tab?.section?.length ? {
        value: `${NOCODE_PREFIX}-create-${tab.section}-${tab.createIndex}`,
        button: {
            label: `New ${tab.section}`,
            icon:  markRaw(MouseRightClickIcon),
        },
        component: () => h(NoCodeWrapper, tab),
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
    if(taskInfoPath.startsWith("create-")){
        const section = taskInfoPath.split("-").slice(1).shift() ?? ""
        const createIndex = parseInt(taskInfoPath.substring(section.length + 8))
        const editorTab: NoCodeProps = {
            section,
            createIndex
        }
        return getTabFromNoCodeTab(editorTab)
    }else if(taskInfoPath.startsWith("edit-")){
        const section = taskInfoPath.split("-").slice(1).shift() ?? ""
        const taskId = taskInfoPath.substring(section.length + 6)
        const editorTab: NoCodeProps = {
            section,
            taskId
        }
        return getTabFromNoCodeTab(editorTab)
    }
    return undefined
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
        // find all nocode task creating tabs for this section
        const existingTabs = panels.value.flatMap(p => p.tabs).filter((tab) => {
            return tab.value.startsWith(`${NOCODE_PREFIX}-create-${section}-`)
        })

        // find the biggest createIndex
        const createIndex = existingTabs.reduce((acc, tab) => {
            const index = parseInt(tab.value.split("-").slice(-1).shift() ?? "")
            return Math.max(acc, index)
        }, 0) + 1
        // create a new tab with the next createIndex
        const tab = getTabFromNoCodeTab({
            section,
            position,
            createIndex
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
import {h, markRaw, Ref} from "vue"
import {useI18n} from "vue-i18n";
import MouseRightClickIcon from "vue-material-design-icons/MouseRightClick.vue";
import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";
import type {Panel, Tab} from "../MultiPanelTabs.vue";
import NoCodeWrapper, {NoCodeProps} from "../code/NoCodeWrapper.vue";
import {PLUGIN_DEFAULTS_SECTION} from "../../utils/constants";


const NOCODE_PREFIX = "nocode"

interface Opener{
    panelIndex: number,
    tabIndex: number
}

interface Handlers{
    onCreateTask: (opener: Opener, section: string, parentTaskId?: string) => boolean,
    onEditTask: (opener: Opener, section: string, taskId: string) => boolean
    onCloseTask: (opener: Opener) => boolean
}

export function getTabFromNoCodeTab(tab: NoCodeProps, t: (key: string) => string, handlers: Handlers): Tab {
    const preTab = tab?.taskId?.length ? {
        value: `${NOCODE_PREFIX}-edit-${tab.section}-${tab.taskId}`,
        button: {
            label: `${tab.section} / ${tab.taskId}`,
            icon:  markRaw(MouseRightClickIcon),
        },
        dirty: false,
    } : tab?.section?.length ? {
        value: `${NOCODE_PREFIX}-create-${tab.section}-${tab.createIndex}${tab.parentTaskId?.length ? `-${tab.parentTaskId}` : ""}`,
        button: {
            label: `${tab.section} / ${t(`no_code.creation.${tab.section}`)}`,
            icon:  markRaw(MouseRightClickIcon),
        },
        dirty: false,
    } : {
        value: NOCODE_PREFIX,
        button: {
            icon: markRaw(MouseRightClickIcon),
            label: "No-code"
        },
        dirty: false,
    }

    const {onCreateTask, onEditTask, onCloseTask} = handlers ?? {}

    return {
        ...preTab,
        component: markRaw({
            name: "NoCodeTab",
            props: ["panelIndex", "tabIndex"],
            setup:(props: Opener) => () => h(NoCodeWrapper, {
                ...tab,
                onCloseTask: onCloseTask?.bind({}, props),
                onCreateTask: onCreateTask?.bind({}, props),
                onEditTask: onEditTask?.bind({}, props),
            })
        }),
    }
}

export function setupInitialNoCodeTabIfExists(flow: string, tab: string, t: (key: string) => string, handlers: Handlers) {
    if(tab.startsWith(`${NOCODE_PREFIX}-`) && tab.substring(7).startsWith("edit-")){
        const taskInfoPath = tab.substring(7)
        const section = taskInfoPath.split("-").slice(1).shift() ?? ""
        const taskId = taskInfoPath.substring(section.length + 6)
        if(section === PLUGIN_DEFAULTS_SECTION){
            if(!YAML_UTILS.extractPluginDefault(flow, taskId)){
                // if the defaults is not found, we don't create the tab
                return undefined
            }
        }else{
            // check if the task exists in the flow
            if(!YAML_UTILS.extractTask(flow, taskId)){
                // if the task is not found, we don't create the tab
                return undefined
            }
        }
    }

    return setupInitialNoCodeTab(tab, t, handlers)
}

export function setupInitialNoCodeTab(tab: string, t: (key: string) => string, handlers:Handlers) {
    function getNoCodeProps(tab: string): NoCodeProps {
        if(tab === NOCODE_PREFIX){
            return {}
        }
        const taskInfoPath = tab.substring(7)
        const section = taskInfoPath.split("-").slice(1).shift() ?? ""
        if(taskInfoPath.startsWith("create-")){
            const [createIndexPathPart, ...parentTaskIdArray] = taskInfoPath.substring(section.length + 8).split("-")
            const createIndex = parseInt(createIndexPathPart, 10)
            const parentTaskId = parentTaskIdArray.join("-")
            return {
                section,
                createIndex,
                parentTaskId
            }
        }else if(taskInfoPath.startsWith("edit-")){
            const taskId = taskInfoPath.substring(section.length + 6)
            return {
                section,
                taskId
            }
        }
        return {}
    }

    if(tab !== NOCODE_PREFIX && !tab.startsWith(`${NOCODE_PREFIX}-`)){
        return undefined
    }

    return getTabFromNoCodeTab(getNoCodeProps(tab), t, handlers)
}

export function useNoCodePanels(panels: Ref<Panel[]>, handlers:Handlers) {
    const {t} = useI18n()

    function openAddTaskTab(
        opener: {
            panelIndex: number,
            tabIndex: number
        },
        section: string,
        parentTaskId?: string,
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
            parentTaskId,
            position,
            createIndex
        }, t, handlers)

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
        }, t, handlers)
        const openerPanel = panels.value[opener.panelIndex]
        if (!openerPanel) {
            return
        }
        openerPanel.tabs.splice(opener.tabIndex + 1, 0, tab)
        openerPanel.activeTab = tab
    }

    function closeTaskTab(opener: {panelIndex: number, tabIndex: number}) {
        const openerPanel = panels.value[opener.panelIndex]
        if (!openerPanel) {
            return
        }
        const tab = openerPanel.tabs[opener.tabIndex]
        if (tab.value.startsWith(NOCODE_PREFIX)) {
            openerPanel.tabs.splice(opener.tabIndex, 1)
            if (openerPanel.activeTab === tab) {
                openerPanel.activeTab = openerPanel.tabs[opener.tabIndex - 1] ?? openerPanel.tabs[opener.tabIndex + 1]
            }
        }
    }

    return {
        openAddTaskTab,
        openEditTaskTab,
        closeTaskTab,
    }
}
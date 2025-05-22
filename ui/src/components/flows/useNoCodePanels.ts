import {h, markRaw, Ref} from "vue"
import {useI18n} from "vue-i18n";
import MouseRightClickIcon from "vue-material-design-icons/MouseRightClick.vue";
import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
import type {Panel, Tab} from "../MultiPanelTabs.vue";
import NoCodeWrapper, {NoCodeProps} from "../code/NoCodeWrapper.vue";
import {BlockType} from "../code/utils/types";


const NOCODE_PREFIX = "nocode"

interface Opener {
    panelIndex: number,
    tabIndex: number
}

interface Handlers {
    onCreateTask: (opener: Opener, blockType: BlockType | "pluginDefaults", parentPath: string, refPath?: string, position?: "before" | "after") => boolean,
    onEditTask: (opener: Opener, blockType: BlockType | "pluginDefaults", parentPath: string, refPath: string) => boolean
    onCloseTask: (opener: Opener) => boolean
}

export function getTabFromNoCodeTab(tab: NoCodeProps, t: (key: string) => string, handlers: Handlers): Tab {
    function getTabValues(tab: NoCodeProps) {
        if (tab.createIndex !== undefined) {
            return {
                value: `${NOCODE_PREFIX}-${JSON.stringify({
                    action:"create",
                    ...tab
                })}`,
                button: {
                    label: `${tab.parentPath} / ${t(`no_code.creation.${tab.blockType}`)}`,
                    icon: markRaw(MouseRightClickIcon),
                },
            }
        } else if (tab.refPath !== undefined) {
            return {
                value: `${NOCODE_PREFIX}-${JSON.stringify({
                    action: "edit",
                    parentPath: tab.parentPath,
                    refPath: tab.refPath,
                    blockType: tab.blockType,
                })}`,
                button: {
                    label: `${tab.parentPath} / ${tab.refPath}`,
                    icon: markRaw(MouseRightClickIcon),
                },
            }
        }
        return {
            value: NOCODE_PREFIX,
            button: {
                label: "No-code",
                icon: markRaw(MouseRightClickIcon),
            },
        }
    }

    const {onCreateTask, onEditTask, onCloseTask} = handlers ?? {}

    return {
        ...getTabValues(tab),
        dirty: false,
        component: markRaw({
            name: "NoCodeTab",
            props: ["panelIndex", "tabIndex"],
            setup: (props: Opener) => () => h(NoCodeWrapper, {
                ...tab,
                onCloseTask: onCloseTask?.bind({}, props),
                onCreateTask: onCreateTask?.bind({}, props) as any,
                onEditTask: onEditTask?.bind({}, props) as any,
            })
        }),
    }
}

export function setupInitialNoCodeTabIfExists(flow: string, tab: string, t: (key: string) => string, handlers: Handlers) {
    if (tab.startsWith(`${NOCODE_PREFIX}-`) && JSON.parse(tab.substring(7)).action === "edit") {
        const {parentPath, refPath} = JSON.parse(tab.substring(7))
        if (!YAML_UTILS.extractBlockWithPath({source: flow, path: parentPath+refPath})) {
            // if the task is not found, we don't create the tab
            return undefined
        }
    }

    return setupInitialNoCodeTab(tab, t, handlers)
}

export function setupInitialNoCodeTab(tab: string, t: (key: string) => string, handlers: Handlers) {
    function getNoCodeProps(tab: string): NoCodeProps {
        if (tab === NOCODE_PREFIX) {
            return {}
        }
        const taskInfoPath = tab.substring(7)
        const {action, createIndex: createIndexPathPart, ...rest} = JSON.parse(taskInfoPath) ?? {}
        if (action === "create") {
            const createIndex = parseInt(createIndexPathPart, 10)
            return {
                createIndex,
                ...rest
            }
        } else if (action === "edit") {
            return rest
        }
        return {}
    }

    if (tab !== NOCODE_PREFIX && !tab.startsWith(`${NOCODE_PREFIX}-`)) {
        return undefined
    }

    return getTabFromNoCodeTab(getNoCodeProps(tab), t, handlers)
}

export function useNoCodePanels(panels: Ref<Panel[]>, handlers: Handlers) {
    const {t} = useI18n()

    function openAddTaskTab(
        opener: {
            panelIndex: number,
            tabIndex: number
        },
        blockType: BlockType,
        parentPath: string,
        refPath?: string,
        position: "before" | "after" = "after"
    ) {
        // find all nocode task creating tabs for this section
        const existingTabs = panels.value.flatMap(p => p.tabs).filter((tab) => {
            return tab.value.startsWith(`${NOCODE_PREFIX}-create-`)
        })

        // find the biggest createIndex
        const createIndex = existingTabs.reduce((acc, tab) => {
            const index = parseInt(tab.value.split("-").slice(-1).shift() ?? "")
            return Math.max(acc, index)
        }, 0) + 1

        // create a new tab with the next createIndex
        const tab = getTabFromNoCodeTab({
            blockType,
            parentPath,
            refPath,
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

    function openEditTaskTab(opener: { panelIndex: number, tabIndex: number }, blockType: BlockType, parentPath: string, refPath: string) {
        const tab = getTabFromNoCodeTab({
            blockType,
            parentPath,
            refPath,
        }, t, handlers)
        const openerPanel = panels.value[opener.panelIndex]
        if (!openerPanel) {
            return
        }
        openerPanel.tabs.splice(opener.tabIndex + 1, 0, tab)
        openerPanel.activeTab = tab
    }

    function closeTaskTab(opener: { panelIndex: number, tabIndex: number }) {
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
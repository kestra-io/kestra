import {defineAsyncComponent, h, markRaw, Ref, Suspense} from "vue"
import {useStore} from "vuex";
import {useI18n} from "vue-i18n";
import MouseRightClickIcon from "vue-material-design-icons/MouseRightClick.vue";
import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
import type {Panel, Tab} from "../MultiPanelTabs.vue";
import {BlockType} from "../code/utils/types";

import type {NoCodeProps} from "../code/NoCodeWrapper.vue";

const NoCodeWrapper = markRaw(defineAsyncComponent(() => import("../code/NoCodeWrapper.vue")))


const NOCODE_PREFIX = "nocode"

interface Opener {
    panelIndex: number,
    tabIndex: number
}

interface Handlers {
    onCreateTask: (opener: Opener, blockType: BlockType | "pluginDefaults", parentPath: string, refPath?: number, position?: "before" | "after") => boolean,
    onEditTask: (opener: Opener, blockType: BlockType | "pluginDefaults", parentPath: string, refPath?: number) => boolean
    onCloseTask: (opener: Opener) => boolean
}

export function getEditTabKey(tab: NoCodeProps, index: number) {
    const indexWithLeftPadding = String(index).padStart(4, "0")
    return `${NOCODE_PREFIX}-${indexWithLeftPadding}-${JSON.stringify({
                    action: "edit",
                    blockType: tab.blockType,
                    parentPath: tab.parentPath,
                    refPath: tab.refPath,
                })}`
}

export function getCreateTabKey(tab: NoCodeProps, index: number) {
    const indexWithLeftPadding = String(index).padStart(4, "0")
    return `${NOCODE_PREFIX}-${indexWithLeftPadding}-${JSON.stringify({
                    action: "create",
                    ...tab,
                })}`
}

interface NoCodeTabWithAction extends NoCodeProps {
    action: "edit" | "create"
}

let keepAliveCacheBuster = 0

export function getTabFromNoCodeTab(tab: NoCodeTabWithAction, t: (key: string) => string, handlers: Handlers, flow: string, dirty: boolean = false): Tab {
    function getTabValues(tab: NoCodeTabWithAction) {
        // FIXME optimize by avoiding to stringify then parse again the yaml object.
        // maybe we could have a function in the YAML_UTILS that returns the parsed value.
        const parentBlock: any = tab.parentPath ? YAML_UTILS.parse(YAML_UTILS.extractBlockWithPath({
            source: flow,
            path: tab.parentPath.replace(/\.[^.]+$/, ""),
        })) : {}

        const parentName = parentBlock ? parentBlock.id ?? parentBlock.type ?? tab.parentPath : tab.parentPath
        if (tab.action === "create") {
            return {
                value: getCreateTabKey(tab, keepAliveCacheBuster++),
                button: {
                    label: `${parentName} / ${t(`no_code.creation.${tab.blockType}`)}`,
                    icon: markRaw(MouseRightClickIcon),
                },
            }
        } else if (tab.action === "edit") {
            const path = tab.refPath !== undefined
                ? `${tab.parentPath}[${tab.refPath}]`
                : tab.parentPath ?? ""

            const currentBlock: any = tab.parentPath ? YAML_UTILS.parse(YAML_UTILS.extractBlockWithPath({
                source: flow,
                path,
            })) : {}

            return {
                value: getEditTabKey(tab, keepAliveCacheBuster++),
                button: {
                    label: `${parentName} / ${currentBlock?.id ?? tab.refPath}`,
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

    const {action: _, ...restOfTab} = tab

    return {
        ...getTabValues(tab),
        dirty,
        component: markRaw({
            name: "NoCodeTab",
            props: ["panelIndex", "tabIndex"],
            setup: (props: Opener) => () => h(Suspense, {},
                [h(NoCodeWrapper, {
                    ...restOfTab,
                    creatingTask: tab.action === "create",
                    editingTask: tab.action === "edit",
                    onCloseTask: onCloseTask?.bind({}, props),
                    onCreateTask: onCreateTask?.bind({}, props) as any,
                    onEditTask: onEditTask?.bind({}, props) as any,
                })]
            )
        }),
    }
}

export function setupInitialNoCodeTabIfExists(flow: string, tab: string, t: (key: string) => string, handlers: Handlers) {
    if (tab.startsWith(`${NOCODE_PREFIX}-`)){
        const {parentPath, refPath, action} = parseTabId(tab)
        if(action === "edit" && !YAML_UTILS.extractBlockWithPath({source: flow, path: `${parentPath}[${refPath}]`})) {
            // if the task is not found, we don't create the tab
            return undefined
        }
    }

    return setupInitialNoCodeTab(tab, t, handlers, flow)
}

function parseTabId(tabId: string) {
    try {
        if (tabId.startsWith(`${NOCODE_PREFIX}-`)){
           return JSON.parse(tabId.substring(12)) as NoCodeTabWithAction
        } else {
            return {} as NoCodeTabWithAction
        }
    } catch (e) {
        console.error("Failed to parse tabId", e)
        return {} as NoCodeTabWithAction
    }
}

export function setupInitialNoCodeTab(tab: string, t: (key: string) => string, handlers: Handlers, flowYaml: string) {
    if (!tab.startsWith(NOCODE_PREFIX)) {
        return undefined
    }

    return getTabFromNoCodeTab(parseTabId(tab), t, handlers, flowYaml)
}

export function useNoCodePanels(panels: Ref<Panel[]>, handlers: Handlers) {
    const {t} = useI18n()
    const store = useStore()

    function openAddTaskTab(
        opener: {
            panelIndex: number,
            tabIndex: number
        },
        blockType: BlockType | "pluginDefaults",
        parentPath: string,
        refPath?: number,
        position: "before" | "after" = "after",
        dirty: boolean = false,
    ) {
        // create a new tab with the next createIndex
        const tab = getTabFromNoCodeTab({
            action: "create",
            blockType,
            parentPath,
            refPath,
            position,
        }, t, handlers, store.state.flow.flowYaml, dirty)

        panels.value[opener.panelIndex]?.tabs.splice(opener.tabIndex + 1, 0, tab)

        const openerPanel = panels.value[opener.panelIndex]
        if (!openerPanel) {
            return
        }

        openerPanel.activeTab = tab
    }

    function openEditTaskTab(opener: { panelIndex: number, tabIndex: number }, blockType: BlockType | "pluginDefaults", parentPath: string, refPath?: number, dirty: boolean = false) {
        const tab = getTabFromNoCodeTab({
            action: "edit",
            blockType,
            parentPath,
            refPath,
        }, t, handlers, store.state.flow.flowYaml, dirty)

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
        if (tab?.value.startsWith(NOCODE_PREFIX)) {
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
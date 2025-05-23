import {h, markRaw, Ref} from "vue"
import {useI18n} from "vue-i18n";
import MouseRightClickIcon from "vue-material-design-icons/MouseRightClick.vue";
import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
import type {Panel, Tab} from "../MultiPanelTabs.vue";
import NoCodeWrapper, {NoCodeProps} from "../code/NoCodeWrapper.vue";
import {BlockType} from "../code/utils/types";
import {useStore} from "vuex";


const NOCODE_PREFIX = "nocode"

interface Opener {
    panelIndex: number,
    tabIndex: number
}

interface Handlers {
    onCreateTask: (opener: Opener, blockType: BlockType | "pluginDefaults", parentPath: string, refPath?: number, position?: "before" | "after") => boolean,
    onEditTask: (opener: Opener, blockType: BlockType | "pluginDefaults", parentPath: string, refPath: number) => boolean
    onCloseTask: (opener: Opener) => boolean
}

export function getEditTabKey(tab: NoCodeProps) {
    return `${NOCODE_PREFIX}-${JSON.stringify({
                    action: "edit",
                    parentPath: tab.parentPath,
                    refPath: tab.refPath,
                    blockType: tab.blockType,
                })}`
}

export function getCreateTabKey(tab: NoCodeProps) {
    return `${NOCODE_PREFIX}-${JSON.stringify({
                    action: "create",
                    ...tab,
                })}`
}

export function getTabFromNoCodeTab(tab: NoCodeProps, t: (key: string) => string, handlers: Handlers, flow: string, dirty: boolean = false): Tab {
    function getTabValues(tab: NoCodeProps) {
        // FIXME optimize by avoiding to stringify then parse again the yaml object.
        // maybe we could have a function in the YAML_UTILS that returns the parsed value.
        const parentBlock: any = tab.parentPath ? YAML_UTILS.parse(YAML_UTILS.extractBlockWithPath({
            source: flow,
            path: tab.parentPath.replace(/\.[^.]+$/, ""),
        })) : {}

        const parentName = parentBlock ? parentBlock.id ?? parentBlock.type ?? tab.parentPath : tab.parentPath
        if (tab.createIndex !== undefined) {
            return {
                value: getCreateTabKey(tab),
                button: {
                    label: `${parentName} / ${t(`no_code.creation.${tab.blockType}`)}`,
                    icon: markRaw(MouseRightClickIcon),
                },
            }
        } else if (tab.refPath !== undefined) {
            const currentBlock: any = tab.parentPath ? YAML_UTILS.parse(YAML_UTILS.extractBlockWithPath({
                source: flow,
                path: `${tab.parentPath}[${tab.refPath}]`,
            })) : {}
            return {
                value: getEditTabKey(tab),
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

    return {
        ...getTabValues(tab),
        dirty,
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

    return setupInitialNoCodeTab(tab, t, handlers, flow)
}
interface NoCodeTabWithAction {
    action: "edit" | "create", createIndex: number, parentPath: string, refPath: number, blockType: BlockType
}


function parseTabId(tabId: string) {
    return JSON.parse(tabId.substring(7)) as NoCodeTabWithAction
}

export function setupInitialNoCodeTab(tab: string, t: (key: string) => string, handlers: Handlers, flowYaml: string) {
    function getNoCodeProps(tab: string): NoCodeProps {
        if (tab === NOCODE_PREFIX) {
            return {}
        }
        const {action, createIndex, ...rest} = parseTabId(tab)
        if (action === "create") {
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

    return getTabFromNoCodeTab(getNoCodeProps(tab), t, handlers, flowYaml)
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
        createIndex?: number,
    ) {
        // create a new tab with the next createIndex
        const tab = getTabFromNoCodeTab({
            blockType,
            parentPath,
            refPath,
            position,
            createIndex,
        }, t, handlers, store.state.flow.flowYaml)

        panels.value[opener.panelIndex]?.tabs.splice(opener.tabIndex + 1, 0, tab)

        const openerPanel = panels.value[opener.panelIndex]
        if (!openerPanel) {
            return
        }

        openerPanel.activeTab = tab
    }

    function openEditTaskTab(opener: { panelIndex: number, tabIndex: number }, blockType: BlockType | "pluginDefaults", parentPath: string, refPath: number) {
        const tab = getTabFromNoCodeTab({
            blockType,
            parentPath,
            refPath,
        }, t, handlers, store.state.flow.flowYaml)

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
            onCloseTab(tab.value)
        }
    }

    function onCloseTab(tabId: string){
        if(!tabId.startsWith(`${NOCODE_PREFIX}-`)) {
            return
        }

        const tab = parseTabId(tabId)

        // cleanup the addition model on close
        const task = store.getters["flow/createdTasks"]?.[tab.createIndex - 1];

        if (!task || tab?.action !== "create") return;

        store.commit("flow/setFlowYamlBeforeAdd",
                        YAML_UTILS.insertBlockWithPath({
                            source: store.state.flow.flowYamlBeforeAdd,
                            ...task
                        })
        );

        store.commit("flow/setCreatedTask", {
            index: tab.createIndex - 1,
        });
    }

    return {
        openAddTaskTab,
        openEditTaskTab,
        closeTaskTab,
        onCloseTab,
    }
}
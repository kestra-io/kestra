import {computed, ComputedRef, h, markRaw, Ref, Suspense} from "vue"
import {useI18n} from "vue-i18n"
import MouseRightClickIcon from "vue-material-design-icons/MouseRightClick.vue"
import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"

import {useFlowStore} from "../../stores/flow"
import {NoCodeProps} from "./noCodeTypes"
import {displayTaskOf} from "../../utils/flowableBlockOps"

import {trackTabOpen, trackTabClose} from "../../utils/tabTracking"
import {EditorElement, Panel, Tab, TabLive} from "../../utils/multiPanelTypes"
import {usePanelDefaultSize} from "../../composables/usePanelDefaultSize"

export const NOCODE_PREFIX = "nocode"

interface Opener {
    panelIndex: number,
    tabIndex: number
}

interface Handlers {
    onCreateTask: (
        opener: Opener,
        parentPath: string,
        blockSchemaPath: string,
        refPath?: number,
        position?: "before" | "after",
    ) => boolean,
    onEditTask: (
        opener: Opener,
        parentPath: string,
        blockSchemaPath: string,
        refPath?: number,
        split?: boolean,
    ) => boolean
    onCloseTask: (opener: Opener) => boolean
}

export function getEditTabKey(tab: NoCodeProps, index: number) {
    const indexWithLeftPadding = String(index).padStart(4, "0")
    const {
        creatingTask: _,
        position: __,
        editingTask: ___,
        ...relevantTabProps
    } = tab

    const keyParts = {
        action: "edit",
        ...relevantTabProps,
    }
    return `${NOCODE_PREFIX}-${indexWithLeftPadding}-${JSON.stringify(keyParts, Object.keys(keyParts).sort())}`
}

export function getCreateTabKey(tab: NoCodeProps, index: number) {
    const indexWithLeftPadding = String(index).padStart(4, "0")
    const keyParts = {
        action: "create",
        ...tab,
    }
    return `${NOCODE_PREFIX}-${indexWithLeftPadding}-${JSON.stringify(keyParts, Object.keys(keyParts).sort())}`
}

interface NoCodeTabWithAction extends NoCodeProps {
    action: "edit" | "create"
}

let keepAliveCacheBuster = 0

function getTabFromNoCodeTab(Comp: any, tab: NoCodeTabWithAction, t: (key: string) => string, handlers: Handlers, flow: string, te: (key: string) => boolean): Tab {
    function getTabValues(_innerTab: NoCodeTabWithAction) {
        // FIXME optimize by avoiding to stringify then parse again the yaml object.
        const parentBlock: any = tab.parentPath ? YAML_UTILS.parse(YAML_UTILS.extractBlockWithPath({
            source: flow,
            path: tab.parentPath.replace(/\.[^.]+$/, ""),
        })) : {}

        const blockType = tab.parentPath?.split(".").pop() ?? ""

        const newTabName = te(`no_code.creation.${blockType}`) ? t(`no_code.creation.${blockType}`) : t("no_code.creation.default")

        const parentName = parentBlock ? parentBlock.id ?? parentBlock.type ?? tab.parentPath : tab.parentPath
        if (tab.action === "create") {
            return {
                uid: getCreateTabKey(tab, keepAliveCacheBuster++),
                button: {
                    label: `${parentName} / ${newTabName}`,
                    icon: markRaw(MouseRightClickIcon),
                },
            } satisfies Omit<Tab, "component">
        } else if (tab.action === "edit") {
            const path = tab.refPath !== undefined
                ? `${tab.parentPath}[${tab.refPath}]`
                : tab.parentPath ?? ""

            const rawBlock: any = tab.parentPath ? YAML_UTILS.parse(YAML_UTILS.extractBlockWithPath({
                source: flow,
                path,
            })) : {}
            const currentBlock = rawBlock ? displayTaskOf(rawBlock) : rawBlock

            return {
                uid: getEditTabKey(tab, keepAliveCacheBuster++),
                button: {
                    label: `${parentName} / ${currentBlock?.id ?? tab.refPath ?? newTabName}`,
                    icon: markRaw(MouseRightClickIcon),
                },
            } satisfies Omit<Tab, "component">
        }
        return {
            uid: NOCODE_PREFIX,
            button: {
                label: "No-code",
                icon: markRaw(MouseRightClickIcon),
            },
        } satisfies Omit<Tab, "component">
    }

    const {onCreateTask, onEditTask, onCloseTask} = handlers ?? {}

    const {action: _, ...restOfTab} = tab

    return {
        ...getTabValues(tab),
        component: markRaw({
            name: "NoCodeTab",
            props: ["panelIndex", "tabIndex"],
            setup: (props: Opener) => () => h(Suspense, {},
                [h(Comp, {
                    ...restOfTab,
                    creatingTask: tab.action === "create",
                    editingTask: tab.action === "edit",
                    onCreateTask: onCreateTask?.bind({}, props),
                    onEditTask: onEditTask?.bind({}, props),
                    onCloseTask: onCloseTask?.bind({}, props),
                })],
            ),
        }),
    }
}

export function setupInitialNoCodeTabIfExists(Comp: any, tab: string, handlers: Handlers, flowYaml: string, t: (key: string) => string, te: (key: string) => boolean) {
    if (tab === NOCODE_PREFIX) {
        return getTabFromNoCodeTab(Comp, parseTabId(tab), t, handlers, flowYaml, te)
    }

    if (tab.startsWith(`${NOCODE_PREFIX}-`)){
        const {parentPath, refPath, action} = parseTabId(tab)
        const path = (refPath === undefined ? parentPath : `${parentPath}[${refPath}]`) ?? ""
        if(action === "edit" && !YAML_UTILS.extractBlockWithPath({source: flowYaml, path})) {
            return undefined
        }
    }

    return setupInitialNoCodeTab(Comp, tab, handlers, flowYaml, t, te)
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

export function setupInitialNoCodeTab(Comp: any, tab: string, handlers: Handlers, flowYaml: string, t: (key: string) => string, te: (key: string) => boolean) {
    if (!tab.startsWith(NOCODE_PREFIX)) {
        return undefined
    }

    return getTabFromNoCodeTab(Comp, parseTabId(tab), t, handlers, flowYaml, te)
}

export function useNoCodeHandlers(openTabs: Ref<string[]>, focusTab: (tab: string) => void, actions: ReturnType<typeof useNoCodePanels>) {
    const noCodeHandlers: Handlers = {
        onCreateTask(opener, parentPath, blockSchemaPath, refPath, position = "after"){
            // Must match openAddTaskTab's own "after" default (below) exactly, or a caller that
            // omits position (like the createTrigger route-query handler) computes a dedup key
            // that never matches the tab actually created, and duplicates it every time.
            const createTabId = getCreateTabKey({
                parentPath,
                refPath,
                blockSchemaPath,
                position,
            }, 0).slice(12)

            const tAdd = openTabs.value.find(t => t.endsWith(createTabId))

            if(tAdd && tAdd.startsWith(`${NOCODE_PREFIX}-`)){
                focusTab(tAdd)
                return false
            }

            actions.openAddTaskTab(opener, parentPath, blockSchemaPath, refPath, position)
            return false
        },
        onEditTask(...args){
            const [
                opener,
                parentPath,
                blockSchemaPath,
                refPath,
                split,
            ] = args
            const editKey = getEditTabKey({
                parentPath,
                blockSchemaPath,
                refPath,
            }, 0).slice(12)

            const tEdit = openTabs.value.find(t => t.endsWith(editKey))

            if(tEdit && tEdit.startsWith(`${NOCODE_PREFIX}-`)){
                focusTab(tEdit)
                return false
            }
            actions.openEditTaskTab(opener, parentPath, blockSchemaPath, refPath, split ? opener.panelIndex + 1 : undefined)
            return false
        },
        onCloseTask(...args){
            actions.closeTaskTab(...args)
            return false
        },
    }

    return noCodeHandlers
}

export function useNoCodePanels(component: any, panels: Ref<Panel[]>, openTabs: Ref<string[]>, focusTab: (tab: string) => void) {
    const {t, te} = useI18n()
    const flowStore = useFlowStore()

    const defaultSize = usePanelDefaultSize(panels)

    function openAddTaskTab(
        opener: {
            panelIndex: number,
            tabIndex: number
        },
        parentPath: string,
        blockSchemaPath: string,
        refPath?: number,
        position: "before" | "after" = "after",
        fieldName?: string | undefined,
        newPanelIndex?: number,
    ) {
        const tab = getTabFromNoCodeTab(component, {
            action: "create",
            parentPath,
            blockSchemaPath,
            refPath,
            position,
            fieldName,
        }, t, handlers, flowStore.flowYaml, te)

        trackTabOpen(tab)

        if(newPanelIndex !== undefined) {
            const targetPanel = {
                tabs: [tab],
                activeTab: tab,
                size: defaultSize.value,
            }
            panels.value.splice(newPanelIndex, 0, targetPanel)
            return
        }

        const openerPanel = panels.value[opener.panelIndex]
        if (!openerPanel) {
            return
        }

        openerPanel.tabs.splice(opener.tabIndex + 1, 0, tab)
        openerPanel.activeTab = tab
    }

     function openEditTaskTab(
        opener: { panelIndex: number, tabIndex: number },
        parentPath: string,
        blockSchemaPath: string,
        refPath?: number,
        newPanelIndex?: number,
    ) {
        const tab = getTabFromNoCodeTab(component, {
            action: "edit",
            parentPath,
            blockSchemaPath,
            refPath,
        }, t, handlers, flowStore.flowYaml ?? "", te)

        trackTabOpen(tab)

        if(newPanelIndex !== undefined) {
            const targetPanel = {
                tabs: [tab],
                activeTab: tab,
                size: defaultSize.value,
            }
            panels.value.splice(newPanelIndex, 0, targetPanel)
            return
        }

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
        if (tab?.uid.startsWith(`${NOCODE_PREFIX}-`)) {
            trackTabClose(tab)
            openerPanel.tabs.splice(opener.tabIndex, 1)
            if (openerPanel.activeTab === tab) {
                openerPanel.activeTab = openerPanel.tabs[opener.tabIndex - 1] ?? openerPanel.tabs[opener.tabIndex + 1]
            }
        }
    }

    const actions = {
        openAddTaskTab,
        openEditTaskTab,
        closeTaskTab,
    }

    const handlers = useNoCodeHandlers(openTabs, focusTab, actions)

    return actions
}

export function useNoCodePanelsFull(options: {
    RawNoCode: any,
    editorView: Ref<{openTabs: string[], panels: Panel<TabLive>[], focusTab: (tab: string) => void} | undefined | null>,
    editorElements: EditorElement[],
    source: ComputedRef<string>
}) {
    const openTabs = computed(() => options.editorView.value?.openTabs ?? [])
    const panels = computed(() => options.editorView.value?.panels ?? [])
    function focusTab(tabValue: string){
        options.editorView.value?.focusTab(tabValue)
    }

    const {t, te} = useI18n()

    const actions = useNoCodePanels(options.RawNoCode, panels, openTabs, focusTab)
    const noCodeHandlers = useNoCodeHandlers(openTabs, focusTab, actions)

    options.editorElements.find(e => e.uid === "nocode")!.deserialize = (value, allowCreate) => {
        return allowCreate
            ? setupInitialNoCodeTab(options.RawNoCode, value, noCodeHandlers, options.source.value ?? "", t, te)
            : setupInitialNoCodeTabIfExists(options.RawNoCode, value, noCodeHandlers, options.source.value ?? "", t, te)
    }

    return {
        actions,
        noCodeHandlers,
        openTabs,
        focusTab,
        panels,
    }
}
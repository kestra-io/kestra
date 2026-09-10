import {ref, Ref, provide, watch} from "vue"
import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"

import {TOPOLOGY_CLICK_INJECTION_KEY} from "../no-code/injectionKeys"
import {BlockType, TopologyClickParams} from "../no-code/utils/types"
import {useFlowStore} from "../../stores/flow"
import {usePluginsStore} from "../../stores/plugins"
import {NOCODE_PREFIX, useNoCodePanels} from "./useNoCodePanels"
import {Panel} from "../../utils/multiPanelTypes"


const TOPOLOGY_PREFIX = "topology"

type ResolvedTaskPath =
    | {ok: true; path: string; refPath: number; fieldName: string | undefined; blockSchemaPath: string}
    | {ok: false; reason: "no-path" | "no-ref-index"}

function resolveTaskPath(
    source: string,
    pluginsStore: ReturnType<typeof usePluginsStore>,
    section: BlockType,
    id: string,
): ResolvedTaskPath {
    const path = YAML_UTILS.getPathFromSectionAndId({source, section, id})
    if (!path) {
        return {ok: false, reason: "no-path"}
    }

    const parsedPath = YAML_UTILS.parsePath(path)
    const refPath = parsedPath.findLast(p => typeof p === "number")
    const fieldNameAny = parsedPath[parsedPath.length - 1]
    const fieldName = typeof fieldNameAny === "string" ? fieldNameAny : undefined

    if (refPath === undefined) {
        return {ok: false, reason: "no-ref-index"}
    }

    const blockSchemaPath = [pluginsStore.flowSchema?.$ref, "properties", section, "items"].join("/")

    return {ok: true, path, refPath, fieldName, blockSchemaPath}
}

// Reused by callers outside the topology-click flow (e.g. a query-param deep link) that need to
// jump straight to a task's no-code edit tab without going through a graph click.
export function resolveEditTaskTarget(
    source: string,
    pluginsStore: ReturnType<typeof usePluginsStore>,
    section: BlockType,
    id: string,
): {parentPath: string; blockSchemaPath: string; refPath: number | undefined} | undefined {
    const resolved = resolveTaskPath(source, pluginsStore, section, id)
    if (!resolved.ok) {
        return undefined
    }

    const {path, refPath, fieldName, blockSchemaPath} = resolved

    if (fieldName === undefined) {
        // editing a task directly in an array: we need the parent path and the refPath
        const parentPath = path.slice(0, - (refPath.toString().length + 2)) // remove the [refPath] part
        return {parentPath, blockSchemaPath, refPath}
    }

    // editing a task as a subfield (like a dag): the path is self-sufficient
    return {parentPath: path, blockSchemaPath, refPath: undefined}
}

export function useTopologyPanels(
    panels: Ref<Panel[]>,
    openAddTaskTab: ReturnType<typeof useNoCodePanels>["openAddTaskTab"],
    openEditTaskTab: ReturnType<typeof useNoCodePanels>["openEditTaskTab"],
) {
    const topologyClick = ref<TopologyClickParams | undefined>(undefined)
    provide(TOPOLOGY_CLICK_INJECTION_KEY, topologyClick)

    function findTopologyIndexes(arr: Pick<Panel, "tabs">[]): {
        panelIndex: number;
        tabIndex: number;
    } {
        const panelIndex = arr.findIndex((p) =>
            p.tabs.some((t) => t.uid === TOPOLOGY_PREFIX),
        )
        const tabIndex =
            panelIndex !== -1
                ? arr[panelIndex].tabs.findIndex((t) => t.uid === TOPOLOGY_PREFIX)
                : 0
        return {panelIndex: panelIndex !== -1 ? panelIndex : 0, tabIndex}
    }

    function findNoCodeIndexes(arr: Panel[]): {
        panelIndex: number;
        tabIndex: number;
    } {
        const panelIndex = -1
        const tabIndex = -1

        for(const [pIndex, panel] of Object.entries(arr)) {
            for(const [tIndex, tab] of Object.entries(panel.tabs)) {
                if(tab.uid.startsWith(NOCODE_PREFIX)) {
                    return {
                        panelIndex: parseInt(pIndex),
                        tabIndex: parseInt(tIndex),
                    }
                }
            }
        }
        return {
            panelIndex,
            tabIndex,
        }
    }

    const flowStore = useFlowStore()
    const pluginsStore = usePluginsStore()

    watch(topologyClick, (value: TopologyClickParams | undefined) => {
        if (!value) return

        const {
            action,
            params,
        } = value

        let newPanelIndex: number | undefined = undefined
        const target = findNoCodeIndexes(panels.value)
        if(target.panelIndex === -1) {
            const topologyIndexes = findTopologyIndexes(panels.value)
            newPanelIndex = topologyIndexes.panelIndex + 1
        }

        const resolved = resolveTaskPath(flowStore.flowYaml ?? "", pluginsStore, params.section, params.id)

        if (!resolved.ok) {
            if (resolved.reason === "no-ref-index") {
                console.warn("No refPath found in topology click params", value)
            }
            return
        }

        const {path, refPath, fieldName, blockSchemaPath} = resolved

        if (action === "create"){
            const refLength = (refPath.toString().length + 2)
                + (fieldName ? fieldName.length + 1 : 0) // -2 for the [ and ] characters an 1 for the .

            const parentPath = path.slice(0, - refLength) // remove the [refPath] part and the fieldName if necessary
            openAddTaskTab(target, parentPath, blockSchemaPath, refPath, params.position, fieldName, newPanelIndex)
        } else if( action === "edit" && fieldName === undefined) {
            // if the fieldName is undefined, editing a task directly in an array
            // we need the parent path and the refPath
            const parentPath = path.slice(0, - (refPath.toString().length + 2)) // remove the [refPath] part
            openEditTaskTab(target, parentPath, blockSchemaPath, refPath, newPanelIndex)
        }else if (action === "edit" && fieldName !== undefined) {
            // if the fieldName is defined, editing a task as a subfield like a dag
            // we only need the path, the rest is part of the path
            openEditTaskTab(target, path, blockSchemaPath, undefined, newPanelIndex)
        }
        topologyClick.value = undefined // reset the click
    })
}

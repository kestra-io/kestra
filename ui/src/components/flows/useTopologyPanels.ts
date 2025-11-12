import {ref, Ref, provide, watch} from "vue";
import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";

import {TOPOLOGY_CLICK_INJECTION_KEY} from "../no-code/injectionKeys";
import {TopologyClickParams} from "../no-code/utils/types";
import {useFlowStore} from "../../stores/flow";
import {usePluginsStore} from "../../stores/plugins";
import {NOCODE_PREFIX, useNoCodePanels} from "./useNoCodePanels";
import {Panel} from "../../utils/multiPanelTypes";


const TOPOLOGY_PREFIX = "topology";

export function useTopologyPanels(
    panels: Ref<Panel[]>,
    openAddTaskTab: ReturnType<typeof useNoCodePanels>["openAddTaskTab"],
    openEditTaskTab: ReturnType<typeof useNoCodePanels>["openEditTaskTab"],
) {
    const topologyClick = ref<TopologyClickParams | undefined>(undefined);
    provide(TOPOLOGY_CLICK_INJECTION_KEY, topologyClick);

    function findTopologyIndexes(arr: Pick<Panel, "tabs">[]): {
        panelIndex: number;
        tabIndex: number;
    } {
        const panelIndex = arr.findIndex((p) =>
            p.tabs.some((t) => t.uid === TOPOLOGY_PREFIX),
        );
        const tabIndex =
            panelIndex !== -1
                ? arr[panelIndex].tabs.findIndex((t) => t.uid === TOPOLOGY_PREFIX)
                : 0;
        return {panelIndex: panelIndex !== -1 ? panelIndex : 0, tabIndex};
    }

    function findNoCodeIndexes(arr: Panel[]): {
        panelIndex: number;
        tabIndex: number;
    } {
        const panelIndex = -1;
        const tabIndex = -1;

        for(const [pIndex, panel] of Object.entries(arr)) {
            for(const [tIndex, tab] of Object.entries(panel.tabs)) {
                if(tab.uid.startsWith(NOCODE_PREFIX)) {
                    return {
                        panelIndex: parseInt(pIndex),
                        tabIndex: parseInt(tIndex),
                    };
                }
            }
        }
        return {
            panelIndex, 
            tabIndex
        }
    }   

    const flowStore = useFlowStore();
    const pluginsStore = usePluginsStore();

    watch(topologyClick, (value: TopologyClickParams | undefined) => {
        if (!value) return;

        const {
            action,
            params,
        } = value;

        let newPanelIndex: number | undefined = undefined;
        const target = findNoCodeIndexes(panels.value);
        if(target.panelIndex === -1) {
            const topologyIndexes = findTopologyIndexes(panels.value);
            newPanelIndex = topologyIndexes.panelIndex + 1;
        }

        const path = YAML_UTILS.getPathFromSectionAndId({
            source: flowStore.flowYaml ?? "",
            section: params.section,
            id: params.id,
        })

        if (!path) {
            return;
        }

        const parsedPath = YAML_UTILS.parsePath(path);
        const refPath = parsedPath.findLast(p => typeof p === "number");
        const fieldNameAny = parsedPath[parsedPath.length - 1];
        let fieldName: string | undefined = undefined;
        if(typeof fieldNameAny === "string") {
            fieldName = fieldNameAny;
        }

        if (refPath === undefined) {
            console.warn("No refPath found in topology click params", value);
            return
        }

        const blockSchemaPath = [pluginsStore.flowSchema?.$ref, "properties", params.section, "items"].join("/");

        if (action === "create"){
            const refLength = (refPath.toString().length + 2)
                + (fieldName ? fieldName.length + 1 : 0); // -2 for the [ and ] characters an 1 for the .

            const parentPath = path.slice(0, - refLength); // remove the [refPath] part and the fieldName if necessary
            openAddTaskTab(target, parentPath, blockSchemaPath, refPath, params.position, fieldName, newPanelIndex);
        } else if( action === "edit" && fieldName === undefined) {
            // if the fieldName is undefined, editing a task directly in an array
            // we need the parent path and the refPath
            const parentPath = path.slice(0, - (refPath.toString().length + 2)); // remove the [refPath] part
            openEditTaskTab(target, parentPath, blockSchemaPath, refPath, newPanelIndex);
        }else if (action === "edit" && fieldName !== undefined) {
            // if the fieldName is defined, editing a task as a subfield like a dag
            // we only need the path, the rest is part of the path
            openEditTaskTab(target, path, blockSchemaPath, undefined, newPanelIndex);
        }
        topologyClick.value = undefined; // reset the click
    });
}

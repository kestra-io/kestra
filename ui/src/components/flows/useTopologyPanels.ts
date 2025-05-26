import {ref, Ref, provide, watch} from "vue";
import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
import {useStore} from "vuex"

import {TOPOLOGY_CLICK_INJECTION_KEY} from "../code/injectionKeys";
import {TopologyClickParams} from "../code/utils/types";
import {Panel} from "../MultiPanelTabs.vue";

export function useTopologyPanels(
    panels: Ref<Panel[]>,
    openAddTaskTab: any,
    openEditTaskTab: any,
) {
    const topologyClick = ref<TopologyClickParams | undefined>(undefined);
    const store = useStore();
    provide(TOPOLOGY_CLICK_INJECTION_KEY, topologyClick);

    function findTopologyIndexes(arr: { tabs: { value: string }[] }[]): {
        panelIndex: number;
        tabIndex: number;
    } {
        const panelIndex = arr.findIndex((p) =>
            p.tabs.some((t) => t.value === "topology"),
        );
        const tabIndex =
            panelIndex !== -1
                ? arr[panelIndex].tabs.findIndex((t) => t.value === "topology")
                : 0;
        return {panelIndex: panelIndex !== -1 ? panelIndex : 0, tabIndex};
    }

    watch(topologyClick, (value: TopologyClickParams | undefined) => {
        if (!value) return;

        const {
            action,
            params,
        } = value;

        const target = findTopologyIndexes(panels.value);

        const path = YAML_UTILS.getPathFromSectionAndId({
            source: store.getters["flow/flowYaml"],
            section: params.section,
            id: params.id,
        })

        if (!path) {
            return;
        }

        const refPath = /\[(\d+)\]$/.exec(path)?.[1];
        if (!refPath) {
            return;
        }
        const refPathIndex = parseInt(refPath, 10);
        const parentPath = path.slice(0, (refPath.length * -1) - 2); // remove the [refPath] part

        if (action === "create") openAddTaskTab(target, params.section, parentPath, refPathIndex, params.position);
        else if (action === "edit") openEditTaskTab(target, params.section, parentPath, refPathIndex);
    });
}

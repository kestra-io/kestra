import {ref, Ref, provide, watch} from "vue";

import {TOPOLOGY_CLICK_INJECTION_KEY} from "../code/injectionKeys";
import {TopologyClickParams} from "../code/utils/types";
import {Panel, Tab} from "../MultiPanelTabs.vue";

export function useCodeTopology(
    panels: Ref<Panel[]>,
    openAddTaskTab: any,
    openEditTaskTab: any,
) {
    const topologyClick = ref<TopologyClickParams | undefined>(undefined);
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

        const visible = panels.value
            ?.map((p: Panel) => p.tabs.map((t: Tab) => t.value))
            .flat();
        if (!visible.includes("code") && !visible.includes("nocode")) {
            const {
                action,
                params: {section, id},
            } = value;

            const target = findTopologyIndexes(panels.value);

            if (action === "create") openAddTaskTab(target, section, id);
            else if (action === "edit") openEditTaskTab(target, section, id);
        }
    });
}

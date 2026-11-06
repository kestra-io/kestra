import {markRaw, type Component} from "vue"
import PluginManagementActions from "../../../components/plugins/PluginManagementActions.vue"
import PluginVersionsLocked from "../../../components/plugins/PluginVersionsLocked.vue"
import PluginVersioningUpsell from "../../../components/plugins/PluginVersioningUpsell.vue"
import PluginIncludedStatus from "../../../components/plugins/PluginIncludedStatus.vue"
import PluginViewToggle from "../../../components/plugins/PluginViewToggle.vue"
import PluginCatalogTable from "../../../components/plugins/PluginCatalogTable.vue"
import type {Plugin} from "../../../utils/pluginUtils"

export interface PluginCatalogExtension {
    pageActions?: Component;
    banner?: Component;
    toolbar?: Component;
    table?: Component;
    matches?: (plugin: Plugin) => boolean;
    extraCards?: Component;
    extraCardCount?: (catalog: Plugin[], search: string, categories: string[]) => number;
    cardStatus?: Component;
    emptyCopy?: (filtered: boolean) => {title: string, description: string} | undefined;
    reset?: () => void;
    versions?: Component;
    load?: () => void;
}

export function usePluginCatalogExtension(): PluginCatalogExtension {
    return {
        pageActions: markRaw(PluginManagementActions),
        banner: markRaw(PluginVersioningUpsell),
        cardStatus: markRaw(PluginIncludedStatus),
        toolbar: markRaw(PluginViewToggle),
        table: markRaw(PluginCatalogTable),
        versions: markRaw(PluginVersionsLocked),
    }
}

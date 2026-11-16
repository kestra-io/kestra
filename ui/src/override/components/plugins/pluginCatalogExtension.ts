import {markRaw, type Component} from "vue"
import PluginManagementActions from "../../../components/plugins/PluginManagementActions.vue"
import PluginVersionsLocked from "../../../components/plugins/PluginVersionsLocked.vue"
import PluginVersioningUpsell from "../../../components/plugins/PluginVersioningUpsell.vue"
import PluginIncludedStatus from "../../../components/plugins/PluginIncludedStatus.vue"
import PluginViewToggle from "../../../components/plugins/PluginViewToggle.vue"
import PluginCatalogTable from "../../../components/plugins/PluginCatalogTable.vue"
import type {Plugin} from "../../../utils/pluginUtils"

/** A card for an artifact with no catalog entry, sorted among the catalog cards by its title and handed back unchanged to `extraCard` and `table`. */
export interface PluginCatalogExtraEntry {
    key: string;
    title: string;
}

export interface PluginCatalogEntry<Extra extends PluginCatalogExtraEntry = PluginCatalogExtraEntry> {
    key: string;
    title: string;
    plugin?: Plugin;
    extra?: Extra;
}

export interface PluginCatalogExtension {
    pageActions?: Component;
    banner?: Component;
    toolbar?: Component;
    table?: Component;
    matches?: (plugin: Plugin) => boolean;
    extraEntries?: (catalog: Plugin[], search: string, categories: string[]) => PluginCatalogExtraEntry[];
    extraCard?: Component;
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

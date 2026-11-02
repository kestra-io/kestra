import {markRaw, type Component} from "vue"
import PluginManagementActions from "../../../components/plugins/PluginManagementActions.vue"
import PluginVersionsLocked from "../../../components/plugins/PluginVersionsLocked.vue"
import type {Plugin} from "../../../utils/pluginUtils"

export interface PluginCatalogExtension {
    /** Rendered in the top bar of the plugin catalog. */
    pageActions?: Component;
    /** Rendered in the catalog toolbar, next to the category filters. */
    toolbar?: Component;
    /** Narrows the catalog cards on top of search and categories. */
    matches?: (plugin: Plugin) => boolean;
    /** Rendered on each catalog card above its footer; receives the artifact entry as `plugin`. */
    cardStatus?: Component;
    /** Rendered in the Overview column of an artifact page, below its versions; receives the artifact entry as `plugin`. */
    versions?: Component;
    /** Called once when the catalog mounts. */
    load?: () => void;
}

export function usePluginCatalogExtension(): PluginCatalogExtension {
    return {
        pageActions: markRaw(PluginManagementActions),
        versions: markRaw(PluginVersionsLocked),
    }
}

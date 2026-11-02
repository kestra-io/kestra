import {markRaw, type Component} from "vue"
import PluginManagementActions from "../../../components/plugins/PluginManagementActions.vue"
import PluginVersionsLocked from "../../../components/plugins/PluginVersionsLocked.vue"

export interface PluginCatalogExtension {
    /** Rendered in the top bar of the plugin catalog. */
    pageActions?: Component;
    /** Rendered in the Overview column of an artifact page, below its versions; receives the artifact entry as `plugin`. */
    versions?: Component;
}

export function usePluginCatalogExtension(): PluginCatalogExtension {
    return {
        pageActions: markRaw(PluginManagementActions),
        versions: markRaw(PluginVersionsLocked),
    }
}

import {markRaw, type Component} from "vue"
import PluginManagementActions from "../../../components/plugins/PluginManagementActions.vue"
import PluginVersionsLocked from "../../../components/plugins/PluginVersionsLocked.vue"
import PluginVersioningUpsell from "../../../components/plugins/PluginVersioningUpsell.vue"
import PluginIncludedStatus from "../../../components/plugins/PluginIncludedStatus.vue"
import PluginViewToggle from "../../../components/plugins/PluginViewToggle.vue"
import PluginCatalogTable from "../../../components/plugins/PluginCatalogTable.vue"
import {usePluginView} from "../../../components/plugins/pluginView"
import type {Plugin} from "../../../utils/pluginUtils"

export interface PluginCatalogExtension {
    /** Rendered in the top bar of the plugin catalog. */
    pageActions?: Component;
    /** Rendered above the catalog toolbar; receives every artifact entry as `catalog`. */
    banner?: Component;
    /** Rendered in the catalog toolbar, next to the category filters; receives every artifact entry as `catalog`. */
    toolbar?: Component;
    /**
     * Rendered instead of the cards while `showTable()` is true. Receives the filtered, sorted entries as `plugins`,
     * every artifact entry as `catalog`, and the lowercased `search` and the selected `categories`.
     */
    table?: Component;
    showTable?: () => boolean;
    /** Narrows the catalog cards on top of search and categories. */
    matches?: (plugin: Plugin) => boolean;
    /**
     * Rendered after the catalog cards, in the same grid, for entries that have no catalog card. Receives every
     * artifact entry as `catalog`, and the lowercased `search` and the selected `categories`.
     */
    extraCards?: Component;
    /** How many cards `extraCards` renders for the same arguments, so that the empty state is not shown above them. */
    extraCardCount?: (catalog: Plugin[], search: string, categories: string[]) => number;
    /** Rendered on each catalog card above its footer; receives the artifact entry as `plugin`. */
    cardStatus?: Component;
    /** Replaces the catalog's empty-state copy; `filtered` is true while a search or a category filter is active. */
    emptyCopy?: (filtered: boolean) => {title: string, description: string} | undefined;
    /** Called by "Show all plugins", after the search and the category filters are cleared. */
    reset?: () => void;
    /** Rendered in the Overview column of an artifact page, below its versions; receives the artifact entry as `plugin`. */
    versions?: Component;
    /** Called once when the catalog mounts. */
    load?: () => void;
}

export function usePluginCatalogExtension(): PluginCatalogExtension {
    const view = usePluginView()

    return {
        pageActions: markRaw(PluginManagementActions),
        banner: markRaw(PluginVersioningUpsell),
        cardStatus: markRaw(PluginIncludedStatus),
        toolbar: markRaw(PluginViewToggle),
        table: markRaw(PluginCatalogTable),
        showTable: () => view.value === "table",
        versions: markRaw(PluginVersionsLocked),
    }
}

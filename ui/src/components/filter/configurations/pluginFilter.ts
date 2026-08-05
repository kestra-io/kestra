import {computed, ComputedRef} from "vue"
import type {FilterConfiguration} from "@kestra-io/design-system"
import {useI18n} from "vue-i18n"
import {usePluginsStore} from "../../../stores/plugins"

export const usePluginFilter = (): ComputedRef<FilterConfiguration> => {
    const {t} = useI18n()
    const pluginsStore = usePluginsStore()

    return computed(() => {
        return {
            title: t("filter.titles.plugin_filters"),
            searchPlaceholder: t("filter.search_placeholders.search_plugins", {count: pluginsStore.plugins?.length ?? 0}),
            keys: [],
        }
    })
}
import {computed, ComputedRef} from "vue"
import {Comparators, FilterConfiguration} from "@kestra-io/design-system"
import {useI18n} from "vue-i18n"
import {useRoute} from "vue-router"
import {routeFamily} from "../../../utils/routeFamily"
import {namespaceValueProvider} from "./namespaceValueProvider"

export const useKvFilter = (): ComputedRef<FilterConfiguration> => {
    const {t} = useI18n()
    const route = useRoute()

    return computed(() => {
        return {
            title: t("filter.titles.kv_filters"),
            searchPlaceholder: t("filter.search_placeholders.search_kv"),
            keys: routeFamily(route.name) !== "namespaces/update" ? [
                {
                    key: "namespace",
                    label: t("filter.namespace.label"),
                    description: t("filter.namespace.description"),
                    comparators: [
                        Comparators.IN,
                        Comparators.NOT_IN,
                        Comparators.CONTAINS,
                        Comparators.PREFIX,
                    ],
                    valueType: "multi-select" as const,
                    valueProvider: namespaceValueProvider(),
                    searchable: true,
                },
            ] : [],
        }
    })
}

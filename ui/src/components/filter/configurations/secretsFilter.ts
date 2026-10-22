import {computed, ComputedRef} from "vue"
import {FilterConfiguration, Comparators} from "@kestra-io/design-system"
import {useI18n} from "vue-i18n"
import {useRoute} from "vue-router"
import {routeFamily} from "../../../utils/routeFamily"
import {namespaceValueProvider} from "./namespaceValueProvider"

export const useSecretsFilter = (): ComputedRef<FilterConfiguration> => {
    const {t} = useI18n()
    const route = useRoute()

    return computed(() => {
        return {
            title: t("filter.titles.secret_filters"),
            searchPlaceholder: t("filter.search_placeholders.search_secrets"),
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
                    valueType: "multi-select",
                    valueProvider: namespaceValueProvider(),
                    searchable: true,
                },
            ] : [],
        }
    })
}
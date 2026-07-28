import {computed, ComputedRef} from "vue"
import {Comparators, type FilterConfiguration} from "@kestra-io/design-system"
import {useI18n} from "vue-i18n"

export const useNamespacesFilter = (): ComputedRef<FilterConfiguration> => {
    const {t} = useI18n()

    return computed(() => {
        return {
            title: t("filter.titles.namespace_filters"),
            searchPlaceholder: t("filter.search_placeholders.search_namespaces"),
            keys: [
                {
                    key: "namespace",
                    label: t("filter.namespace.label"),
                    description: t("filter.namespace.description"),
                    comparators: [
                        Comparators.EQUALS,
                        Comparators.NOT_EQUALS,
                        Comparators.CONTAINS,
                        Comparators.STARTS_WITH,
                        Comparators.ENDS_WITH,
                        Comparators.PREFIX,
                        Comparators.REGEX,
                    ],
                    valueType: "text",
                    showComparatorSelection: true,
                },
            ],
        }
    })
}

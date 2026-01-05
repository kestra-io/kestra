import {computed, ComputedRef} from "vue";
import {FilterConfiguration, Comparators} from "../utils/filterTypes";
import {useValues} from "../composables/useValues";
import {useI18n} from "vue-i18n";

export const useLogExecutionsFilter = (): ComputedRef<FilterConfiguration> => {
    const {t} = useI18n();

    return computed(() => {
        return {
            title: t("filter.titles.log_filters"),
            searchPlaceholder: t("filter.search_placeholders.search_logs"),
            keys: [
                {
                    key: "level",
                    label: t("filter.level.label"),
                    description: t("filter.level.description"),
                    comparators: [Comparators.EQUALS, Comparators.NOT_EQUALS],
                    valueType: "select",
                    valueProvider: async () => {
                        const {VALUES} = useValues("logs");
                        return VALUES.LEVELS;
                    },
                    visibleByDefault: true
                }
            ]
        };
    });
};
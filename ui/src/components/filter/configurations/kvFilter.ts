import {computed, ComputedRef} from "vue";
import {FilterConfiguration} from "../../../components/filter/utils/filterTypes";
import {useI18n} from "vue-i18n";

export const useKvFilter = (): ComputedRef<FilterConfiguration> => computed(() => {
    const {t} = useI18n();

    return {
        title: t("filter.titles.kv_filters"),
        searchPlaceholder: t("filter.search_placeholders.search_kv"),
        keys: [],
    };
});
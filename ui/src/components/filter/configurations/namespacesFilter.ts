import {FilterConfiguration} from "../../../components/filter/utils/filterTypes";
import {useI18n} from "vue-i18n";

export const namespacesFilter = (): FilterConfiguration => {
    const {t} = useI18n();
    
    return {
        title: t("filter.titles.namespaces_filters"),
        searchPlaceholder: t("filter.search_placeholders.search_namespaces"),
        keys: [],
    };
};
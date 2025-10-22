import {FilterConfiguration} from "../../../components/filter/utils/filterTypes";
import {useI18n} from "vue-i18n";

export const pluginFilter = (count: number): FilterConfiguration => {
    const {t} = useI18n();

    return {
        title: t("filter.titles.plugin_filters"),
        searchPlaceholder: t("filter.search_placeholders.search_plugins", {count}),
        keys: [],
    };
};
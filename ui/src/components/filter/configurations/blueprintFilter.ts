import {FilterConfiguration} from "../utils/filterTypes";
import {useI18n} from "vue-i18n";

export const blueprintFilter = (): FilterConfiguration => {
    const {t} = useI18n();

    return {
        title: t("filter.titles.blueprint_filters"),
        searchPlaceholder: t("filter.search_placeholders.search_blueprints"),
        keys: [
        ]
    };
};
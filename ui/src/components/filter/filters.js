import {useI18n} from "vue-i18n";

const getItem = (key) => {
    return JSON.parse(localStorage.getItem(key) || "[]");
};

const setItem = (key, value) => {
    return localStorage.setItem(key, JSON.stringify(value));
};

const compare = (i, e) => JSON.stringify(i) !== JSON.stringify(e);
const filterItems = (items, element) => {
    return items.filter((item) => compare(item, element));
};

export const formatLabel = (value) => {
    let label = value.label;

    if (value.comparator) label += `:${value.comparator}`;
    if (value.value.length) label += `:${value.value.join(", ")}`;

    return label;
};

export function useFilters(prefix) {
    const {t} = useI18n({useScope: "global"});

    const keys = {recent: `recent__${prefix}`, saved: `saved__${prefix}`};

    const COMPARATORS = {
        IS: t("filters.comparators.is"),
        IS_ONE_OF: t("filters.comparators.is_one_of"),
        IS_NOT: t("filters.comparators.is_not"),
        IS_NOT_ONE_OF: t("filters.comparators.is_not_one_off"),
        CONTAINS: t("filters.comparators.contains"),
        NOT_CONTAINS: t("filters.comparators.not_contains"),
    };

    const OPTIONS = [
        {
            label: t("filters.options.namespace"),
            value: {label: "namespace", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS],
        },
        {
            label: t("filters.options.state"),
            value: {label: "state", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS_ONE_OF, COMPARATORS.IS_NOT_ONE_OF],
        },
        {
            label: t("filters.options.scope"),
            value: {label: "scope", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS_ONE_OF],
        },
        {
            label: t("filters.options.date"),
            value: {label: "date", comparator: undefined, value: []},
            comparators: [],
        },
    ];

    return {
        getRecentItems: () => {
            return getItem(keys.recent);
        },
        setRecentItems: (value) => {
            return setItem(keys.recent, value);
        },
        removeRecentItem: (element) => {
            const filtered = filterItems(getItem(keys.recent), element);
            return setItem(keys.recent, filtered);
        },

        getSavedItems: () => {
            return getItem(keys.saved);
        },
        setSavedItems: (value) => {
            return setItem(keys.saved, value);
        },
        removeSavedItem: (element) => {
            const filtered = filterItems(getItem(keys.saved), element);
            return setItem(keys.saved, filtered);
        },

        OPTIONS,
    };
}

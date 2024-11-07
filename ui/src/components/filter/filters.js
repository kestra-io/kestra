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

    if (value.comparator?.label) label += `:${value.comparator.label}`;
    if (value.value.length) label += `:${value.value.join(", ")}`;

    return label;
};

export const encodeParams = (filters) => {
    return filters.reduce((query, filter) => {
        const label = filter.label === "text" ? "q" : filter.label; // To conform with BE endpoint

        query[label] = filter.value.map((v) => encodeURIComponent(v));
        return query;
    }, {});
};

export function useFilters(prefix) {
    const {t} = useI18n({useScope: "global"});

    const keys = {recent: `recent__${prefix}`, saved: `saved__${prefix}`};

    const COMPARATORS = {
        IS: {
            label: t("filters.comparators.is"),
            value: t("filters.comparators.is"),
            multiple: false,
        },
        IS_ONE_OF: {
            label: t("filters.comparators.is_one_of"),
            value: t("filters.comparators.is_one_of"),
            multiple: true,
        },
        IS_NOT: {
            label: t("filters.comparators.is_not"),
            value: t("filters.comparators.is_not"),
            multiple: false,
        },
        IS_NOT_ONE_OF: {
            label: t("filters.comparators.is_not_one_off"),
            value: t("filters.comparators.is_not_one_off"),
            multiple: true,
        },
        CONTAINS: {
            label: t("filters.comparators.contains"),
            value: t("filters.comparators.contains"),
            multiple: true,
        },
        NOT_CONTAINS: {
            label: t("filters.comparators.not_contains"),
            value: t("filters.comparators.not_contains"),
            multiple: true,
        },
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
            comparators: [COMPARATORS.IS_ONE_OF],
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

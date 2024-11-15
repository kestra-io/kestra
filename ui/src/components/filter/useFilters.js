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

export const formatLabel = (option) => {
    let {label, comparator, value} = option;

    if (comparator?.label) label += `:${comparator.label}`;

    if (value.length) {
        if (label !== "absolute_date:between") label += `:${value.join(", ")}`;
        else label += `:${value[0]?.startDate}:and:${value[0]?.endDate}`;
    }

    return label;
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
        IN: {
            label: t("filters.comparators.in"),
            value: t("filters.comparators.in"),
            multiple: false,
        },
        BETWEEN: {
            label: t("filters.comparators.between"),
            value: t("filters.comparators.between"),
            multiple: false,
        },
        STARTS_WITH: {
            label: t("filters.comparators.starts_with"),
            value: t("filters.comparators.starts_with"),
            multiple: false,
        },
    };

    const OPTIONS = [
        {
            key: "namespace",
            label: t("filters.options.namespace"),
            value: {label: "namespace", comparator: undefined, value: []},
            comparators: [COMPARATORS.STARTS_WITH],
        },
        {
            key: "state",
            label: t("filters.options.state"),
            value: {label: "state", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS_ONE_OF],
        },
        {
            key: "scope",
            label: t("filters.options.scope"),
            value: {label: "scope", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS_ONE_OF],
        },
        {
            key: "labels",
            label: t("filters.options.labels"),
            value: {label: "labels", comparator: undefined, value: []},
            comparators: [COMPARATORS.CONTAINS],
        },
        {
            key: "childFilter",
            label: t("filters.options.child"),
            value: {label: "child", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS],
        },
        {
            key: "timeRange",
            label: t("filters.options.relative_date"),
            value: {label: "relative_date", comparator: undefined, value: []},
            comparators: [COMPARATORS.IN],
        },
        {
            key: "date",
            label: t("filters.options.absolute_date"),
            value: {label: "absolute_date", comparator: undefined, value: []},
            comparators: [COMPARATORS.BETWEEN],
        },
    ];
    const encodeParams = (filters) => {
        const encode = (values, key) => {
            return values
                .map((v) => {
                    if (key === "childFilter" && v === "ALL") {
                        return null;
                    }
                    const encoded = encodeURIComponent(v);
                    return key === "labels"
                        ? encoded.replace(/%3A/g, ":")
                        : encoded;
                })
                .filter((v) => v !== null);
        };

        return filters.reduce((query, filter) => {
            const match = OPTIONS.find((o) => o.value.label === filter.label);
            let key = match ? match.key : filter.label === "text" ? "q" : null;

            if (key) {
                if (key !== "date") query[key] = encode(filter.value, key);
                else {
                    const {startDate, endDate} = filter.value[0];

                    query.startDate = startDate;
                    query.endDate = endDate;
                }
            }

            return query;
        }, {});
    };

    const decodeParams = (query, include) => {
        const params = Object.entries(query)
            .filter(
                ([key]) =>
                    key === "q" ||
                    OPTIONS.some(
                        (o) => o.key === key && include.includes(o.value.label),
                    ),
            )
            .map(([key, value]) => {
                const label =
                    key === "q"
                        ? "text"
                        : OPTIONS.find((o) => o.key === key)?.value.label ||
                          key;

                const decodedValue = Array.isArray(value)
                    ? value.map(decodeURIComponent)
                    : [decodeURIComponent(value)];

                return {label, value: decodedValue};
            });

        // Handle the date functionality by grouping startDate and endDate if they exist
        if (query.startDate && query.endDate) {
            params.push({
                label: "absolute_date:between",
                value: [{startDate: query.startDate, endDate: query.endDate}],
            });
        }

        return params;
    };

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
        encodeParams,
        decodeParams,
    };
}

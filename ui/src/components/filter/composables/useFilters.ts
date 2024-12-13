import {useI18n} from "vue-i18n";

import * as ICONS from "../utils/icons";

const getItem = (key) => {
    return JSON.parse(localStorage.getItem(key) || "[]");
};

const setItem = (key, value) => {
    return localStorage.setItem(key, JSON.stringify(value));
};

export const compare = (i, e) => JSON.stringify(i) !== JSON.stringify(e);
const filterItems = (items, element) => {
    return items.filter((item) => compare(item, element));
};

export function useFilters(prefix) {
    const {t} = useI18n({useScope: "global"});

    const keys = {saved: `saved__${prefix}`};

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
            icon: ICONS.DotsSquare,
            label: t("filters.options.namespace"),
            value: {label: "namespace", comparator: undefined, value: []},
            comparators: [COMPARATORS.STARTS_WITH],
        },
        {
            key: "state",
            icon: ICONS.StateMachine,
            label: t("filters.options.state"),
            value: {label: "state", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS_ONE_OF],
        },
        {
            key: "trigger_state",
            icon: ICONS.StateMachine,
            label: t("filters.options.state"),
            value: {label: "trigger_state", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS],
        },
        {
            key: "scope",
            icon: ICONS.FilterSettingsOutline,
            label: t("filters.options.scope"),
            value: {label: "scope", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS_ONE_OF],
        },
        {
            key: "childFilter",
            icon: ICONS.FilterVariantMinus,
            label: t("filters.options.child"),
            value: {label: "child", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS],
        },
        {
            key: "level",
            icon: ICONS.MathLog,
            label: t("filters.options.level"),
            value: {label: "level", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS],
        },
        {
            key: "task",
            icon: ICONS.TimelineTextOutline,
            label: t("filters.options.task"),
            value: {label: "task", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS],
        },
        {
            key: "metric",
            icon: ICONS.ChartBar,
            label: t("filters.options.metric"),
            value: {label: "metric", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS],
        },
        {
            key: "user",
            icon: ICONS.AccountOutline,
            label: t("filters.options.user"),
            value: {label: "user", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS],
        },
        {
            key: "permission",
            icon: ICONS.AccountCheck,
            label: t("filters.options.permission"),
            value: {label: "permission", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS],
        },
        {
            key: "action",
            icon: ICONS.GestureTapButton,
            label: t("filters.options.action"),
            value: {label: "action", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS],
        },
        {
            key: "details",
            icon: ICONS.TagOutline,
            label: t("filters.options.details"),
            value: {label: "details", comparator: undefined, value: []},
            comparators: [COMPARATORS.CONTAINS],
        },
        {
            key: "aggregation",
            icon: ICONS.Sigma,
            label: t("filters.options.aggregation"),
            value: {label: "aggregation", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS],
        },
        {
            key: "timeRange",
            icon: ICONS.CalendarRangeOutline,
            label: t("filters.options.relative_date"),
            value: {label: "relative_date", comparator: undefined, value: []},
            comparators: [COMPARATORS.IN],
        },
        {
            key: "date",
            icon: ICONS.CalendarEndOutline,
            label: t("filters.options.absolute_date"),
            value: {label: "absolute_date", comparator: undefined, value: []},
            comparators: [COMPARATORS.BETWEEN],
        },
        {
            key: "labels",
            icon: ICONS.TagOutline,
            label: t("filters.options.labels"),
            value: {label: "labels", comparator: undefined, value: []},
            comparators: [COMPARATORS.CONTAINS],
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
            const key = match
                ? match.key
                : filter.label === "text"
                  ? "q"
                  : null;

            if (key) {
                if (key === "details") {
                    match.value.value.forEach((item) => {
                        const value = item.split(":");
                        if (value.length === 2) {
                            console.log(value);
                            query[`details.${value[0]}`] = value[1];
                        }
                    });
                }
                if (key !== "date") query[key] = encode(filter.value, key);
                else {
                    const {startDate, endDate} = filter.value[0];

                    query.startDate = startDate;
                    query.endDate = endDate;
                }
            }

            delete query.details;

            return query;
        }, {});
    };

    const decodeParams = (query, include) => {
        let params = Object.entries(query)
            .filter(
                ([key]) =>
                    key === "q" ||
                    OPTIONS.some(
                        (o) => o.key === key && include.includes(o.value.label),
                    ),
            )
            .map(([key, value]) => {
                if (key.startsWith("details.")) {
                    // Handle details.* keys
                    const detailKey = key.replace("details.", ""); // Extract key after 'details.'
                    return {label: "details", value: `${detailKey}:${value}`};
                }

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

        // Group all details into a single entry
        const details = params
            .filter((p) => p.label === "details")
            .map((p) => p.value); // Collect all `details` values

        if (details.length > 0) {
            // Replace multiple details with a single object
            params = params.filter((p) => p.label !== "details"); // Remove individual details
            params.push({label: "details", value: details});
        }

        // Handle the date functionality by grouping startDate and endDate if they exist
        if (query.startDate && query.endDate) {
            params.push({
                label: "absolute_date",
                value: [{startDate: query.startDate, endDate: query.endDate}],
            });
        }

        // TODO: Will need tweaking once we introduce multiple comparators for filters
        return params.map((p) => {
            const comparator = OPTIONS.find((o) => o.value.label === p.label);
            return {...p, comparator: comparator?.comparators?.[0]};
        });
    };

    return {
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

        COMPARATORS,
        OPTIONS,
        encodeParams,
        decodeParams,
    };
}

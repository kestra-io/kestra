import {useI18n} from "vue-i18n";

import DotsSquare from "vue-material-design-icons/DotsSquare.vue";
import TagOutline from "vue-material-design-icons/TagOutline.vue";
import MathLog from "vue-material-design-icons/MathLog.vue";
import Sigma from "vue-material-design-icons/Sigma.vue";
import TimelineTextOutline from "vue-material-design-icons/TimelineTextOutline.vue";
import ChartBar from "vue-material-design-icons/ChartBar.vue";
import CalendarRangeOutline from "vue-material-design-icons/CalendarRangeOutline.vue";
import CalendarEndOutline from "vue-material-design-icons/CalendarEndOutline.vue";
import FilterVariantMinus from "vue-material-design-icons/FilterVariantMinus.vue";
import StateMachine from "vue-material-design-icons/StateMachine.vue";
import FilterSettingsOutline from "vue-material-design-icons/FilterSettingsOutline.vue";

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
            icon: DotsSquare,
            label: t("filters.options.namespace"),
            value: {label: "namespace", comparator: undefined, value: []},
            comparators: [COMPARATORS.STARTS_WITH],
        },
        {
            key: "state",
            icon: StateMachine,
            label: t("filters.options.state"),
            value: {label: "state", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS_ONE_OF],
        },
        {
            key: "scope",
            icon: FilterSettingsOutline,
            label: t("filters.options.scope"),
            value: {label: "scope", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS_ONE_OF],
        },
        {
            key: "childFilter",
            icon: FilterVariantMinus,
            label: t("filters.options.child"),
            value: {label: "child", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS],
        },
        {
            key: "level",
            icon: MathLog,
            label: t("filters.options.level"),
            value: {label: "level", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS],
        },
        {
            key: "task",
            icon: TimelineTextOutline,
            label: t("filters.options.task"),
            value: {label: "task", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS],
        },
        {
            key: "metric",
            icon: ChartBar,
            label: t("filters.options.metric"),
            value: {label: "metric", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS],
        },
        {
            key: "aggregation",
            icon: Sigma,
            label: t("filters.options.aggregation"),
            value: {label: "aggregation", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS],
        },
        {
            key: "timeRange",
            icon: CalendarRangeOutline,
            label: t("filters.options.relative_date"),
            value: {label: "relative_date", comparator: undefined, value: []},
            comparators: [COMPARATORS.IN],
        },
        {
            key: "date",
            icon: CalendarEndOutline,
            label: t("filters.options.absolute_date"),
            value: {label: "absolute_date", comparator: undefined, value: []},
            comparators: [COMPARATORS.BETWEEN],
        },
        {
            key: "labels",
            icon: TagOutline,
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
        let params = Object.entries(query)
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

        COMPARATORS,
        OPTIONS,
        encodeParams,
        decodeParams,
    };
}

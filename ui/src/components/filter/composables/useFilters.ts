import {useI18n} from "vue-i18n";

import {Comparator, Option} from "../utils/types";

import * as ICONS from "../utils/icons";

const getItem = (key: string) => {
    return JSON.parse(localStorage.getItem(key) || "[]");
};

const setItem = (key: string, value: object) => {
    return localStorage.setItem(key, JSON.stringify(value));
};

export const compare = (item: object, element: object) => {
    return JSON.stringify(item) !== JSON.stringify(element);
};

const filterItems = (items: object[], element: object) => {
    return items.filter((item) => compare(item, element));
};

const buildComparator = (label: string, value: string, multiple: boolean = false): Comparator => {
    return {label, value, multiple};
};

export function useFilters(prefix: string) {
    const {t} = useI18n({useScope: "global"});

    const comparator = (which: string) => `filters.comparators.${which}`;
    const COMPARATORS: Record<string, Comparator> = {
        EQUALS: buildComparator(t(comparator("is")), "$eq"),
        NOT_EQUALS: buildComparator(t(comparator("is_not")), "$ne"),
        CONTAINS: buildComparator(t(comparator("contains")), "$contains", true),
        STARTS_WITH: buildComparator(t(comparator("starts_with")), "$startsWith"),
        ENDS_WITH: buildComparator(t(comparator("ends_with")), "$endsWith"),
        IN: buildComparator(t(comparator("in")), "$in", true),
        NOT_IN: buildComparator(t(comparator("not_in")), "$notIn", true),
        BETWEEN: buildComparator(t(comparator("between")), "$between"),
        GREATER_THAN: buildComparator(t(comparator("greater_than")), "$gt"),
        LESS_THAN: buildComparator(t(comparator("less_than")), "$lt"),
    };

    const OPTIONS: Option[] = [
        {
            key: "namespace",
            icon: ICONS.DotsSquare,
            label: t("filters.options.namespace"),
            value: {label: "namespace", comparator: undefined, value: []},
            comparators: [COMPARATORS.EQUALS, COMPARATORS.NOT_EQUALS, COMPARATORS.STARTS_WITH, COMPARATORS.ENDS_WITH, COMPARATORS.CONTAINS],
        },
        {
            key: "state",
            icon: ICONS.StateMachine,
            label: t("filters.options.state"),
            value: {label: "state", comparator: undefined, value: []},
            comparators: [COMPARATORS.EQUALS, COMPARATORS.NOT_EQUALS, COMPARATORS.IN, COMPARATORS.NOT_IN],
        },
        {
            key: "trigger_state",
            icon: ICONS.StateMachine,
            label: t("filters.options.state"),
            value: {label: "trigger_state", comparator: undefined, value: []},
            comparators: [COMPARATORS.EQUALS, COMPARATORS.NOT_EQUALS, COMPARATORS.IN, COMPARATORS.NOT_IN],
        },
        {
            key: "scope",
            icon: ICONS.FilterSettingsOutline,
            label: t("filters.options.scope"),
            value: {label: "scope", comparator: undefined, value: []},
            comparators: [COMPARATORS.EQUALS, COMPARATORS.NOT_EQUALS],
        },
        {
            key: "childFilter",
            icon: ICONS.FilterVariantMinus,
            label: t("filters.options.child"),
            value: {label: "child", comparator: undefined, value: []},
            comparators: [COMPARATORS.EQUALS, COMPARATORS.NOT_EQUALS],
        },
        {
            key: "level",
            icon: ICONS.MathLog,
            label: t("filters.options.level"),
            value: {label: "level", comparator: undefined, value: []},
            comparators: [COMPARATORS.EQUALS, COMPARATORS.NOT_EQUALS],
        },
        {
            key: "task",
            icon: ICONS.TimelineTextOutline,
            label: t("filters.options.task"),
            value: {label: "task", comparator: undefined, value: []},
            comparators: [COMPARATORS.EQUALS],
        },
        {
            key: "metric",
            icon: ICONS.ChartBar,
            label: t("filters.options.metric"),
            value: {label: "metric", comparator: undefined, value: []},
            comparators: [COMPARATORS.EQUALS],
        },
        {
            key: "user",
            icon: ICONS.AccountOutline,
            label: t("filters.options.user"),
            value: {label: "user", comparator: undefined, value: []},
            comparators: [COMPARATORS.EQUALS],
        },
        {
            key: "details.cls",
            icon: ICONS.FormatListBulletedType,
            label: t("filters.options.type"),
            value: {label: "type", comparator: undefined, value: []},
            comparators: [COMPARATORS.EQUALS],
        },
        {
            key: "type",
            icon: ICONS.FormatListBulletedType,
            label: t("filters.options.type"),
            value: {label: "service_type", comparator: undefined, value: []},
            comparators: [COMPARATORS.EQUALS],
        },
        {
            key: "permission",
            icon: ICONS.AccountCheck,
            label: t("filters.options.permission"),
            value: {label: "permission", comparator: undefined, value: []},
            comparators: [COMPARATORS.EQUALS],
        },
        {
            key: "type",
            icon: ICONS.GestureTapButton,
            label: t("filters.options.action"),
            value: {label: "action", comparator: undefined, value: []},
            comparators: [COMPARATORS.EQUALS],
        },
        {
            key: "status",
            icon: ICONS.StateMachine,
            label: t("filters.options.status"),
            value: {label: "status", comparator: undefined, value: []},
            comparators: [COMPARATORS.IS],
        },
        {
            key: "details",
            icon: ICONS.TagOutline,
            label: t("filters.options.details"),
            value: {label: "details", comparator: undefined, value: []},
            comparators: [COMPARATORS.EQUALS],
        },
        {
            key: "aggregation",
            icon: ICONS.Sigma,
            label: t("filters.options.aggregation"),
            value: {label: "aggregation", comparator: undefined, value: []},
            comparators: [COMPARATORS.EQUALS]
        },
        {
            key: "timeRange",
            icon: ICONS.CalendarRangeOutline,
            label: t("filters.options.relative_date"),
            value: {label: "relative_date", comparator: undefined, value: []},
            comparators: [COMPARATORS.EQUALS, COMPARATORS.NOT_EQUALS]
        },
        {
            key: "date",
            icon: ICONS.CalendarEndOutline,
            label: t("filters.options.absolute_date"),
            value: {label: "absolute_date", comparator: undefined, value: []},
            comparators: [COMPARATORS.EQUALS]
        },
        {
            key: "labels",
            icon: ICONS.TagOutline,
            label: t("filters.options.labels"),
            value: {label: "labels", comparator: undefined, value: []},
            comparators: [COMPARATORS.EQUALS, COMPARATORS.NOT_EQUALS],
        },
    ];

    const keys = {saved: `saved__${prefix}`};

    return {
        getSavedItems: () => {
            return getItem(keys.saved);
        },
        setSavedItems: (value: object) => {
            return setItem(keys.saved, value);
        },
        removeSavedItem: (element: object) => {
            const filtered = filterItems(getItem(keys.saved), element);
            return setItem(keys.saved, filtered);
        },

        COMPARATORS,
        OPTIONS,
    };
}

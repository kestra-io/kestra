import {useI18n} from "vue-i18n";
import {computed} from "vue";
import {useMiscStore} from "override/stores/misc";
import {FilterValue} from "../utils/filterTypes";

import {State} from "@kestra-io/ui-libs";
import {auditLogTypes} from "../../../models/auditLogTypes";
import permission from "../../../models/permission";
import action from "../../../models/action";

const capitalize = (str: string): string => {
    return str.charAt(0).toUpperCase() + str.slice(1);
};

const buildFromArray = (values: string[], isCapitalized = false): FilterValue[] =>
    values.map((value) => ({
        label: isCapitalized ? capitalize(value) : value,
        value,
    }));

const buildFromObject = (object: object): FilterValue[] =>
    Object.entries(object).map(([key, value]) => ({
        label: key,
        value,
    }));

export function useValues(label: string | undefined, t?: ReturnType<typeof useI18n>["t"]) {
    if (t === undefined) {
        t = useI18n({useScope: "global"}).t;
    }

    const isOSS = computed(() => useMiscStore().configs?.edition === "OSS")

    // Override for the scope labels on the dashboard
    const DASHBOARDS = ["dashboard", "custom_dashboard"];
    const SCOPE_LABEL = label === undefined || DASHBOARDS.includes(label) ? t("executions") : label;

    const RELATIVE_DATE = [
        {label: t("datepicker.last5minutes"), value: "PT5M"},
        {label: t("datepicker.last15minutes"), value: "PT15M"},
        {label: t("datepicker.last1hour"), value: "PT1H"},
        {label: t("datepicker.last12hours"), value: "PT12H"},
        {label: t("datepicker.last24hours"), value: "PT24H"},
        {label: t("datepicker.last48hours"), value: "PT48H"},
        {label: t("datepicker.last7days"), value: "PT168H"},
        {label: t("datepicker.last30days"), value: "P30D"},
        {label: t("datepicker.last365days"), value: "PT8760H"},
    ];

    const getRelativeDateLabel = (value: string): string => {
        const item = RELATIVE_DATE.find((item) => item.value === value);
        return item ? item.label : value;
    };

    const VALUES = {
        EXECUTION_STATES: buildFromArray(
            State.arrayAllStates().map((state: { name: string }) => state.name),
        ),
        SCOPES: [
            {
                label: t("scope_filter.user", {label: SCOPE_LABEL}),
                description: t("scope_filter.user_description", {label: SCOPE_LABEL}),
                value: "USER",
            },
            {
                label: t("scope_filter.system", {label: SCOPE_LABEL}),
                description: t("scope_filter.system_description", {label: SCOPE_LABEL}),
                value: "SYSTEM",
            },
        ],
        CHILDS: [
            {
                label: t("trigger filter.options.CHILD"),
                description: t("filter.hierarchy.child_description"),
                value: "CHILD",
            },
            {
                label: t("trigger filter.options.MAIN"),
                description: t("filter.hierarchy.parent_description"),
                value: "MAIN",
            },
        ],
        KINDS: [
            {
                label: t("filter.execution_kind.playground"),
                description: t("filter.execution_kind.playground_description"),
                value: "PLAYGROUND",
            },
            ...(isOSS.value ? [] : [{
                label: t("filter.execution_kind.test"),
                description: t("filter.execution_kind.test_description"),
                value: "TEST",
            }]),
        ],
        LEVELS: buildFromArray(["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]),
        TYPES: auditLogTypes,
        PERMISSIONS: buildFromObject(permission),
        ACTIONS: buildFromObject({
            ...action,
            LOGIN: "LOGIN",
            LOGOUT: "LOGOUT",
        }),
        STATUSES: buildFromArray(["PENDING", "ACCEPTED", "EXPIRED"]),
        AGGREGATIONS: buildFromArray(["SUM", "AVG", "MIN", "MAX"]),
        RELATIVE_DATE,
        TRIGGER_STATES:[
        {label: t("filter.triggerState.enabled"), value: "enabled"},
        {label: t("filter.triggerState.disabled"), value: "disabled"}
    ]
    };

    return {VALUES, getRelativeDateLabel};
}

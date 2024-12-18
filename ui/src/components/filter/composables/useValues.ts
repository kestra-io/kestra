import {useI18n} from "vue-i18n";

import State from "../../../utils/state.js";
import {auditLogTypes} from "../../../models/auditLogTypes";
import permission from "../../../models/permission.js";
import action from "../../../models/action.js";

interface Option {
    label: string;
    value: string;
}

const capitalize = (str: string): string => {
    return str.charAt(0).toUpperCase() + str.slice(1);
};

const buildFromArray = (values: string[], isCapitalized = false): Option[] =>
    values.map((value) => ({
        label: isCapitalized ? capitalize(value) : value,
        value,
    }));

const bulidFromObject = (object: object): Option[] =>
    Object.entries(object).map(([key, value]) => ({
        label: key,
        value,
    }));

export function useValues(label: string) {
    const {t} = useI18n({useScope: "global"});

    // Override for the scope labels on the dashboard
    const DASHBOARDS = ["dashboard", "custom_dashboard"];
    const SCOPE_LABEL = DASHBOARDS.includes(label) ? t("executions") : label;

    const VALUES = {
        EXECUTION_STATES: buildFromArray(
            State.arrayAllStates().map((state: { name: string }) => state.name),
        ),
        TRIGGER_STATES: buildFromArray(["enabled", "disabled"], true),
        SCOPES: [
            {
                label: t("scope_filter.user", {label: SCOPE_LABEL}),
                value: "USER",
            },
            {
                label: t("scope_filter.system", {label: SCOPE_LABEL}),
                value: "SYSTEM",
            },
        ],
        CHILDS: [
            {label: t("trigger filter.options.ALL"), value: "ALL"},
            {label: t("trigger filter.options.CHILD"), value: "CHILD"},
            {label: t("trigger filter.options.MAIN"), value: "MAIN"},
        ],
        LEVELS: buildFromArray(["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]),
        TYPES: auditLogTypes,
        PERMISSIONS: bulidFromObject(permission),
        ACTIONS: bulidFromObject({
            ...action,
            LOGIN: "LOGIN",
            LOGOUT: "LOGOUT",
        }),
        AGGREGATIONS: buildFromArray(["sum", "avg", "min", "max"]),
        RELATIVE_DATE: [
            {label: t("datepicker.last5minutes"), value: "PT5M"},
            {label: t("datepicker.last15minutes"), value: "PT15M"},
            {label: t("datepicker.last1hour"), value: "PT1H"},
            {label: t("datepicker.last12hours"), value: "PT12H"},
            {label: t("datepicker.last24hours"), value: "PT24H"},
            {label: t("datepicker.last48hours"), value: "PT48H"},
            {label: t("datepicker.last7days"), value: "PT168H"},
            {label: t("datepicker.last30days"), value: "PT720H"},
            {label: t("datepicker.last365days"), value: "PT8760H"},
        ],
    };

    return {VALUES};
}

import {useI18n} from "vue-i18n";

import State from "../../utils/state.js";
import permission from "../../models/permission";
import action from "../../models/action";

export function useValues(label?: string) {
    const {t} = useI18n({useScope: "global"});

    const VALUES = {
        SCOPE: [
            {label: t("scope_filter.user", {label}), value: "USER"},
            {label: t("scope_filter.system", {label}), value: "SYSTEM"},
        ],
        CHILD: [
            {label: t("trigger filter.options.ALL"), value: "ALL"},
            {label: t("trigger filter.options.CHILD"), value: "CHILD"},
            {label: t("trigger filter.options.MAIN"), value: "MAIN"},
        ],
        LEVEL: ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"].map((value) => ({
            label: value,
            value,
        })),
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
        EXECUTION_STATE: State.arrayAllStates().map(
            (state: { name: string }) => ({
                label: state.name,
                value: state.name,
            }),
        ),
        AGGREGATION: ["sum", "avg", "min", "max"].map((value) => ({
            label: value,
            value,
        })),
        TRIGGER_STATE: ["enabled", "disabled"].map((value) => ({
            label: `${value.charAt(0).toUpperCase()}${value.slice(1)}`,
            value,
        })),
        PERMISSIONS: Object.entries(permission).map(([key, value]) => ({
            label: key,
            value,
        })),
        ACTIONS: Object.entries(action).map(([key, value]) => ({
            label: key,
            value,
        })),
    };

    return {VALUES};
}

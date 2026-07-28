import _merge from "lodash/merge";
import Utils from "../../../utils/utils";
import {cssVariable, State} from "@kestra-io/ui-libs";
import {getSchemeValue} from "../../../utils/scheme";

import {useMiscStore} from "override/stores/misc";

export function tooltip(tooltipModel: {
    title?: string[];
    body?: { lines: string[] }[];
    labelColors: {
        backgroundColor: string;
        borderColor: string
    }[];
}) {
    const titleLines = tooltipModel.title || [];
    const bodyLines = (tooltipModel.body || []).map((r) => r.lines);

    if (tooltipModel.body) {
        let innerHtml = "";

        titleLines.forEach(function (title) {
            innerHtml += "<h6>" + title + "</h6>";
        });

        bodyLines.forEach(function (body, i) {
            if (body.length > 0) {
                const colors = tooltipModel.labelColors[i];
                let style = "background:" + colors.backgroundColor;
                style += "; border-color:" + colors.borderColor;
                const span = "<span class=\"square\" style=\"" + style + "\"></span>";
                innerHtml += span + body + "<br />";
            }
        });

        return innerHtml;
    }

    return undefined;
}

export function defaultConfig(override: {
    [key: string]: any;
}, theme?: "dark" | "light") {
    const protectedTheme = theme ?? Utils.getTheme();
    const color = protectedTheme === "dark" ? "#FFFFFF" : cssVariable("--bs-gray-700");

    return _merge(
        {
            animation: false as const,
            responsive: true,
            maintainAspectRatio: false,
            layout: {
                padding: {
                    top: 2,
                },
            },
            scales: {
                x: {
                    display: false,
                    title: {color},
                    ticks: {color},
                    border: {color: cssVariable("--ks-border-primary")},
                },
                y: {
                    display: false,
                    title: {color},
                    ticks: {color},
                    border: {color: cssVariable("--ks-border-primary")},
                },
                yB: {
                    display: false,
                    title: {color},
                    ticks: {color},
                },
            },
            elements: {
                line: {
                    borderWidth: 1,
                    fill: "start",
                    tension: 0.3,
                },
                point: {
                    radius: 0,
                    hoverRadius: 0,
                },
            },
            plugins: {
                legend: {
                    display: false,
                },
                tooltip: {
                    mode: "index" as const,
                    intersect: false,
                    enabled: false,
                    boxPadding: 5,
                    usePointStyle: true,
                    multiKeyBackground: "#000000",
                },
            },
        },
        override,
    );
}

export function extractState(value: any) {
    if (!value || typeof value !== "string") return value;

    if (value.includes(",")) {
        const stateNames = State.arrayAllStates().map(state => state.name);
        const matchedState = value.split(",")
            .map(part => part.trim())
            .find(part => stateNames.includes(part.toUpperCase()));
        return matchedState || value;
    }

    return value;
}

// Maps a dashboard `where` FilterType to the list comparator key used in the URL.
// Types with no list equivalent (OR, REGEX, IS_NULL/IS_NOT_NULL, IS_TRUE/IS_FALSE) are omitted (skipped).
const WHERE_TYPE_TO_COMPARATOR: Record<string, string> = {
    EQUAL_TO: "EQUALS",
    NOT_EQUAL_TO: "NOT_EQUALS",
    IN: "IN",
    NOT_IN: "NOT_IN",
    CONTAINS: "CONTAINS",
    STARTS_WITH: "STARTS_WITH",
    ENDS_WITH: "ENDS_WITH",
    PREFIX: "PREFIX",
    GREATER_THAN: "GREATER_THAN",
    GREATER_THAN_OR_EQUAL_TO: "GREATER_THAN_OR_EQUAL_TO",
    LESS_THAN: "LESS_THAN",
    LESS_THAN_OR_EQUAL_TO: "LESS_THAN_OR_EQUAL_TO",
};

interface WhereCondition {
    field?: string;
    labelKey?: string;
    type?: string;
    value?: unknown;
}

interface DrillDownDescriptor {
    /** Router name of the list view to drill into. */
    route: string;
    /** Dashboard field enum name -> list filter key. Fields absent here have no list filter and are skipped. */
    fieldKey: Record<string, string>;
    /** Filter keys the list models as multi-select, so equality maps to IN/NOT_IN. */
    multiSelect: string[];
    /** Whether the list supports a `timeRange` filter (flows, for instance, do not — passing it 400s). */
    timeFiltered: boolean;
}

// Per data-source drill-down config, keyed by the short name of `chart.data.type`
// (io.kestra.plugin.core.dashboard.data.<Name>). Sources without a list to drill into (e.g. Metrics) are omitted,
// so a click on such a chart simply does not navigate rather than landing on the wrong page.
const DRILL_DOWNS: Record<string, DrillDownDescriptor> = {
    Executions: {
        route: "executions/list",
        fieldKey: {NAMESPACE: "namespace", FLOW_ID: "flowId", STATE: "state", LABELS: "labels", SCOPE: "scope", TRIGGER_EXECUTION_ID: "triggerExecutionId"},
        multiSelect: ["namespace", "flowId", "state", "scope"],
        timeFiltered: true,
    },
    Logs: {
        route: "logs/list",
        fieldKey: {NAMESPACE: "namespace", FLOW_ID: "flowId", TRIGGER_ID: "triggerId", TASK_ID: "taskId", TASK_RUN_ID: "taskRunId", ATTEMPT_NUMBER: "attemptNumber"},
        multiSelect: ["namespace"],
        timeFiltered: true,
    },
    Flows: {
        // Flows.Fields only exposes ID / NAMESPACE / REVISION; NAMESPACE is the one with a flows-list filter.
        // Flows have no time dimension — the flows list rejects a timeRange filter — so timeFiltered is false.
        route: "flows/list",
        fieldKey: {NAMESPACE: "namespace"},
        multiSelect: ["namespace"],
        timeFiltered: false,
    },
    Triggers: {
        route: "admin/triggers",
        fieldKey: {NAMESPACE: "namespace", FLOW_ID: "flowId", TRIGGER_ID: "triggerId", WORKER_ID: "workerId"},
        multiSelect: ["namespace"],
        timeFiltered: true,
    },
};

function drillDownFor(dataType?: string): DrillDownDescriptor | undefined {
    const sourceName = dataType?.split(".").pop();
    return sourceName ? DRILL_DOWNS[sourceName] : undefined;
}

// Resolves the list comparator for a (filterKey, dashboard FilterType), honoring multi-select fields
// (equality -> IN/NOT_IN). Returns null when the operator can't be represented for that field.
function comparatorFor(descriptor: DrillDownDescriptor, filterKey: string, type?: string): string | null {
    if (descriptor.multiSelect.includes(filterKey)) {
        // Multi-select fields use IN/NOT_IN for (in)equality; other operators (CONTAINS, STARTS_WITH, …)
        // still pass through to the generic mapping below.
        if (type === "EQUAL_TO" || type === "IN") return "IN";
        if (type === "NOT_EQUAL_TO" || type === "NOT_IN") return "NOT_IN";
    }
    return WHERE_TYPE_TO_COMPARATOR[type ?? ""] ?? null;
}

function encodeFilter(filterKey: string, comparator: string, labelKey: string | undefined, value: string): Record<string, string> {
    if (filterKey === "labels") {
        return labelKey ? {[`filters[labels][${comparator}][${labelKey}]`]: value} : {};
    }
    return {[`filters[${filterKey}][${comparator}]`]: value};
}

function asString(value: unknown): string {
    return Array.isArray(value) ? value.join(",") : String(value);
}


export function dimensionFilter(
    descriptor: DrillDownDescriptor,
    column: {field?: string; labelKey?: string} | undefined,
    value: string,
): Record<string, string> {
    const filterKey = descriptor.fieldKey[column?.field ?? ""];
    if (!filterKey) return {};
    const comparator = comparatorFor(descriptor, filterKey, "EQUAL_TO");
    if (!comparator) return {};
    const resolved = filterKey === "state" ? extractState(value) : value;
    return encodeFilter(filterKey, comparator, column?.labelKey, resolved);
}


// Translates a chart's `where` conditions into list `filters[...]` query params
export function whereToFilters(descriptor: DrillDownDescriptor, where?: unknown): Record<string, string> {
    const out: Record<string, string> = {};
    if (!Array.isArray(where)) return out;

    for (const condition of where as WhereCondition[]) {
        if (condition?.value === undefined || condition.value === null) continue;
        const filterKey = descriptor.fieldKey[condition.field ?? ""];
        if (!filterKey) continue;
        const comparator = comparatorFor(descriptor, filterKey, condition.type);
        if (!comparator) continue;
        Object.assign(out, encodeFilter(filterKey, comparator, condition.labelKey, asString(condition.value)));
    }

    return out;
}

export function chartSegmentDrillDown(
    chart: {data?: Record<string, any>} | undefined,
    column: {field?: string; labelKey?: string} | undefined,
    value: string,
): {name: string; query: Record<string, string>; timeFiltered: boolean} | null {
    const descriptor = drillDownFor(chart?.data?.type);
    if (!descriptor) return null;
    return {
        name: descriptor.route,
        timeFiltered: descriptor.timeFiltered,
        query: {
            ...whereToFilters(descriptor, chart?.data?.where),
            ...dimensionFilter(descriptor, column, value),
        },
    };
}

export function chartClick(moment: any, router: any, route: any, event: any, parsedData: any, elements: any, type = "label", filters: Record<string, any> = {}) {
    const query: Record<string, any> = {};

    if (elements && parsedData) {
        if (elements.length > 0) {
            const element = elements[0];
            let state;
            if (type === "label") {
                // For Bar charts that use dataset labels for state
                state = parsedData.datasets[element.datasetIndex].label;
            } else if (type === "dataset") {
                // For Pie/Doughnut charts that use labels array for state
                state = parsedData.labels[element.index];
            }
            if (state) {
                query.state = extractState(state);
                query.scope = "USER";
                query.size = 100;
                query.page = 1;
            }
        }
    }

    if (event.date) {
        const formattedDate = moment(
            event.date,
            moment.localeData().longDateFormat("L"),
        );
        query.startDate = formattedDate.toISOString(true);
        query.endDate = formattedDate.add(1, "d").toISOString(true);
    }

    if (event.startDate) {
        query.startDate = moment(event.startDate).toISOString(true);
    }

    if (event.endDate) {
        query.endDate = moment(event.endDate).toISOString(true);
    }

    if (event.status) {
        query.status = event.status.toUpperCase();
    }

    if (event.state) {
        query.state = extractState(event.state);
    }

    if (route.query.namespace) {
        query.namespace = route.query.namespace;
    }

    if (route.query.q) {
        query.q = route.query.q;
    }

    if (event.namespace && event.flowId) {
        router.push({
            name: "flows/update",
            params: {
                namespace: event.namespace,
                id: event.flowId,
                tab: "executions",
                tenant: route.params.tenant,
            },
            query: query,
        });
    } else {
        if (event.namespace) {
            query.namespace = event.namespace;
        }

        router.push({
            name: "executions/list",
            params: {
                tenant: route.params.tenant,
            },
            query: {
                ...query,
                ...filters,
                "filters[timeRange][EQUALS]":useMiscStore()?.configs?.chartDefaultDuration ?? "PT24H"
            },
        });
    }
}

export function backgroundFromState(state: string, alpha = 1) {
    const hex = State.color()[state];
    if (!hex) {
        return null;
    }

    const [r, g, b] = hex.match(/\w\w/g)?.map((x) => parseInt(x, 16)) ?? [0, 0, 0];
    return `rgba(${r},${g},${b},${alpha})`;
}

export function getConsistentHEXColor(_theme: "light" | "dark", value: string) {
    // TODO: This was added as part of https://github.com/kestra-io/kestra/issues/10055
    // Idea is to separate the value to parts and only use the status
    // Needs to be made more generic and robust as part of the https://github.com/kestra-io/kestra/issues/9149#issuecomment-2969506266
    const result = value?.includes(",") ? value.split(",").pop()?.trim() : value;

    let hex;

    hex = getSchemeValue(result as any, "executions");
    if (hex && hex !== "transparent") {
        return hex;
    }

    hex = getSchemeValue(result as any, "logs");
    if (hex && hex !== "transparent") {
        return hex;
    }

    // FNV-1a Hash Algorithm
    let hash = 0x811c9dc5; // FNV offset basis (32-bit)
    const fnvPrime = 0x01000193; // FNV prime (32-bit)

    for (let i = 0; i < (value ?? "").length; i++) {
        hash ^= value.charCodeAt(i); // XOR with character code
        hash = (hash * fnvPrime) >>> 0; // Multiply by FNV prime and ensure 32-bit
    }

    // Bit-mixing step (to ensure greater differentiation)
    hash ^= hash >>> 16; // XOR with a shifted version
    hash *= 0x85ebca6b; // Multiply with a large prime
    hash ^= hash >>> 13; // XOR again with another shift
    hash *= 0xc2b2ae35; // Multiply with another large prime
    hash ^= hash >>> 16; // Final XOR with a shift

    // Generate a HEX color from the hash
    return `#${((hash >>> 0) & 0xffffff).toString(16).padStart(6, "0")}`;
}

export function getStateColor(state: string) {
    return State.getStateColor(state);
}

export function getFormat(groupBy?: string) {
    switch (groupBy) {
        case "minute":
            return "LT";
        case "hour":
            return "LLL";
        case "day":
        case "week":
            return "l";
        case "month":
            return "MM.YYYY";
    }
}

import {useRoute, useRouter} from "vue-router"
import {STATES} from "@kestra-io/design-system"
import {useMiscStore} from "override/stores/misc"

interface WhereCondition {
    field?: string;
    labelKey?: string;
    type?: string;
    value?: unknown;
}

interface DrillDownDescriptor {
    route: string;
    fieldKey: Record<string, string>;
    multiSelect: string[];
    timeFiltered: boolean;
    dimensionType?: Record<string, string>;
}

type ClickDimension = {column: {field?: string; labelKey?: string} | undefined; value: string};

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
}

const DRILL_DOWNS: Record<string, DrillDownDescriptor> = {
    Executions: {
        route: "executions/list",
        fieldKey: {
            NAMESPACE: "namespace",
            FLOW_ID: "flowId",
            STATE: "state",
            LABELS: "labels",
            SCOPE: "scope",
            TRIGGER_EXECUTION_ID: "triggerExecutionId",
        },
        multiSelect: ["namespace", "flowId", "state", "scope"],
        timeFiltered: true,
    },
    Logs: {
        route: "logs/list",
        fieldKey: {
            NAMESPACE: "namespace",
            FLOW_ID: "flowId",
            LEVEL: "level",
            TRIGGER_ID: "triggerId",
            TASK_ID: "taskId",
            TASK_RUN_ID: "taskRunId",
            ATTEMPT_NUMBER: "attemptNumber",
        },
        multiSelect: ["namespace"],
        dimensionType: {LEVEL: "GREATER_THAN_OR_EQUAL_TO"},
        timeFiltered: true,
    },
    Flows: {
        route: "flows/list",
        fieldKey: {NAMESPACE: "namespace"},
        multiSelect: ["namespace"],
        timeFiltered: false,
    },
}

function extractState(value: unknown): unknown {
    if (typeof value !== "string" || !value.includes(",")) {
        return value
    }

    return value
        .split(",")
        .map((part) => part.trim())
        .find((part) => part.toUpperCase() in STATES) ?? value
}

function comparatorFor(descriptor: DrillDownDescriptor, filterKey: string, type?: string): string | null {
    if (descriptor.multiSelect.includes(filterKey)) {
        switch (type) {
        case "EQUAL_TO":
        case "IN":
            return "IN"
        case "NOT_EQUAL_TO":
        case "NOT_IN":
            return "NOT_IN"
        }
    }
    return WHERE_TYPE_TO_COMPARATOR[type ?? ""] ?? null
}

function asString(value: unknown): string {
    return Array.isArray(value) ? value.join(",") : String(value)
}

function buildFilter(
    descriptor: DrillDownDescriptor,
    field: string | undefined,
    type: string | undefined,
    labelKey: string | undefined,
    value: unknown,
): Record<string, string> {
    const filterKey = descriptor.fieldKey[field ?? ""]
    if (!filterKey) return {}

    const comparator = comparatorFor(descriptor, filterKey, type)
    if (!comparator) return {}

    const resolved = asString(value)
    if (filterKey === "labels") {
        return labelKey ? {[`filters[labels][${comparator}][${labelKey}]`]: resolved} : {}
    }
    return {[`filters[${filterKey}][${comparator}]`]: resolved}
}

function dimensionFilter(
    descriptor: DrillDownDescriptor,
    column: {field?: string; labelKey?: string} | undefined,
    value: string,
): Record<string, string> {
    const resolved = column?.field === "STATE" ? extractState(value) : value
    const type = descriptor.dimensionType?.[column?.field ?? ""] ?? "EQUAL_TO"
    return buildFilter(descriptor, column?.field, type, column?.labelKey, resolved)
}

function whereToFilters(descriptor: DrillDownDescriptor, where?: unknown): Record<string, string> {
    if (!Array.isArray(where)) return {}

    const out: Record<string, string> = {}
    for (const condition of where as WhereCondition[]) {
        if (condition?.value == null) continue
        Object.assign(out, buildFilter(descriptor, condition.field, condition.type, condition.labelKey, condition.value))
    }
    return out
}

export function chartSegmentDrillDown(
    chart: {data?: Record<string, any>} | undefined,
    column: {field?: string; labelKey?: string} | undefined,
    value: string,
): {name: string; query: Record<string, string>; timeFiltered: boolean} | null {
    const descriptor = DRILL_DOWNS[chart?.data?.type?.split(".").pop() ?? ""]
    if (!descriptor) return null

    return {
        name: descriptor.route,
        timeFiltered: descriptor.timeFiltered,
        query: {
            ...whereToFilters(descriptor, chart?.data?.where),
            ...dimensionFilter(descriptor, column, value),
        },
    }
}

export function useChartDrillDown(chart: {data?: Record<string, any>} | undefined) {
    const route = useRoute()
    const router = useRouter()

    function drillDown(dimensions: ClickDimension[]) {
        const query: Record<string, string> = {}
        let target: ReturnType<typeof chartSegmentDrillDown> = null

        for (const {column, value} of dimensions) {
            const resolved = chartSegmentDrillDown(chart, column, value)
            if (!resolved) return
            target = resolved
            Object.assign(query, resolved.query)
        }
        if (!target) return

        router.push({
            name: target.name,
            params: {tenant: route.params.tenant},
            query: {
                ...query,
                scope: "USER",
                size: 100,
                page: 1,
                ...(target.timeFiltered
                    ? {"filters[timeRange][EQUALS]": useMiscStore()?.configs?.chartDefaultDuration ?? "PT24H"}
                    : {}),
            },
        })
    }

    return {drillDown}
}

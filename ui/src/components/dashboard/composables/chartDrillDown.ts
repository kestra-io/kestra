import {useRoute, useRouter, type LocationQuery} from "vue-router"
import {STATES} from "@kestra-io/design-system"
import {useMiscStore} from "override/stores/misc"
import {useDrillDownStore, type DrillDownTarget} from "../../../stores/drillDown"
import {keepTopLevelFilters} from "../../../utils/queryFilters"
import {getDrillDownPreview} from "./drillDownPreview"

interface WhereCondition {
    field?: string;
    key?: string;
    type?: string;
    value?: unknown;
}

export interface DrillDownDescriptor {
    route: string;
    fieldKey: Record<string, string>;
    multiSelect: string[];
    timeFiltered: boolean;
    dimensionType?: Record<string, string>;
}

type ClickDimension = {column: {field?: string; key?: string} | undefined; value: string};

const START_DATE_PARAM = "filters[startDate][GREATER_THAN_OR_EQUAL_TO]"
const END_DATE_PARAM = "filters[endDate][LESS_THAN_OR_EQUAL_TO]"
const TIME_RANGE_PARAM = "filters[timeRange][EQUALS]"

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

export const DRILL_DOWNS: Record<string, DrillDownDescriptor> = {
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

/**
 * Registers a drill-down descriptor for a dashboard data source type (keyed by the type's short name, e.g. "Assets").
 * Lets editions add drill-down support for their own data sources without editing this map.
 */
export function registerDrillDown(type: string, descriptor: DrillDownDescriptor): void {
    DRILL_DOWNS[type] = descriptor
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
    key: string | undefined,
    value: unknown,
): Record<string, string> {
    const filterKey = descriptor.fieldKey[field ?? ""]
    if (!filterKey) return {}

    const comparator = comparatorFor(descriptor, filterKey, type)
    if (!comparator) return {}

    const resolved = asString(value)
    // Key-value fields (e.g. Executions LABELS, Assets METADATA) carry a key and use a nested filter key.
    if (key) {
        return {[`filters[${filterKey}][${comparator}][${key}]`]: resolved}
    }
    // A key-value field with no key has no list equivalent, so it is skipped (superset, never wrong rows).
    if (filterKey === "labels") {
        return {}
    }
    return {[`filters[${filterKey}][${comparator}]`]: resolved}
}

function dimensionFilter(
    descriptor: DrillDownDescriptor,
    column: {field?: string; key?: string} | undefined,
    value: string,
): Record<string, string> {
    const resolved = column?.field === "STATE" ? extractState(value) : value
    const type = descriptor.dimensionType?.[column?.field ?? ""] ?? "EQUAL_TO"
    return buildFilter(descriptor, column?.field, type, column?.key, resolved)
}

function whereToFilters(descriptor: DrillDownDescriptor, where?: unknown): Record<string, string> {
    if (!Array.isArray(where)) return {}

    const out: Record<string, string> = {}
    for (const condition of where as WhereCondition[]) {
        if (condition?.value == null) continue
        Object.assign(out, buildFilter(descriptor, condition.field, condition.type, condition.key, condition.value))
    }
    return out
}

export function chartSegmentDrillDown(
    chart: {data?: Record<string, any>} | undefined,
    column: {field?: string; key?: string} | undefined,
    value: string,
): {name: string; query: LocationQuery; timeFiltered: boolean} | null {
    const target = chartDrillDownTarget(chart, [{column, value}])
    return target && {name: target.name, timeFiltered: target.timeFiltered, query: target.query}
}

export function chartDrillDownTarget(
    chart: {data?: Record<string, any>} | undefined,
    dimensions: ClickDimension[],
    context?: {routeQuery?: LocationQuery; dateRange?: {startDate: string; endDate: string}},
): DrillDownTarget | null {
    const descriptor = DRILL_DOWNS[chart?.data?.type?.split(".").pop() ?? ""]
    if (!descriptor) return null

    const routeQuery = context?.routeQuery ?? {}
    const query: LocationQuery = {
        ...keepTopLevelFilters(routeQuery, Object.values(descriptor.fieldKey)),
        ...whereToFilters(descriptor, chart?.data?.where),
    }
    for (const {column, value} of dimensions) {
        Object.assign(query, dimensionFilter(descriptor, column, value))
    }

    return {
        name: descriptor.route,
        timeFiltered: descriptor.timeFiltered,
        query,
        timeWindow: context?.dateRange
            ? {[START_DATE_PARAM]: context.dateRange.startDate, [END_DATE_PARAM]: context.dateRange.endDate}
            : routeTimeWindow(routeQuery),
    }
}

// Only one of the two forms is returned, since the backend treats `startDate`/`endDate` and
// `timeRange` as mutually exclusive.
function routeTimeWindow(query: LocationQuery): Record<string, string> | undefined {
    const startDate = query[START_DATE_PARAM]
    const endDate = query[END_DATE_PARAM]
    if (startDate && endDate) {
        return {[START_DATE_PARAM]: String(startDate), [END_DATE_PARAM]: String(endDate)}
    }

    const timeRange = query[TIME_RANGE_PARAM]
    return timeRange ? {[TIME_RANGE_PARAM]: String(timeRange)} : undefined
}

/**
 * Reproduces the query augmentation the full listing pages expect (scope, pagination, and the time
 * window), so the drawer's fetch, the drawer's "Open full page" push, and the legacy full-page
 * redirect all build the exact same query from a drill-down target.
 */
export function buildFullQuery(target: DrillDownTarget, pagination?: {size: number; page: number}): Record<string, any> {
    return {
        ...target.query,
        scope: "USER",
        ...(pagination ? {size: pagination.size, page: pagination.page} : {}),
        ...(target.timeWindow ?? (target.timeFiltered
            ? {[TIME_RANGE_PARAM]: useMiscStore()?.configs?.chartDefaultDuration ?? "PT24H"}
            : {})),
    }
}

export function useChartDrillDown(chart: {data?: Record<string, any>} | undefined) {
    const route = useRoute()
    const router = useRouter()

    function drillDown(dimensions: ClickDimension[], options?: {dateRange?: {startDate: string; endDate: string}}) {
        const target = chartDrillDownTarget(chart, dimensions, {
            routeQuery: route.query,
            dateRange: options?.dateRange,
        })
        if (!target) return

        const preview = getDrillDownPreview(target.name)
        if (preview && preview.mode !== "none") {
            useDrillDownStore().open(target)
            return
        }

        if (!preview) {
            console.error(`[drill-down] no drawer preview registered for route "${target.name}" — register one via registerDrillDownPreview, or opt out with {mode: "none"} to keep the full-page redirect silently.`)
        }

        router.push({
            name: target.name,
            params: {tenant: route.params.tenant},
            query: buildFullQuery(target, {size: 100, page: 1}),
        })
    }

    return {drillDown}
}

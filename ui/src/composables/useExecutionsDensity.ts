import type {LocationQuery} from "vue-router"
import {decodeSearchParams} from "@kestra-io/design-system"
import type {QueryFilter, QueryFilterField} from "@kestra-io/kestra-sdk"
import {useDashboardStore} from "../stores/dashboard"
import DENSITY_CHART from "../components/executions/timeline/executions_density_chart.yaml?raw"
import ROWS_CHART from "../components/executions/timeline/executions_timeline_rows_chart.yaml?raw"
import {
    densityPointsFrom, planDensityRequests, rowDensityPointsFrom,
    type DensityPlan, type DensityPoint, type DensityRequest, type RowDensityPoint,
} from "../utils/executionsTimeline"

const TIME_FIELDS = new Set<string>(["timeRange", "startDate", "endDate"])
export const MAX_PREVIEW_PAGE_SIZE = 1000
export const MAX_ROWS_PAGES = 5

export interface ExecutionsDensity {
    points: DensityPoint[];
    granularityMs: number;
    ignoredFilters: QueryFilterField[];
}

export interface ExecutionsRowDensity {
    rowPoints: RowDensityPoint[];
    granularityMs: number;
    ignoredFilters: QueryFilterField[];
    isCapped: boolean;
}

type ChartPage = Awaited<ReturnType<ReturnType<typeof useDashboardStore>["chartPreview"]>>

function rangeFilters(scopeFilters: QueryFilter[], request: DensityRequest): QueryFilter[] {
    return [
        ...scopeFilters,
        {field: "startDate", operation: "GREATER_THAN_OR_EQUAL_TO", value: new Date(request.startMs).toISOString()},
        ...(request.lookAheadEndMs === undefined
            ? []
            : [{field: "endDate", operation: "LESS_THAN_OR_EQUAL_TO", value: new Date(request.lookAheadEndMs).toISOString()} as QueryFilter]),
    ]
}

function planFor(query: Record<string, unknown>, startMs: number, endMs: number, nowMs: number): {scopeFilters: QueryFilter[]; plan: DensityPlan} {
    return {
        scopeFilters: (decodeSearchParams(query as LocationQuery) as QueryFilter[]).filter(filter => !TIME_FIELDS.has(String(filter.field))),
        plan: planDensityRequests(startMs, endMs, nowMs),
    }
}

function distinctRows(rows: Record<string, unknown>[]): Record<string, unknown>[] {
    const seen = new Map<string, Record<string, unknown>>()
    for (const row of rows) {
        const key = [row.date, row.namespace, row.flowId, row.state].join("|")
        if (!seen.has(key)) seen.set(key, row)
    }
    return [...seen.values()]
}

async function fetchRowPages(previewPage: (pageNumber: number) => Promise<ChartPage>): Promise<{results: Record<string, unknown>[]; ignoredFilters: QueryFilterField[]; isCapped: boolean}> {
    const first = await previewPage(1)
    const pageCount = Math.ceil(first.total / MAX_PREVIEW_PAGE_SIZE)
    const ignoredFilters = first.ignoredFilters ?? []
    if (pageCount > MAX_ROWS_PAGES) return {results: first.results, ignoredFilters, isCapped: true}

    const remainingPages = await Promise.all(Array.from({length: pageCount - 1}, (_, i) => previewPage(i + 2)))
    return {results: distinctRows([first, ...remainingPages].flatMap(page => page.results)), ignoredFilters, isCapped: false}
}

export function useExecutionsDensity() {
    const dashboardStore = useDashboardStore()

    async function fetchDensity(query: Record<string, unknown>, startMs: number, endMs: number, nowMs: number = Date.now()): Promise<ExecutionsDensity> {
        const {scopeFilters, plan} = planFor(query, startMs, endMs, nowMs)

        const responses = await Promise.all(plan.requests.map(request => dashboardStore.chartPreview({
            chart: DENSITY_CHART,
            globalFilter: {filters: rangeFilters(scopeFilters, request)},
        })))

        return {
            points: responses.flatMap((response, index) =>
                densityPointsFrom(response.results).filter(point => point.startMs < plan.requests[index].endMs),
            ),
            granularityMs: plan.granularityMs,
            ignoredFilters: [...new Set(responses.flatMap(response => response.ignoredFilters ?? []))],
        }
    }

    async function fetchRowDensity(query: Record<string, unknown>, startMs: number, endMs: number, nowMs: number = Date.now()): Promise<ExecutionsRowDensity> {
        const {scopeFilters, plan} = planFor(query, startMs, endMs, nowMs)

        const responses = await Promise.all(plan.requests.map(request => fetchRowPages(pageNumber => dashboardStore.chartPreview({
            chart: ROWS_CHART,
            globalFilter: {pageNumber, pageSize: MAX_PREVIEW_PAGE_SIZE, filters: rangeFilters(scopeFilters, request)},
        }))))

        return {
            rowPoints: responses.flatMap((response, index) =>
                rowDensityPointsFrom(response.results).filter(point => point.startMs < plan.requests[index].endMs),
            ),
            granularityMs: plan.granularityMs,
            ignoredFilters: [...new Set(responses.flatMap(response => response.ignoredFilters))],
            isCapped: responses.some(response => response.isCapped),
        }
    }

    return {fetchDensity, fetchRowDensity}
}

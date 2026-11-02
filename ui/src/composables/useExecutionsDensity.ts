import type {LocationQuery} from "vue-router"
import {decodeSearchParams} from "@kestra-io/design-system"
import type {QueryFilter, QueryFilterField} from "@kestra-io/kestra-sdk"
import {useDashboardStore} from "../stores/dashboard"
import DENSITY_CHART from "../components/executions/timeline/executions_density_chart.yaml?raw"
import {planDensityRequests, rowDensityPointsFrom, sumDensityPoints, type DensityPoint, type RowDensityPoint} from "../utils/executionsTimeline"

const TIME_FIELDS = new Set<string>(["timeRange", "startDate", "endDate"])
export const MAX_PREVIEW_PAGE_SIZE = 1000
export const MAX_DENSITY_PAGES = 5

export interface ExecutionsDensity {
    points: DensityPoint[];
    rowPoints: RowDensityPoint[];
    isCapped: boolean;
    granularityMs: number;
    ignoredFilters: QueryFilterField[];
}

type ChartPage = Awaited<ReturnType<ReturnType<typeof useDashboardStore>["chartPreview"]>>

interface DensityResponse {
    results: Record<string, unknown>[];
    ignoredFilters: QueryFilterField[];
    isCapped: boolean;
}

async function fetchAllPages(previewPage: (pageNumber: number) => Promise<ChartPage>): Promise<DensityResponse> {
    const first = await previewPage(1)
    const pageCount = Math.ceil(first.total / MAX_PREVIEW_PAGE_SIZE)
    const remainingPages = await Promise.all(
        Array.from({length: Math.min(pageCount, MAX_DENSITY_PAGES) - 1}, (_, i) => previewPage(i + 2)),
    )
    const pages = [first, ...remainingPages]

    return {
        results: pages.flatMap(page => page.results),
        ignoredFilters: first.ignoredFilters ?? [],
        isCapped: pageCount > MAX_DENSITY_PAGES,
    }
}

export function useExecutionsDensity() {
    const dashboardStore = useDashboardStore()

    async function fetchDensity(query: Record<string, unknown>, startMs: number, endMs: number, nowMs: number = Date.now()): Promise<ExecutionsDensity> {
        const scopeFilters = (decodeSearchParams(query as LocationQuery) as QueryFilter[])
            .filter(filter => !TIME_FIELDS.has(String(filter.field)))
        const plan = planDensityRequests(startMs, endMs, nowMs)

        const responses = await Promise.all(plan.requests.map((request) => {
            const previewPage = (pageNumber: number) => dashboardStore.chartPreview({
                chart: DENSITY_CHART,
                globalFilter: {
                    pageNumber,
                    pageSize: MAX_PREVIEW_PAGE_SIZE,
                    filters: [
                        ...scopeFilters,
                        {field: "startDate", operation: "GREATER_THAN_OR_EQUAL_TO", value: new Date(request.startMs).toISOString()},
                        ...(request.lookAheadEndMs === undefined
                            ? []
                            : [{field: "endDate", operation: "LESS_THAN_OR_EQUAL_TO", value: new Date(request.lookAheadEndMs).toISOString()} as QueryFilter]),
                    ],
                },
            })
            return fetchAllPages(previewPage)
        }))

        const rowPoints = responses.flatMap((response, index) =>
            rowDensityPointsFrom(response.results).filter(point => point.startMs < plan.requests[index].endMs),
        )

        return {
            points: sumDensityPoints(rowPoints),
            rowPoints,
            isCapped: responses.some(response => response.isCapped),
            granularityMs: plan.granularityMs,
            ignoredFilters: [...new Set(responses.flatMap(response => response.ignoredFilters ?? []))],
        }
    }

    return {fetchDensity}
}

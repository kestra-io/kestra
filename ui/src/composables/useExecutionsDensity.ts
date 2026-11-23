import type {LocationQuery} from "vue-router"
import {decodeSearchParams} from "@kestra-io/design-system"
import type {QueryFilter, QueryFilterField} from "@kestra-io/kestra-sdk"
import {useDashboardStore} from "../stores/dashboard"
import DENSITY_CHART from "../components/executions/timeline/executions_density_chart.yaml?raw"
import {densityPointsFrom, planDensityRequests, type DensityPoint} from "../utils/executionsTimeline"

const TIME_FIELDS = new Set<string>(["timeRange", "startDate", "endDate"])

export interface ExecutionsDensity {
    points: DensityPoint[];
    granularityMs: number;
    ignoredFilters: QueryFilterField[];
}

export function useExecutionsDensity() {
    const dashboardStore = useDashboardStore()

    async function fetchDensity(query: Record<string, unknown>, startMs: number, endMs: number, nowMs: number = Date.now()): Promise<ExecutionsDensity> {
        const scopeFilters = (decodeSearchParams(query as LocationQuery) as QueryFilter[])
            .filter(filter => !TIME_FIELDS.has(String(filter.field)))
        const plan = planDensityRequests(startMs, endMs, nowMs)

        const responses = await Promise.all(plan.requests.map(request => dashboardStore.chartPreview({
            chart: DENSITY_CHART,
            globalFilter: {
                filters: [
                    ...scopeFilters,
                    {field: "startDate", operation: "GREATER_THAN_OR_EQUAL_TO", value: new Date(request.startMs).toISOString()},
                    ...(request.lookAheadEndMs === undefined
                        ? []
                        : [{field: "endDate", operation: "LESS_THAN_OR_EQUAL_TO", value: new Date(request.lookAheadEndMs).toISOString()} as QueryFilter]),
                ],
            },
        })))

        return {
            points: responses.flatMap((response, index) =>
                densityPointsFrom(response.results).filter(point => point.startMs < plan.requests[index].endMs),
            ),
            granularityMs: plan.granularityMs,
            ignoredFilters: [...new Set(responses.flatMap(response => response.ignoredFilters ?? []))],
        }
    }

    return {fetchDensity}
}

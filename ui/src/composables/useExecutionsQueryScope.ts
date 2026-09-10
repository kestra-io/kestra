import {computed, type ComputedRef} from "vue"
import _merge from "lodash/merge"
import {useRoute} from "vue-router"
import type {FilterConfiguration} from "@kestra-io/design-system"
import {keepSupportedFilters, FILTER_FIELD_PATTERN} from "../components/executions/utils"

export {FILTER_FIELD_PATTERN}

interface ExecutionsQueryScope {
    namespace?: string;
    flowId?: string;
    statuses?: string[];
}

/**
 * Shared execution-filter scoping between the Executions table and the Timeline view: both must
 * apply the same namespace/flow/status scope and drop query params the active filter configuration
 * doesn't support, so the two stay consistent for the same route.
 */
export function useExecutionsQueryScope(
    configuration: ComputedRef<FilterConfiguration>,
    scope: ComputedRef<ExecutionsQueryScope>,
) {
    const route = useRoute()

    const supportedFilterFields = computed<Set<string>>(() => {
        const fields = (configuration.value.keys ?? []).flatMap((entry: {key: string}) =>
            entry.key === "timeRange" ? ["timeRange", "startDate", "endDate"] : [entry.key],
        )
        if (configuration.value.searchPlaceholder) {
            fields.push("q")
        }
        return new Set(fields)
    })

    const dropUnsupportedFilters = (query: Record<string, unknown>): Record<string, unknown> =>
        keepSupportedFilters(query, supportedFilterFields.value)

    const loadQuery = (base: Record<string, unknown>): Record<string, unknown> => {
        const {page: _p, size: _s, sort: _so, ...restQuery} = route.query
        let queryFilter: Record<string, unknown> = dropUnsupportedFilters(restQuery)

        if (scope.value.namespace) {
            queryFilter["filters[namespace][PREFIX]"] = scope.value.namespace
        }

        if (scope.value.flowId) {
            queryFilter["filters[flowId][EQUALS]"] = scope.value.flowId
        }

        const hasStateFilters = Object.keys(queryFilter).some(key => key.startsWith("filters[state]")) || queryFilter.state
        if (!hasStateFilters && (scope.value.statuses?.length ?? 0) > 0) {
            queryFilter["filters[state][IN]"] = scope.value.statuses!.join(",")
        }

        return _merge(base, queryFilter)
    }

    return {supportedFilterFields, dropUnsupportedFilters, loadQuery}
}

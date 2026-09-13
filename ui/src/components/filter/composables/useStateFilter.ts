import {useRoute, useRouter} from "vue-router"
import type {RouteLocationRaw} from "vue-router"

export const STATE_FILTER_KEY = "filters[state][IN]"

export const executionsListFilteredByState = (
    state: string,
    tenant?: string,
    scope: Record<string, string> = {},
): RouteLocationRaw => ({
    name: "executions/list",
    params: tenant ? {tenant} : {},
    query: {[STATE_FILTER_KEY]: state, ...scope},
})

export const useStateFilter = () => {
    const route = useRoute()
    const router = useRouter()

    const filterByState = (state?: string) => {
        if (!state) return
        const {state: _legacyState, page: _page, ...rest} = route.query
        const current = rest[STATE_FILTER_KEY]
        const isOnlyActiveState = Array.isArray(current)
            ? current.length === 1 && current[0] === state
            : current === state
        if (isOnlyActiveState) {
            const {[STATE_FILTER_KEY]: _active, ...without} = rest
            router.push({query: {...without, page: "1"}})
            return
        }
        router.push({query: {...rest, [STATE_FILTER_KEY]: state, page: "1"}})
    }

    const navigateToStateFilter = (state?: string, scope: Record<string, string> = {}) => {
        if (!state) return
        router.push(executionsListFilteredByState(state, route.params.tenant as string, scope))
    }

    return {filterByState, navigateToStateFilter}
}

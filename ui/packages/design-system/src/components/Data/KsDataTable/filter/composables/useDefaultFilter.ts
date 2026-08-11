import {nextTick, onMounted} from "vue"
import {type LocationQuery, type RouteLocationNormalizedLoaded, useRoute, useRouter} from "vue-router"

interface DefaultFilterOptions {
    namespace?: string | null;
    includeTimeRange?: boolean;
    includeScope?: boolean;
    /**
     * Duration from dashboard's timeWindow.default (e.g. "P7D").
     * Falls back to chartDefaultDuration from config endpoint -> then "PT24H".
    **/
    defaultDuration?: string;
}

const NAMESPACE_FILTER_PREFIX = "filters[namespace]"
const SCOPE_FILTER_PREFIX = "filters[scope]"
const TIME_RANGE_FILTER_PREFIX = "filters[timeRange]"
const TIME_FILTER_KEYS = /startDate|endDate|timeRange/

const hasFilterKey = (query: LocationQuery, prefix: string): boolean =>
    Object.keys(query).some(key => key.startsWith(prefix))

function readSavedRestoreState(route: RouteLocationNormalizedLoaded): LocationQuery {
    const {tenant, tab} = route.params
    const key = `${route.name?.toString().replace("/", "_")}${tab ? "_" + tab : ""}${tenant ? "_" + tenant : ""}_restore_url`
    try {
        const raw = window.sessionStorage.getItem(key)
        return raw ? JSON.parse(raw) : {}
    } catch {
        return {}
    }
}

export function defaultNamespace() {
    return localStorage.getItem("defaultNamespace")
}

export function applyDefaultFilters(
    currentQuery?: LocationQuery,
    {
        namespace,
        includeTimeRange,
        includeScope,
        defaultDuration,
    }: DefaultFilterOptions = {}): { query: LocationQuery, change: boolean } {

    const query = {...currentQuery}
    let change = false

    if (namespace !== null && defaultNamespace() && !hasFilterKey(query, NAMESPACE_FILTER_PREFIX)) {
        query[`${NAMESPACE_FILTER_PREFIX}[PREFIX]`] = defaultNamespace()
        change = true
    }

    if (includeScope && !hasFilterKey(query, SCOPE_FILTER_PREFIX)) {
        query[`${SCOPE_FILTER_PREFIX}[EQUALS]`] = "USER"
        change = true
    }

    if (includeTimeRange) {
        const hasExisting = Object.keys(query).some(key => TIME_FILTER_KEYS.test(key))
        if (!hasExisting) {
            const duration = defaultDuration ?? "PT24H"
            query[`${TIME_RANGE_FILTER_PREFIX}[EQUALS]`] = duration
            change = true
        }
    }

    if (!includeScope) {
        Object.keys(query).forEach(key => {
            if (key.startsWith(SCOPE_FILTER_PREFIX)) {
                delete query[key]
                change = true
            }
        })
    }

    return {query, change}
}

export function useDefaultFilter(
    defaultOptions?: DefaultFilterOptions,
) {
    const route = useRoute()
    const router = useRouter()

    onMounted(async () => {
        await nextTick()
        await nextTick()
        // Apply defaults against (saved ∪ current URL) so we only fill keys
        // that neither the URL nor the pending restore covers — letting newly
        // changed defaults (e.g. defaultNamespace from settings) take effect
        // without clobbering keys the user already chose.
        const merged = {...readSavedRestoreState(route), ...route.query}
        const {query, change} = applyDefaultFilters(merged, defaultOptions)
        if(change) {
            router.replace({...route, query})
        }
    })

    function resetDefaultFilter(){
        router.replace({
            ...route,
            query: applyDefaultFilters({}, defaultOptions).query,
        })
    }

    return {
        resetDefaultFilter,
    }
}

import {nextTick, onMounted} from "vue";
import {LocationQuery, useRoute, useRouter} from "vue-router";
import {useMiscStore} from "override/stores/misc";
import {defaultNamespace} from "../../../composables/useNamespaces";

interface DefaultFilterOptions {
    namespace?: string | null;
    includeTimeRange?: boolean;
    includeScope?: boolean;
    legacyQuery?: boolean;
}

const NAMESPACE_FILTER_PREFIX = "filters[namespace]";
const SCOPE_FILTER_PREFIX = "filters[scope]";
const TIME_RANGE_FILTER_PREFIX = "filters[timeRange]";

const hasFilterKey = (query: LocationQuery, prefix: string): boolean =>
    Object.keys(query).some(key => key.startsWith(prefix));

export function applyDefaultFilters(
    currentQuery?: LocationQuery, 
    {
        namespace, 
        includeTimeRange, 
        includeScope, 
        legacyQuery,
    }: DefaultFilterOptions = {}): { query: LocationQuery, change: boolean } {
        
    const query = {...currentQuery};
    let change = false;
   

    if (namespace !== null && defaultNamespace() && !hasFilterKey(query, NAMESPACE_FILTER_PREFIX)) {
        query[legacyQuery ? "namespace" : `${NAMESPACE_FILTER_PREFIX}[PREFIX]`] = defaultNamespace();
        change = true;
    }

    if (includeScope && !hasFilterKey(query, SCOPE_FILTER_PREFIX)) {
        query[legacyQuery ? "scope" : `${SCOPE_FILTER_PREFIX}[EQUALS]`] = "USER";
        change = true;
    }

    const TIME_FILTER_KEYS = /startDate|endDate|timeRange/;

    if (includeTimeRange && !Object.keys(query).some(key => TIME_FILTER_KEYS.test(key))) {
        const defaultDuration = useMiscStore().configs?.chartDefaultDuration ?? "P30D";
        query[legacyQuery ? "timeRange" : `${TIME_RANGE_FILTER_PREFIX}[EQUALS]`] = defaultDuration;
        change = true;
    }

    return {query, change};
}

export function useDefaultFilter(
    defaultOptions?: DefaultFilterOptions,
) {
    const route = useRoute();
    const router = useRouter();

    onMounted(async () => {
        // wait for router to be ready
        await nextTick()
        // wait for the useRestoreUrl to apply its changes
        await nextTick()
        // finally add default filter if necessary
        const {query, change} = applyDefaultFilters(route.query, defaultOptions)
        if(change) {
            router.replace({...route, query})
        }
    });

    function resetDefaultFilter(){
        router.replace({
            ...route,
            query: applyDefaultFilters({}, defaultOptions).query
        });
    }

    return {
        resetDefaultFilter
    }
}   
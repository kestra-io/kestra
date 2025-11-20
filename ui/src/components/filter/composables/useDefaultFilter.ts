import {nextTick, onMounted} from "vue";
import {LocationQuery, RouteLocation, useRoute, useRouter} from "vue-router";
import {useMiscStore} from "override/stores/misc";
import {defaultNamespace} from "../../../composables/useNamespaces";
import {FilterConfiguration} from "../utils/filterTypes";

interface DefaultFilterOptions {
    namespace?: string;
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
        configuration, 
        route, 
        namespace, 
        includeTimeRange, 
        includeScope, 
        legacyQuery,
    }: DefaultFilterOptions & { 
        configuration?: FilterConfiguration; 
        route?: RouteLocation 
    } = {}): { query: LocationQuery, change: boolean } {

    if(currentQuery && Object.keys(currentQuery).length > 0) {
        return {
            query: currentQuery,
            change: false,
        }
    }

    const hasTimeRange = configuration && route 
        ? configuration.keys?.some((k: any) => k.key === "timeRange") ?? false
        : includeTimeRange ?? false;

    const hasScope = configuration && route
        ? route?.name !== "logs/list" && (configuration.keys?.some((k: any) => k.key === "scope") ?? false)
        : includeScope ?? false;
        
    const query = {...currentQuery};
   
    if (namespace === undefined && defaultNamespace() && !hasFilterKey(query, NAMESPACE_FILTER_PREFIX)) {
        query[legacyQuery ? "namespace" : `${NAMESPACE_FILTER_PREFIX}[PREFIX]`] = defaultNamespace();
    }

    if (hasScope && !hasFilterKey(query, SCOPE_FILTER_PREFIX)) {
        query[legacyQuery ? "scope" : `${SCOPE_FILTER_PREFIX}[EQUALS]`] = "USER";
    }

    const TIME_FILTER_KEYS = /startDate|endDate|timeRange/;

    if (hasTimeRange && !Object.keys(query).some(key => TIME_FILTER_KEYS.test(key))) {
        const defaultDuration = useMiscStore().configs?.chartDefaultDuration ?? "P30D";
        query[legacyQuery ? "timeRange" : `${TIME_RANGE_FILTER_PREFIX}[EQUALS]`] = defaultDuration;
    }

    return {query, change: true};
}

export function useDefaultFilter(
    configuration?: FilterConfiguration, 
    legacyQuery?: boolean,
) {
    const route = useRoute();
    const router = useRouter();

    onMounted(async () => {
        // wait for router to be ready
        await nextTick()
        // wait for the useRestoreUrl to apply its changes
        await nextTick()
        // finally add default filter if necessary
        const {query, change} = applyDefaultFilters(route.query, {configuration, route, legacyQuery})
        if(change) {
            router.replace({...route, query})
        }
    });
}   
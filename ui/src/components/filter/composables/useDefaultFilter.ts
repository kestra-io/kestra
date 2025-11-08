import {onMounted} from "vue";
import {LocationQuery, useRoute, useRouter} from "vue-router";
import {useMiscStore} from "override/stores/misc";
import {defaultNamespace} from "../../../composables/useNamespaces";

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
    currentQuery: LocationQuery, 
    options: DefaultFilterOptions & { 
        configuration?: any; 
        route?: any 
    } = {}): { query: LocationQuery; hasChanges: boolean } {
        
    const {configuration, route, namespace, includeTimeRange, includeScope, legacyQuery = false} = options;
    
    const hasTimeRange = configuration && route 
        ? configuration.keys?.some((k: any) => k.key === "timeRange") ?? false
        : includeTimeRange ?? false;
    const hasScope = configuration && route
        ? route?.name !== "logs/list" && (configuration.keys?.some((k: any) => k.key === "scope") ?? false)
        : includeScope ?? false;
        
    const query = {...currentQuery};
    let hasChanges = false;

    if (namespace === undefined && defaultNamespace() && !hasFilterKey(query, NAMESPACE_FILTER_PREFIX)) {
        query[legacyQuery ? "namespace" : `${NAMESPACE_FILTER_PREFIX}[PREFIX]`] = defaultNamespace();
        hasChanges = true;
    }

    if (hasScope && !hasFilterKey(query, SCOPE_FILTER_PREFIX)) {
        query[legacyQuery ? "scope" : `${SCOPE_FILTER_PREFIX}[EQUALS]`] = "USER";
        hasChanges = true;
    }

    const TIME_FILTER_KEYS = /startDate|endDate|timeRange/;

    if (hasTimeRange && !Object.keys(query).some(key => TIME_FILTER_KEYS.test(key))) {
        const defaultDuration = useMiscStore().configs?.chartDefaultDuration ?? "P30D";
        query[legacyQuery ? "timeRange" : `${TIME_RANGE_FILTER_PREFIX}[EQUALS]`] = defaultDuration;
        hasChanges = true;
    }

    return {query, hasChanges};
}

export function useApplyDefaultFilter(options?: DefaultFilterOptions) {
    const router = useRouter();
    const route = useRoute();

    onMounted(() => {
        const {query, hasChanges} = applyDefaultFilters(route.query, options);
        if (hasChanges) {
            router.replace({query});
        }
    });
}
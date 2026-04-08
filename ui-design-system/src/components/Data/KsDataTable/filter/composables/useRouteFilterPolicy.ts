import {computed, ref, watch} from "vue";
import {
    LocationQuery,
    LocationQueryRaw,
    useRoute,
    useRouter
} from "vue-router";
import {AppliedFilter} from "../utils/filterTypes";

type QueryLike = LocationQuery | LocationQueryRaw | Record<string, any>;

interface UseRouteFilterPolicyOptions<T> {
    enabled?: () => boolean;
    explicitValue?: () => T | undefined;
    defaultValue?: () => T | undefined;
    fallbackValue?: () => T | undefined;
    applyDefaultIfMissing?: () => boolean;
    readFromRoute: (query: QueryLike) => T | undefined;
    writeToRoute: (query: Record<string, any>, value: T | undefined) => Record<string, any>;
    hasUnsupportedRouteValue?: (query: QueryLike) => boolean;
    readFromAppliedFilters?: (filters: AppliedFilter[]) => T | undefined;
    shouldSyncFromAppliedFilters?: (filters: AppliedFilter[], routeQuery: Record<string, any>) => boolean;
}
export function useRouteFilterPolicy<T>(options: UseRouteFilterPolicyOptions<T>) {
    const route = useRoute();
    const router = useRouter();
    const normalizedOnce = ref(false);

    const isEnabled = () => options.enabled?.() ?? true;
    const shouldApplyDefaultIfMissing = () => options.applyDefaultIfMissing?.() ?? false;

    const routeValue = computed(() => options.readFromRoute(route.query));
    const explicitValue = computed(() => options.explicitValue?.());
    const hasUnsupportedRouteValue = computed(
        () => options.hasUnsupportedRouteValue?.(route.query) ?? false
    );

    const effectiveValue = computed(() => {
        if (!isEnabled()) {
            return options.fallbackValue?.();
        }

        if (routeValue.value !== undefined) {
            return routeValue.value;
        }

        if (explicitValue.value !== undefined) {
            return explicitValue.value;
        }

        if (!normalizedOnce.value && shouldApplyDefaultIfMissing()) {
            return options.defaultValue?.();
        }

        return options.fallbackValue?.();
    });

    watch(
        [routeValue, explicitValue, hasUnsupportedRouteValue],
        ([routeValueNow, explicitValueNow, hasUnsupportedNow]) => {
            if (normalizedOnce.value || !isEnabled() || !shouldApplyDefaultIfMissing()) {
                return;
            }

            normalizedOnce.value = true;

            if (routeValueNow !== undefined && !hasUnsupportedNow) {
                return;
            }

            const nextValue = routeValueNow ?? explicitValueNow ?? options.defaultValue?.();
            if (nextValue === undefined) {
                return;
            }

            router.replace({
                query: options.writeToRoute(route.query as Record<string, any>, nextValue)
            });
        },
        {immediate: true}
    );

    const setRouteValue = (value: T | undefined) => {
        if (!isEnabled()) {
            return;
        }

        if (value === routeValue.value && !hasUnsupportedRouteValue.value) {
            return;
        }

        router.replace({
            query: options.writeToRoute(route.query as Record<string, any>, value)
        });
    };

    const syncFromAppliedFilters = (filters: AppliedFilter[]) => {
        if (!isEnabled() || !options.readFromAppliedFilters) {
            return;
        }

        if (
            options.shouldSyncFromAppliedFilters &&
            !options.shouldSyncFromAppliedFilters(filters, route.query as Record<string, any>)
        ) {
            return;
        }

        setRouteValue(options.readFromAppliedFilters(filters));
    };

    return {
        routeValue,
        effectiveValue,
        hasUnsupportedRouteValue,
        syncFromAppliedFilters,
        setRouteValue
    };
}

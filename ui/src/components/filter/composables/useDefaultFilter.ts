import {onMounted} from "vue";
import {defaultNamespace} from "../../../composables/useNamespaces";
import {RouteLocation, useRoute, useRouter} from "vue-router";
import {useMiscStore} from "../../../override/stores/misc";

export function useDefaultFilter(props?: { namespace?: string }, query: Record<string, any> = {}) {
    let queryHasChanged = false;
    const queryKeys = Object.keys(query);
    if (props?.namespace === undefined && defaultNamespace() && !queryKeys.some(key => key.startsWith("filters[namespace]"))) {
        query["filters[namespace][PREFIX]"] = defaultNamespace();
        queryHasChanged = true;
    }

    if (!queryKeys.some(key => key.startsWith("filters[scope]"))) {
        query["filters[scope][EQUALS]"] = "USER";
        queryHasChanged = true;
    }

    return {queryHasChanged, query};
}

export function maybeAddTimeRangeFilter(to: RouteLocation) {
    const dateTimeKeys = ["startDate", "endDate", "timeRange"];

    // Default to the configured duration if no time range is set
    if (!Object.keys(to.query).some((key) => dateTimeKeys.some((dateTimeKey) => key.includes(dateTimeKey)))) {
        const miscStore = useMiscStore();
        const defaultDuration = miscStore.configs?.chartDefaultDuration || "P30D"; // Fallback to 30 days
        to.query["filters[timeRange][EQUALS]"] = defaultDuration;

        return true;
    }

    return false;
}

export function useApplyDefaultFilter(props: { namespace?: string }) {
    onMounted(() => {
        const router = useRouter();
        const route = useRoute();
        const query = {...route.query};
    
        const {queryHasChanged} = useDefaultFilter(props, query);

        if (queryHasChanged || maybeAddTimeRangeFilter(route)) {
            router.replace({query});
        }
    });
};


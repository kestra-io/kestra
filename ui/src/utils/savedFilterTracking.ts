import type {SavedFilterAnalyticsEvent} from "@kestra-io/design-system"
import {useApiStore} from "../stores/api"
import {useMiscStore} from "override/stores/misc"

export {SAVED_FILTER_ANALYTICS_INJECTION_KEY} from "@kestra-io/design-system"

export function trackSavedFilter({action, page, filtersCount}: SavedFilterAnalyticsEvent) {
    try {
        if (useMiscStore().configs?.isUiAnonymousUsageEnabled === false) return

        useApiStore().posthogEvents({
            type: "SAVED_FILTER",
            action,
            filter_page: page,
            filters_count: filtersCount,
        })
    } catch {
        //
    }
}

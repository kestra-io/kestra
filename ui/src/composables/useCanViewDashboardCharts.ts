import {computed} from "vue"
import action from "../models/action"
import resource from "../models/resource"
import {useAuthStore} from "override/stores/auth"

/** The stat charts above a list are dashboard chart previews, which the backend refuses without DASHBOARD.VIEW. */
export function useCanViewDashboardCharts(namespace: () => string | null | undefined) {
    const authStore = useAuthStore()
    return computed(() => {
        const ns = namespace()
        return ns
            ? authStore.user?.isAllowed(resource.DASHBOARD, action.VIEW, ns) === true
            : authStore.user?.hasAnyAction(resource.DASHBOARD, action.VIEW) === true
    })
}

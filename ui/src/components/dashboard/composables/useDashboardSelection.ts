import {useRoute, useRouter} from "vue-router"
import {useDashboardStore} from "../../../stores/dashboard"

export function useDashboardSelection() {
    const route = useRoute()
    const router = useRouter()
    const dashboardStore = useDashboardStore()

    const selectDashboard = (value: string) => {
        localStorage.setItem(dashboardStore.getUserDashboardStorageKey(route), value)
        router.replace({params: {...route.params, dashboard: value}})
    }

    return {selectDashboard}
}

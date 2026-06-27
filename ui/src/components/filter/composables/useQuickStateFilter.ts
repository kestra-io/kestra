import {computed} from "vue"
import {useRoute, useRouter} from "vue-router"
import {useComplexFilters} from "./useComplexFilters"
import {STATE_FILTER_KEY} from "./useStateFilter"

export const useQuickStateFilter = () => {
    const route = useRoute()
    const router = useRouter()

    const {hasComplexFilters} = useComplexFilters()

    const quickStates = computed(() => [
        {label: "KILLED", value: "KILLED"},
        {label: "QUEUED", value: "QUEUED"},
        {label: "FAILED", value: "FAILED"},
        {label: "PAUSED", value: "PAUSED"},
        {label: "RUNNING", value: "RUNNING"},
    ])

    const selectedStates = computed<string[]>(() => {
        const raw = route.query[STATE_FILTER_KEY]
        if (!raw) return []
        const joined = Array.isArray(raw) ? raw.join(",") : String(raw)
        return joined.split(",").map((state) => state.trim()).filter(Boolean)
    })

    const onQuickFilterState = (value: string) => {
        const states = new Set(selectedStates.value)
        if (states.has(value)) {
            states.delete(value)
        } else {
            states.add(value)
        }

        const query = {...route.query}
        if (states.size === 0) {
            delete query[STATE_FILTER_KEY]
        } else {
            query[STATE_FILTER_KEY] = Array.from(states).join(",")
        }
        router.replace({query})
    }

    return {quickStates, selectedStates, onQuickFilterState, hasComplexFilters}
}

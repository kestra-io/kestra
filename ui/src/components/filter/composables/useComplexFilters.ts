import {computed} from "vue"
import {useRoute} from "vue-router"
import {decodeSearchParams} from "@kestra-io/design-system"

const GLOBAL_FIELDS = new Set(["timeRange", "startDate", "endDate", "q"])

export const useComplexFilters = () => {
    const route = useRoute()

    const hasComplexFilters = computed(() => {
        const decoded = decodeSearchParams(route.query)
        if (decoded.some((param) => param?.groupIndex !== undefined)) return true
        const fields = new Set(
            decoded
                .filter((param) => param && !GLOBAL_FIELDS.has(param.field))
                .map((param) => param.field),
        )
        return fields.size > 1
    })

    return {hasComplexFilters}
}

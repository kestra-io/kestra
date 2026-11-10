import {computed, type ComputedRef} from "vue"
import {useStorage} from "@vueuse/core"
import {storageKeys} from "../utils/constants"

export function usePageSize(querySize: ComputedRef<unknown>, defaultSize = 25) {
    const savedSize = useStorage(storageKeys.PAGINATION_SIZE, defaultSize, undefined, {writeDefaults: false})
    const pageSize = computed(() => Number(querySize.value) || savedSize.value || defaultSize)

    function rememberPageSize(size: number) {
        savedSize.value = size
    }

    return {pageSize, rememberPageSize}
}

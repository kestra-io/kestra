import {computed, provide, ref} from "vue"
import {POLICY_SUPPLIED_PATHS_INJECTION_KEY} from "../injectionKeys"

interface ValidationResult {
    policySuppliedPaths?: string[]
}

export function usePolicySuppliedPaths() {
    const paths = ref<string[]>([])
    provide(POLICY_SUPPLIED_PATHS_INJECTION_KEY, computed(() => new Set(paths.value)))

    let latestRequest = 0

    function track<T extends ValidationResult | undefined>(
        request: Promise<T>,
        onResult?: (result: T) => void,
        onError?: () => void,
    ) {
        const requestId = ++latestRequest
        request
            .then((result) => {
                if (requestId !== latestRequest) return
                paths.value = result?.policySuppliedPaths ?? []
                onResult?.(result)
            })
            .catch(() => {
                if (requestId === latestRequest) onError?.()
            })
    }

    function reset() {
        latestRequest++
        paths.value = []
    }

    return {track, reset}
}

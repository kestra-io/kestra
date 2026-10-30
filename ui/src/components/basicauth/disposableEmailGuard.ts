import {ref, shallowRef} from "vue"

export type MailChecker = {isValid(email: string): boolean}

/**
 * Gates an address on mailchecker's ~850 kB domain list, fetched on first use so it never
 * blocks the setup screen's first paint.
 */
export function useDisposableEmailGuard(load: () => Promise<MailChecker>) {
    const checker = shallowRef<MailChecker | null>(null)
    const unavailable = ref(false)
    let pending: Promise<MailChecker | null> | null = null

    function ensureLoaded() {
        pending ??= load()
            .then((module) => (checker.value = module))
            // A quality gate rather than a security boundary: a chunk that never arrives must
            // not leave the only setup screen permanently unsubmittable.
            .catch(() => {
                unavailable.value = true
                return null
            })
        return pending
    }

    /** False while the list is still in flight, so a disposable address cannot slip through early. */
    function isAllowed(email: string) {
        if (!email) return false
        if (unavailable.value) return true
        return checker.value?.isValid(email) ?? false
    }

    return {checker, unavailable, ensureLoaded, isAllowed}
}

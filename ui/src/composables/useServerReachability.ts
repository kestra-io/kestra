import {readonly, ref} from "vue"

// A lone failure (a page unload, one 502) clears before this, so the banner does not flash.
export const UNREACHABLE_DELAY = 2_000

const unreachable = ref(false)
let pending: ReturnType<typeof setTimeout> | undefined

export function markServerUnreachable() {
    pending ??= setTimeout(() => {
        pending = undefined
        unreachable.value = true
    }, UNREACHABLE_DELAY)
}

export function markServerReachable() {
    clearTimeout(pending)
    pending = undefined
    unreachable.value = false
}

export function useServerReachability() {
    return {unreachable: readonly(unreachable)}
}

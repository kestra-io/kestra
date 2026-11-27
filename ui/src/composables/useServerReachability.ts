import {readonly, ref} from "vue"
import {baseUrl} from "override/utils/route"

// A lone failure (a page unload, one 502) clears before this, so the banner does not flash.
export const UNREACHABLE_DELAY = 2_000
export const PROBE_INTERVAL = 10_000

const unreachable = ref(false)
let pending: ReturnType<typeof setTimeout> | undefined
let probing: ReturnType<typeof setInterval> | undefined

// While the banner is up the page may send nothing on its own, so the server is asked directly.
async function probe() {
    try {
        const response = await fetch(`${baseUrl}/ping`, {cache: "no-store"})
        // A gateway answering in the server's place replies with a 5xx.
        if (response.status < 500) markServerReachable()
    } catch {
        return
    }
}

function startProbing() {
    if (probing) return
    probing = setInterval(probe, PROBE_INTERVAL)
    window.addEventListener("online", probe)
}

function stopProbing() {
    clearInterval(probing)
    probing = undefined
    window.removeEventListener("online", probe)
}

export function markServerUnreachable() {
    pending ??= setTimeout(() => {
        pending = undefined
        unreachable.value = true
        startProbing()
    }, UNREACHABLE_DELAY)
}

export function markServerReachable() {
    clearTimeout(pending)
    pending = undefined
    stopProbing()
    unreachable.value = false
}

export function useServerReachability() {
    return {unreachable: readonly(unreachable)}
}

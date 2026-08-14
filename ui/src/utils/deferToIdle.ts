/**
 * Runs `callback` once the browser has drained the work already queued - in practice, once
 * the view that scheduled it has mounted, evaluated its modules and painted.
 *
 * Meant for decoration that is fetched on mount but that nothing on screen waits for:
 * counters, badges, previews. Fetching those in `onMounted` puts their requests in the same
 * tick as the view's own boot, where they compete for the browser's per-origin connection
 * budget with the modules and API calls the view actually needs in order to paint - visible
 * in the flow editor, whose toolbar fires half a dozen counter requests while Monaco, the
 * topology and the plugin schemas are still loading over the same origin.
 *
 * Returns a cancel function so callers can drop a still-pending callback on unmount, or
 * collapse a burst of triggers into the last one.
 */
export function deferToIdle(callback: () => void, timeout = 2000): () => void {
    // Safari only shipped requestIdleCallback in 16.4, and it is absent under jsdom.
    if (typeof requestIdleCallback !== "function") {
        const handle = setTimeout(callback, 0)
        return () => clearTimeout(handle)
    }

    const handle = requestIdleCallback(callback, {timeout})
    return () => cancelIdleCallback(handle)
}

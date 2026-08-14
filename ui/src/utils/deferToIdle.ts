/** Runs `callback` once the browser is idle, returning a cancel function for pending callbacks. */
export function deferToIdle(callback: () => void, timeout = 2000): () => void {
    // Safari only shipped requestIdleCallback in 16.4, and it is absent under jsdom.
    if (typeof requestIdleCallback !== "function") {
        const handle = setTimeout(callback, 0)
        return () => clearTimeout(handle)
    }

    const handle = requestIdleCallback(callback, {timeout})
    return () => cancelIdleCallback(handle)
}

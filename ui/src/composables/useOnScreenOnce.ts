import {onBeforeUnmount, ref, watch, type Ref} from "vue"

/** Roughly the distance ahead of the viewport at which browsers start fetching a `loading="lazy"` image. */
const DEFAULT_MARGIN = "200px 0px"

/**
 * Reports whether an element has come within `rootMargin` of the viewport, latching to `true` on the first
 * intersection so the content it gates is never torn down again once loaded.
 *
 * Answers `true` from the start where `IntersectionObserver` is unavailable (jsdom, older engines), so a missing
 * API degrades to eager loading rather than to content that never appears.
 */
export function useOnScreenOnce(target: Ref<Element | null | undefined>, rootMargin: string = DEFAULT_MARGIN): Ref<boolean> {
    const isOnScreen = ref(typeof IntersectionObserver === "undefined")
    let observer: IntersectionObserver | undefined

    const disconnect = () => {
        observer?.disconnect()
        observer = undefined
    }

    watch(target, element => {
        disconnect()

        if (!element || isOnScreen.value) {
            return
        }

        observer = new IntersectionObserver(entries => {
            if (!entries.some(entry => entry.isIntersecting)) {
                return
            }

            isOnScreen.value = true
            disconnect()
        }, {rootMargin})

        observer.observe(element)
    }, {immediate: true, flush: "post"})

    onBeforeUnmount(disconnect)

    return isOnScreen
}

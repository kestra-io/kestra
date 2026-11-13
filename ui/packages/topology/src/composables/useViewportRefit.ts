import {onMounted, onUnmounted} from "vue"
import type {Ref} from "vue"
import type {VueFlowStore} from "@vue-flow/core"
import {debounce} from "@kestra-io/design-system"

export const REFIT_DELAY_MS = 100

interface ViewportRefitOptions {
    container: Ref<HTMLElement | undefined>
    vueFlow: Pick<VueFlowStore, "fitView" | "onMoveStart">
    /** Whether the graph has had its first fit, before which a resize has nothing to refit. */
    isReady: () => boolean
    /** Whether the starting viewport was set by the caller, so it must survive a resize too. */
    startsAdjusted: boolean
}

/**
 * Refits the graph to its container after a resize, unless the user panned or zoomed since the
 * last fit: refitting on every resize discarded their framing (#19387).
 *
 * @returns `fitGraph`, which every full fit must go through so that resizes refit again after it,
 *          and `zoomByUser`, for zoom controls that move the view without a user gesture.
 */
export function useViewportRefit({container, vueFlow, isReady, startsAdjusted}: ViewportRefitOptions) {
    let adjustedByUser = startsAdjusted
    let containerSize: {width: number, height: number} | undefined
    let resizeObserver: ResizeObserver | undefined

    vueFlow.onMoveStart(() => {
        adjustedByUser = true
    })

    function fitGraph() {
        adjustedByUser = false
        vueFlow.fitView()
    }

    function zoomByUser(zoom: () => Promise<boolean>) {
        adjustedByUser = true
        zoom()
    }

    const refitAfterResize = debounce(() => {
        if (isReady() && !adjustedByUser) fitGraph()
    }, REFIT_DELAY_MS)

    onMounted(() => {
        if (!container.value) return
        resizeObserver = new ResizeObserver(([entry]) => {
            const {width, height} = entry.contentRect
            // A hidden panel measures 0, and fitting the graph into it would leave it unreadable once shown again.
            if (!width || !height) return
            const resized = containerSize !== undefined && (containerSize.width !== width || containerSize.height !== height)
            containerSize = {width, height}
            if (resized) refitAfterResize()
        })
        resizeObserver.observe(container.value)
    })

    onUnmounted(() => {
        resizeObserver?.disconnect()
        refitAfterResize.cancel()
    })

    return {fitGraph, zoomByUser}
}

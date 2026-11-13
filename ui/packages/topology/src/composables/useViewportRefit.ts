import {onMounted, onUnmounted} from "vue"
import type {Ref} from "vue"
import type {ViewportTransform, VueFlowStore} from "@vue-flow/core"
import {debounce} from "@kestra-io/design-system"

export const REFIT_DELAY_MS = 100

/**
 * Refits the graph to its container after a resize, as long as the view is still the one the last
 * fit produced. Any other view was set on purpose (a pan, a zoom, a host focusing one task) and
 * refitting would discard it (#19387). Before the first fit there is no view of ours to keep up.
 *
 * @returns `fitGraph`, which every full fit must go through for resizes to refit after it.
 */
export function useViewportRefit(container: Ref<HTMLElement | undefined>, vueFlow: Pick<VueFlowStore, "fitView" | "viewport">) {
    let fittedView: ViewportTransform | undefined
    let containerSize: {width: number, height: number} | undefined
    let resizeObserver: ResizeObserver | undefined

    async function fitGraph() {
        await vueFlow.fitView()
        fittedView = {...vueFlow.viewport.value}
    }

    function isFittedView() {
        if (!fittedView) return false
        const {x, y, zoom} = vueFlow.viewport.value
        return Math.abs(x - fittedView.x) < 1
            && Math.abs(y - fittedView.y) < 1
            && Math.abs(zoom - fittedView.zoom) < 0.001
    }

    const refitAfterResize = debounce(() => {
        if (isFittedView()) fitGraph()
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

    return {fitGraph}
}

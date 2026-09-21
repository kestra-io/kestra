import {onBeforeUnmount, ref, type ComponentPublicInstance} from "vue"

/** Charts mount once their block is this close to the viewport. */
export const ACTIVATION_MARGIN = "200px 0px"

/**
 * Recyclable charts stay mounted until their block is this far outside the viewport. The gap between this and
 * ACTIVATION_MARGIN is the hysteresis that keeps a small scroll from unmounting what the user just looked at.
 */
export const RETENTION_MARGIN = "1200px 0px"

/** How many charts may mount per frame, so a tall viewport does not mount its whole first screen in a single long task. */
export const ACTIVATIONS_PER_FRAME = 4

/**
 * Mounts dashboard charts as their block approaches the viewport, and unmounts the recyclable ones again once the
 * block is far behind, so a long dashboard holds a bounded number of live charts instead of one per chart.
 *
 * @param isRecyclable decides which charts may be unmounted after activation; the rest stay mounted for good.
 */
export function useLazyChartBlocks(isRecyclable: (chartId: string) => boolean) {
    const activatedCharts = ref(new Set<string>())

    // Height each chart had when it was recycled, so its placeholder holds the block open and content below does not jump.
    const recycledHeights = ref(new Map<string, number>())

    const chartIdsByBlock = new WeakMap<Element, string>()
    const blocksByChartId = new Map<string, Element>()
    const pendingActivations: string[] = []
    let activationFrame: number | undefined
    // Guards on its own flag rather than on the handle, so a callback that fires before requestAnimationFrame returns
    // cannot leave a stale handle behind and wedge the queue.
    let activationScheduled = false

    function scheduleActivations() {
        if (activationScheduled) return

        activationScheduled = true
        activationFrame = requestAnimationFrame(() => {
            activationScheduled = false
            pendingActivations.splice(0, ACTIVATIONS_PER_FRAME).forEach((chartId) => activatedCharts.value.add(chartId))
            if (pendingActivations.length) scheduleActivations()
        })
    }

    function queueActivation(chartId: string) {
        if (activatedCharts.value.has(chartId) || pendingActivations.includes(chartId)) return

        pendingActivations.push(chartId)
        scheduleActivations()
    }

    function recycle(chartId: string) {
        const queued = pendingActivations.indexOf(chartId)
        if (queued !== -1) pendingActivations.splice(queued, 1)

        if (!activatedCharts.value.has(chartId) || !isRecyclable(chartId)) return

        const height = blocksByChartId.get(chartId)?.getBoundingClientRect().height
        if (height) recycledHeights.value.set(chartId, height)

        activatedCharts.value.delete(chartId)
    }

    function createObserver(rootMargin: string, onEntry: (chartId: string, isIntersecting: boolean) => void) {
        if (typeof IntersectionObserver === "undefined") return undefined

        return new IntersectionObserver((entries) => {
            for (const entry of entries) {
                const chartId = chartIdsByBlock.get(entry.target)
                if (chartId) onEntry(chartId, entry.isIntersecting)
            }
        }, {rootMargin})
    }

    const activationObserver = createObserver(ACTIVATION_MARGIN, (chartId, isIntersecting) => {
        if (isIntersecting) queueActivation(chartId)
    })

    const retentionObserver = createObserver(RETENTION_MARGIN, (chartId, isIntersecting) => {
        if (!isIntersecting) recycle(chartId)
    })

    function forget(chartId: string) {
        const block = blocksByChartId.get(chartId)
        if (!block) return

        activationObserver?.unobserve(block)
        retentionObserver?.unobserve(block)
        blocksByChartId.delete(chartId)
    }

    function observeChartBlock(el: Element | ComponentPublicInstance | null, chartId: string) {
        // Vue calls a function ref with null when the block unmounts; the block may also already be replaced by a new one.
        if (!(el instanceof Element)) {
            if (!blocksByChartId.get(chartId)?.isConnected) forget(chartId)
            return
        }

        // environments without IntersectionObserver (e.g. jsdom) load every chart eagerly
        if (!activationObserver || !retentionObserver) {
            activatedCharts.value.add(chartId)
            return
        }

        if (blocksByChartId.get(chartId) === el) return
        forget(chartId)

        chartIdsByBlock.set(el, chartId)
        blocksByChartId.set(chartId, el)
        activationObserver.observe(el)
        retentionObserver.observe(el)
    }

    onBeforeUnmount(() => {
        if (activationFrame !== undefined) cancelAnimationFrame(activationFrame)
        activationObserver?.disconnect()
        retentionObserver?.disconnect()
    })

    return {
        activatedCharts,
        observeChartBlock,
        placeholderHeight: (chartId: string) => recycledHeights.value.get(chartId),
    }
}

import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h, nextTick, ref} from "vue"
import type {ViewportTransform, VueFlowStore} from "@vue-flow/core"
import {mount} from "@vue/test-utils"
import {REFIT_DELAY_MS, useViewportRefit} from "./useViewportRefit"

const FITTED_VIEW: ViewportTransform = {x: 10, y: 20, zoom: 0.8}

let resize: (width: number, height: number) => void

function mountRefit() {
    const viewport = ref<ViewportTransform>({x: 0, y: 0, zoom: 1})
    const fitView = vi.fn(async () => {
        viewport.value = {...FITTED_VIEW}
        return true
    })
    let refit!: ReturnType<typeof useViewportRefit>

    const wrapper = mount(defineComponent({
        setup() {
            const container = ref<HTMLElement>()
            refit = useViewportRefit(container, {fitView, viewport} as unknown as Pick<VueFlowStore, "fitView" | "viewport">)
            return () => h("div", {ref: container})
        },
    }))
    resize(800, 600)

    return {wrapper, fitView, viewport, refit}
}

async function mountFitted() {
    const mounted = mountRefit()
    await mounted.refit.fitGraph()
    mounted.fitView.mockClear()
    return mounted
}

async function resizeAndSettle(width: number, height: number) {
    resize(width, height)
    vi.advanceTimersByTime(REFIT_DELAY_MS)
    await nextTick()
}

describe("useViewportRefit", () => {
    beforeEach(() => {
        vi.useFakeTimers()
        vi.stubGlobal("ResizeObserver", class {
            constructor(callback: ResizeObserverCallback) {
                resize = (width, height) => callback([{contentRect: {width, height}} as ResizeObserverEntry], this as unknown as ResizeObserver)
            }
            observe() {}
            unobserve() {}
            disconnect() {}
        })
    })

    afterEach(() => {
        vi.useRealTimers()
        vi.unstubAllGlobals()
    })

    it("should refit once the container settles on a new size", async () => {
        const {fitView} = await mountFitted()

        resize(600, 600)
        resize(500, 600)
        expect(fitView).not.toHaveBeenCalled()
        await resizeAndSettle(400, 600)

        expect(fitView).toHaveBeenCalledTimes(1)
    })

    it("should not refit on the first measure of the container", async () => {
        const {fitView} = await mountFitted()

        vi.advanceTimersByTime(REFIT_DELAY_MS)

        expect(fitView).not.toHaveBeenCalled()
    })

    it("should not refit before the first fit", async () => {
        const {fitView} = mountRefit()

        await resizeAndSettle(500, 600)

        expect(fitView).not.toHaveBeenCalled()
    })

    it("should keep a view moved since the last fit, by the user or by a task focus", async () => {
        const {fitView, viewport} = await mountFitted()

        viewport.value = {x: 300, y: -120, zoom: 1.5}
        await resizeAndSettle(500, 600)

        expect(fitView).not.toHaveBeenCalled()
    })

    it("should refit on resize again after a full fit", async () => {
        const {fitView, viewport, refit} = await mountFitted()

        viewport.value = {x: 300, y: -120, zoom: 1.5}
        await refit.fitGraph()
        await resizeAndSettle(500, 600)

        expect(fitView).toHaveBeenCalledTimes(2)
    })

    it("should not refit while the container is hidden", async () => {
        const {fitView} = await mountFitted()

        await resizeAndSettle(0, 0)

        expect(fitView).not.toHaveBeenCalled()
    })

    it("should drop a pending refit once unmounted", async () => {
        const {wrapper, fitView} = await mountFitted()

        resize(500, 600)
        wrapper.unmount()
        vi.advanceTimersByTime(REFIT_DELAY_MS)

        expect(fitView).not.toHaveBeenCalled()
    })
})

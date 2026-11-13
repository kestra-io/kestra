import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h, ref} from "vue"
import {mount} from "@vue/test-utils"
import {REFIT_DELAY_MS, useViewportRefit} from "./useViewportRefit"

let resize: (width: number, height: number) => void

function mountRefit({ready = true, startsAdjusted = false} = {}) {
    const fitView = vi.fn().mockResolvedValue(true)
    let moveStart = () => {}
    let refit!: ReturnType<typeof useViewportRefit>

    const wrapper = mount(defineComponent({
        setup() {
            const container = ref<HTMLElement>()
            refit = useViewportRefit({
                container,
                vueFlow: {fitView, onMoveStart: (handler: () => void) => {moveStart = handler}} as never,
                isReady: () => ready,
                startsAdjusted,
            })
            return () => h("div", {ref: container})
        },
    }))
    resize(800, 600)

    return {wrapper, fitView, refit, userMoves: () => moveStart()}
}

function resizeAndSettle(width: number, height: number) {
    resize(width, height)
    vi.advanceTimersByTime(REFIT_DELAY_MS)
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

    it("should refit once the container settles on a new size", () => {
        const {fitView} = mountRefit()

        resize(600, 600)
        resize(500, 600)
        expect(fitView).not.toHaveBeenCalled()
        vi.advanceTimersByTime(REFIT_DELAY_MS)

        expect(fitView).toHaveBeenCalledTimes(1)
    })

    it("should not refit on the first measure of the container", () => {
        const {fitView} = mountRefit()

        vi.advanceTimersByTime(REFIT_DELAY_MS)

        expect(fitView).not.toHaveBeenCalled()
    })

    it("should keep a view the user panned or zoomed with a gesture", () => {
        const {fitView, userMoves} = mountRefit()

        userMoves()
        resizeAndSettle(500, 600)

        expect(fitView).not.toHaveBeenCalled()
    })

    it("should keep a view the user zoomed with a control button", () => {
        const {fitView, refit} = mountRefit()
        const zoomIn = vi.fn().mockResolvedValue(true)

        refit.zoomByUser(zoomIn)
        resizeAndSettle(500, 600)

        expect(zoomIn).toHaveBeenCalledTimes(1)
        expect(fitView).not.toHaveBeenCalled()
    })

    it("should refit on resize again after a full fit", () => {
        const {fitView, refit, userMoves} = mountRefit()

        userMoves()
        refit.fitGraph()
        resizeAndSettle(500, 600)

        expect(fitView).toHaveBeenCalledTimes(2)
    })

    it("should keep a starting viewport set by the caller", () => {
        const {fitView} = mountRefit({startsAdjusted: true})

        resizeAndSettle(500, 600)

        expect(fitView).not.toHaveBeenCalled()
    })

    it("should not refit before the graph had its first fit", () => {
        const {fitView} = mountRefit({ready: false})

        resizeAndSettle(500, 600)

        expect(fitView).not.toHaveBeenCalled()
    })

    it("should not refit while the container is hidden", () => {
        const {fitView} = mountRefit()

        resizeAndSettle(0, 0)

        expect(fitView).not.toHaveBeenCalled()
    })

    it("should drop a pending refit once unmounted", () => {
        const {wrapper, fitView} = mountRefit()

        resize(500, 600)
        wrapper.unmount()
        vi.advanceTimersByTime(REFIT_DELAY_MS)

        expect(fitView).not.toHaveBeenCalled()
    })
})

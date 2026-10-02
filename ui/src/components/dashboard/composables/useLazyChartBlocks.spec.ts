import {afterAll, beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h, nextTick} from "vue"
import {mount} from "@vue/test-utils"

import {ACTIVATIONS_PER_FRAME, useLazyChartBlocks} from "./useLazyChartBlocks"

type ObserverCallback = (entries: {target: Element; isIntersecting: boolean}[]) => void

interface FakeObserver {
    rootMargin: string
    callback: ObserverCallback
    observed: Set<Element>
}

const observers: FakeObserver[] = []

/** The activation observer is created first, the retention one second. */
const activation = () => observers[0]
const retention = () => observers[1]

function report(observer: FakeObserver, target: Element, isIntersecting: boolean) {
    observer.callback([{target, isIntersecting}])
}

class FakeIntersectionObserver {
    constructor(callback: ObserverCallback, options: {rootMargin: string}) {
        observers.push({rootMargin: options.rootMargin, callback, observed: new Set()})
        this.index = observers.length - 1
    }

    private index: number

    observe(target: Element) {
        observers[this.index].observed.add(target)
    }

    unobserve(target: Element) {
        observers[this.index].observed.delete(target)
    }

    disconnect() {
        observers[this.index].observed.clear()
    }
}

/**
 * Mounts a host that registers `count` blocks, so the composable runs with a real component instance (it registers an
 * onBeforeUnmount hook) and real elements to hand to the observers.
 */
function mountBlocks(count: number, isRecyclable: (chartId: string) => boolean = () => true) {
    const api: {value?: ReturnType<typeof useLazyChartBlocks>} = {}

    const wrapper = mount(defineComponent({
        setup() {
            const lazy = useLazyChartBlocks(isRecyclable)
            api.value = lazy

            return () => h("div", Array.from({length: count}, (_, index) => h("div", {
                key: `chart-${index}`,
                ref: (el) => lazy.observeChartBlock(el as Element, `chart-${index}`),
            })))
        },
    }), {attachTo: document.body})

    const blocks = wrapper.element.querySelectorAll("div")

    return {wrapper, lazy: api.value!, block: (index: number) => blocks[index]}
}

describe("useLazyChartBlocks", () => {
    beforeEach(() => {
        observers.length = 0
        vi.stubGlobal("IntersectionObserver", FakeIntersectionObserver)
        // Run queued activations synchronously so tests do not depend on frame timing.
        vi.stubGlobal("requestAnimationFrame", (callback: FrameRequestCallback) => {
            callback(0)
            return 1
        })
        vi.stubGlobal("cancelAnimationFrame", vi.fn())
    })

    afterAll(() => {
        vi.unstubAllGlobals()
    })

    it("should observe every block with both an activation and a wider retention margin", () => {
        const {block} = mountBlocks(2)

        expect(observers).toHaveLength(2)
        expect(activation().rootMargin).toBe("200px 0px")
        expect(retention().rootMargin).toBe("1200px 0px")
        expect(activation().observed.has(block(0))).toBe(true)
        expect(retention().observed.has(block(0))).toBe(true)
    })

    it("should not activate any chart before its block approaches the viewport", () => {
        const {lazy} = mountBlocks(3)

        expect(lazy.activatedCharts.value.size).toBe(0)
    })

    it("should activate a chart once its block enters the activation band", async () => {
        const {lazy, block} = mountBlocks(2)

        report(activation(), block(0), true)
        await nextTick()

        expect(lazy.activatedCharts.value.has("chart-0")).toBe(true)
        expect(lazy.activatedCharts.value.has("chart-1")).toBe(false)
    })

    it("should mount at most ACTIVATIONS_PER_FRAME charts per frame", () => {
        const total = ACTIVATIONS_PER_FRAME + 3
        const pending: FrameRequestCallback[] = []
        vi.stubGlobal("requestAnimationFrame", (callback: FrameRequestCallback) => {
            pending.push(callback)
            return pending.length
        })

        const {lazy, block} = mountBlocks(total)
        for (let index = 0; index < total; index++) report(activation(), block(index), true)

        expect(lazy.activatedCharts.value.size).toBe(0)

        pending.shift()!(0)
        expect(lazy.activatedCharts.value.size).toBe(ACTIVATIONS_PER_FRAME)

        pending.shift()!(0)
        expect(lazy.activatedCharts.value.size).toBe(total)
    })

    it("should unmount a recyclable chart once its block leaves the retention band", () => {
        const {lazy, block} = mountBlocks(1)

        report(activation(), block(0), true)
        expect(lazy.activatedCharts.value.has("chart-0")).toBe(true)

        report(retention(), block(0), false)
        expect(lazy.activatedCharts.value.has("chart-0")).toBe(false)
    })

    it("should keep a chart mounted when it leaves the activation band but stays within retention", () => {
        const {lazy, block} = mountBlocks(1)

        report(activation(), block(0), true)
        report(activation(), block(0), false)

        expect(lazy.activatedCharts.value.has("chart-0")).toBe(true)
    })

    it("should never unmount a chart the caller marks as not recyclable", () => {
        const {lazy, block} = mountBlocks(1, () => false)

        report(activation(), block(0), true)
        report(retention(), block(0), false)

        expect(lazy.activatedCharts.value.has("chart-0")).toBe(true)
    })

    it("should drop a queued activation when the block scrolls out of retention before it mounts", () => {
        const pending: FrameRequestCallback[] = []
        vi.stubGlobal("requestAnimationFrame", (callback: FrameRequestCallback) => {
            pending.push(callback)
            return pending.length
        })

        const {lazy, block} = mountBlocks(1)
        report(activation(), block(0), true)
        report(retention(), block(0), false)

        pending.shift()!(0)

        expect(lazy.activatedCharts.value.size).toBe(0)
    })

    it("should re-activate a recycled chart when its block comes back into view", () => {
        const {lazy, block} = mountBlocks(1)

        report(activation(), block(0), true)
        report(retention(), block(0), false)
        report(activation(), block(0), true)

        expect(lazy.activatedCharts.value.has("chart-0")).toBe(true)
    })

    it("should report the height a recycled chart had so its placeholder can hold the block open", () => {
        const {lazy, block} = mountBlocks(1)
        vi.spyOn(block(0), "getBoundingClientRect").mockReturnValue({height: 420} as DOMRect)

        report(activation(), block(0), true)
        expect(lazy.placeholderHeight("chart-0")).toBeUndefined()

        report(retention(), block(0), false)
        expect(lazy.placeholderHeight("chart-0")).toBe(420)
    })

    it("should activate every chart eagerly when IntersectionObserver is unavailable", () => {
        vi.stubGlobal("IntersectionObserver", undefined)

        const {lazy} = mountBlocks(3)

        expect(lazy.activatedCharts.value.size).toBe(3)
    })

    it("should stop observing when the host unmounts", () => {
        const {wrapper, block} = mountBlocks(1)

        expect(activation().observed.has(block(0))).toBe(true)
        wrapper.unmount()

        expect(activation().observed.size).toBe(0)
        expect(retention().observed.size).toBe(0)
    })
})

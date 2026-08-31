import {afterEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h, nextTick, ref} from "vue"
import {mount} from "@vue/test-utils"

import {useOnScreenOnce} from "./useOnScreenOnce"

type ObserverCallback = (entries: {target: Element; isIntersecting: boolean}[]) => void

const observers: {callback: ObserverCallback; observed: Set<Element>}[] = []

class FakeIntersectionObserver {
    private readonly index: number

    constructor(callback: ObserverCallback) {
        observers.push({callback, observed: new Set()})
        this.index = observers.length - 1
    }

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

function mountHost() {
    const isOnScreen = ref<boolean>()

    const wrapper = mount(defineComponent({
        setup() {
            const target = ref<HTMLElement>()
            const state = useOnScreenOnce(target)

            return () => {
                isOnScreen.value = state.value
                return h("div", {ref: target})
            }
        },
    }), {attachTo: document.body})

    return {wrapper, isOnScreen}
}

afterEach(() => {
    observers.length = 0
    vi.unstubAllGlobals()
})

describe("useOnScreenOnce", () => {
    it("should stay false until the observed element intersects", async () => {
        vi.stubGlobal("IntersectionObserver", FakeIntersectionObserver)

        const {wrapper, isOnScreen} = mountHost()
        await nextTick()

        expect(isOnScreen.value).toBe(false)

        const observer = observers.at(-1)!
        observer.callback([{target: wrapper.element, isIntersecting: true}])
        await nextTick()

        expect(isOnScreen.value).toBe(true)
    })

    it("should stop observing once the element has been on screen", async () => {
        vi.stubGlobal("IntersectionObserver", FakeIntersectionObserver)

        const {wrapper} = mountHost()
        await nextTick()

        const observer = observers.at(-1)!
        expect(observer.observed.size).toBe(1)

        observer.callback([{target: wrapper.element, isIntersecting: true}])

        expect(observer.observed.size).toBe(0)
    })

    it("should report true from the start when IntersectionObserver is unavailable", async () => {
        vi.stubGlobal("IntersectionObserver", undefined)

        const {isOnScreen} = mountHost()
        await nextTick()

        expect(isOnScreen.value).toBe(true)
        expect(observers).toHaveLength(0)
    })
})

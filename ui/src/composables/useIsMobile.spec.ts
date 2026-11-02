import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {type EffectScope, effectScope} from "vue"
import {MOBILE_QUERY, useIsMobile} from "./useIsMobile"

type ChangeListener = (event: {matches: boolean}) => void

// `breakpointsElement` puts `sm` at 768px, so the viewport is mobile up to and including 767px.
const SM_BREAKPOINT = 768

const mediaQueryLists: Array<{matches: boolean, listeners: Set<ChangeListener>, query: string}> = []
let viewportWidth = 1280

// Only evaluates the `min-width` / `max-width` conditions in px, which is all the breakpoints emit.
const matchesAt = (query: string, width: number) => {
    const min = query.match(/\(\s*min-width:\s*([\d.]+)px\s*\)/)
    const max = query.match(/\(\s*max-width:\s*([\d.]+)px\s*\)/)

    return (!min || width >= Number(min[1])) && (!max || width <= Number(max[1]))
}

const stubMatchMedia = () => vi.stubGlobal("matchMedia", (query: string) => {
    const list = {
        query,
        matches: matchesAt(query, viewportWidth),
        listeners: new Set<ChangeListener>(),
        media: query,
        addEventListener: (_type: string, listener: ChangeListener) => list.listeners.add(listener),
        removeEventListener: (_type: string, listener: ChangeListener) => list.listeners.delete(listener),
        addListener: (listener: ChangeListener) => list.listeners.add(listener),
        removeListener: (listener: ChangeListener) => list.listeners.delete(listener),
    }
    mediaQueryLists.push(list)

    return list
})

// Moves the viewport and notifies every live media query list the way the browser does.
const resizeViewport = (width: number) => {
    viewportWidth = width
    for (const list of mediaQueryLists) {
        const matches = matchesAt(list.query, width)
        if (matches === list.matches) continue

        list.matches = matches
        list.listeners.forEach((listener) => listener({matches}))
    }
}

describe("useIsMobile", () => {
    let scope: EffectScope
    const callUseIsMobile = () => scope.run(() => useIsMobile())!

    beforeEach(() => {
        scope = effectScope()
        viewportWidth = 1280
        mediaQueryLists.length = 0
        stubMatchMedia()
    })

    afterEach(() => {
        scope.stop()
        vi.unstubAllGlobals()
    })

    it.each([0, 320, 767])("reports true below the sm breakpoint (%ipx)", (width: number) => {
        viewportWidth = width

        expect(callUseIsMobile().value).toBe(true)
    })

    it.each([SM_BREAKPOINT, 1024, 1920])("reports false at or above the sm breakpoint (%ipx)", (width: number) => {
        viewportWidth = width

        expect(callUseIsMobile().value).toBe(false)
    })

    it("reacts to a viewport change instead of reading the width once at setup", () => {
        const isMobile = callUseIsMobile()
        expect(isMobile.value).toBe(false)

        resizeViewport(500)
        expect(isMobile.value).toBe(true)

        resizeViewport(SM_BREAKPOINT)
        expect(isMobile.value).toBe(false)

        resizeViewport(SM_BREAKPOINT - 1)
        expect(isMobile.value).toBe(true)
    })

    it("flips at the same width as MOBILE_QUERY", () => {
        const isMobile = callUseIsMobile()

        for (const width of [320, SM_BREAKPOINT - 1, SM_BREAKPOINT, 1280]) {
            resizeViewport(width)

            expect(isMobile.value).toBe(matchesAt(MOBILE_QUERY, width))
        }
    })

    it("gives two callers the same value before and after a viewport change", () => {
        viewportWidth = 500
        const first = callUseIsMobile()
        const second = callUseIsMobile()

        expect(first.value).toBe(true)
        expect(second.value).toBe(first.value)

        resizeViewport(1280)

        expect(first.value).toBe(false)
        expect(second.value).toBe(first.value)
    })
})

import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {MOBILE_QUERY, useIsMobile} from "./useIsMobile"

let width = 1024
let listeners: (() => void)[] = []

const isWithin = (media: string) => width <= Number(/max-width:\s*([\d.]+)px/.exec(media)?.[1])

function resizeTo(newWidth: number) {
    width = newWidth
    listeners.forEach(notify => notify())
}

describe("useIsMobile", () => {
    beforeEach(() => {
        width = 1024
        listeners = []
        vi.stubGlobal("matchMedia", (media: string) => ({
            get matches() { return isWithin(media) },
            media,
            addEventListener: (_: string, listener: (event: {matches: boolean}) => void) => listeners.push(() => listener({matches: isWithin(media)})),
            removeEventListener: vi.fn(),
        }))
    })

    afterEach(() => {
        vi.unstubAllGlobals()
    })

    it("exports the mobile media query", () => {
        expect(MOBILE_QUERY).toBe("(max-width: 767px)")
    })

    it("is mobile below the sm breakpoint and not from it upwards", () => {
        width = 767
        expect(useIsMobile().value).toBe(true)

        width = 768
        expect(useIsMobile().value).toBe(false)
    })

    it("follows a viewport change after setup", () => {
        const isMobile = useIsMobile()
        expect(isMobile.value).toBe(false)

        resizeTo(500)

        expect(isMobile.value).toBe(true)
    })

    it("gives every caller the same value", () => {
        const first = useIsMobile()
        const second = useIsMobile()

        resizeTo(500)
        expect([first.value, second.value]).toEqual([true, true])

        resizeTo(1200)
        expect([first.value, second.value]).toEqual([false, false])
    })


})
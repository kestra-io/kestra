import {describe, test, expect, vi} from "vitest"
import {getTaskIconSrc} from "../../../src/utils/taskIconSrc"

function decodeDataUri(src: string): string {
    expect(src.startsWith("data:image/svg+xml;base64,")).toBe(true)
    const base64 = src.slice("data:image/svg+xml;base64,".length)
    const bytes = Uint8Array.from(atob(base64), c => c.charCodeAt(0))
    return new TextDecoder().decode(bytes)
}

describe("getTaskIconSrc", () => {
    test("bakes the given color into currentColor", () => {
        const svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><circle fill=\"currentColor\"/></svg>"
        const src = getTaskIconSrc(btoa(svg), "#123456")
        const decoded = decodeDataUri(src)
        expect(decoded).toContain("fill=\"#123456\"")
        expect(decoded).not.toContain("currentColor")
    })

    test("returns the fallback icon when no base64 icon is given", () => {
        const decoded = decodeDataUri(getTaskIconSrc(undefined, "#000000"))
        expect(decoded).toContain("<svg")
        expect(decoded).toContain("fill=\"#000000\"")
    })

    test("round-trips non-ASCII characters without throwing", () => {
        const svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\">" +
            "<!-- Générateur: Ædobe Illustrator 日本語 --><circle fill=\"currentColor\"/></svg>"
        const decoded = decodeDataUri(getTaskIconSrc(btoa(unescape(encodeURIComponent(svg))), "#fff"))
        expect(decoded).toContain("日本語")
        expect(decoded).toContain("Générateur")
    })

    test("caches the built src per (icon, color) pair — same inputs never re-decode", () => {
        const svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><polygon points=\"1,1 2,2\" fill=\"currentColor\"/></svg>"
        const base64 = btoa(svg)

        const spy = vi.spyOn(globalThis, "atob")
        const first = getTaskIconSrc(base64, "#abcdef")
        const callsAfterFirst = spy.mock.calls.length
        const second = getTaskIconSrc(base64, "#abcdef")
        const callsAfterSecond = spy.mock.calls.length
        spy.mockRestore()

        expect(second).toBe(first)
        expect(callsAfterSecond).toBe(callsAfterFirst)
    })

    test("builds a different src for the same icon under a different color", () => {
        const svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><rect fill=\"currentColor\"/></svg>"
        const base64 = btoa(svg)

        const light = getTaskIconSrc(base64, "#000000")
        const dark = getTaskIconSrc(base64, "#ffffff")

        expect(light).not.toBe(dark)
        expect(decodeDataUri(light)).toContain("fill=\"#000000\"")
        expect(decodeDataUri(dark)).toContain("fill=\"#ffffff\"")
    })
})

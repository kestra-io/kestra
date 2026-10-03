import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {revealApp} from "../../../src/utils/loaderReveal"

function mountLoader() {
    const loader = document.createElement("div")
    loader.id = "loader-wrapper"
    loader.classList.add("is-visible")
    document.body.appendChild(loader)
    return loader
}

function mountAppContainer() {
    const appContainer = document.createElement("div")
    appContainer.id = "app-container"
    appContainer.style.display = "none"
    document.body.appendChild(appContainer)
    return appContainer
}

describe("revealApp", () => {
    beforeEach(() => {
        vi.useFakeTimers()
    })

    afterEach(() => {
        document.getElementById("loader-wrapper")?.remove()
        document.getElementById("app-container")?.remove()
        delete window.__kestraLoader
        vi.useRealTimers()
    })

    it("clears the pending __kestraLoader timer", () => {
        const showLoader = vi.fn()
        const timer = setTimeout(showLoader, 200)
        window.__kestraLoader = {showDelay: 200, timer}
        const clearTimeoutSpy = vi.spyOn(globalThis, "clearTimeout")

        revealApp()
        vi.advanceTimersByTime(200)

        expect(clearTimeoutSpy).toHaveBeenCalledWith(timer)
        expect(showLoader).not.toHaveBeenCalled()
        clearTimeoutSpy.mockRestore()
    })

    it("hides the loader by removing is-visible and setting display none", () => {
        const loader = mountLoader()
        mountAppContainer()

        revealApp()

        expect(loader.classList.contains("is-visible")).toBe(false)
        expect(loader.style.display).toBe("none")
    })

    it("shows the app container", () => {
        mountLoader()
        const appContainer = mountAppContainer()

        revealApp()

        expect(appContainer.style.display).toBe("block")
    })

    it("calls onRevealed once the app container is shown", () => {
        mountLoader()
        const appContainer = mountAppContainer()
        const onRevealed = vi.fn(() => {
            expect(appContainer.style.display).toBe("block")
        })

        revealApp(onRevealed)

        expect(onRevealed).toHaveBeenCalledOnce()
    })

    it("does not throw when the loader element is missing", () => {
        const appContainer = mountAppContainer()
        const onRevealed = vi.fn()

        expect(() => revealApp(onRevealed)).not.toThrow()
        expect(appContainer.style.display).toBe("block")
        expect(onRevealed).toHaveBeenCalledOnce()
    })

    it("does not throw when the app container is missing", () => {
        const loader = mountLoader()
        const onRevealed = vi.fn()

        expect(() => revealApp(onRevealed)).not.toThrow()
        expect(loader.style.display).toBe("none")
        expect(onRevealed).toHaveBeenCalledOnce()
    })

    it("does not throw when window.__kestraLoader was never set", () => {
        mountLoader()
        mountAppContainer()

        expect(window.__kestraLoader).toBeUndefined()
        expect(() => revealApp()).not.toThrow()
    })
})

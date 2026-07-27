import {beforeEach, describe, expect, it, vi} from "vitest"
import {appInstalled, deferredInstallPrompt, initPwaInstallCapture} from "../../../src/utils/pwaInstallState"

function fireBeforeInstallPrompt() {
    const event = new Event("beforeinstallprompt") as Event & {prompt: () => Promise<void>, userChoice: Promise<unknown>}
    event.prompt = vi.fn().mockResolvedValue(undefined)
    event.userChoice = Promise.resolve({outcome: "accepted", platform: "web"})
    window.dispatchEvent(event)
    return event
}

describe("pwaInstallState", () => {
    beforeEach(() => {
        deferredInstallPrompt.value = null
        appInstalled.value = false
    })

    it("captures beforeinstallprompt once initialized, before any composable reads it", () => {
        initPwaInstallCapture()

        expect(deferredInstallPrompt.value).toBeNull()

        fireBeforeInstallPrompt()

        expect(deferredInstallPrompt.value).not.toBeNull()
    })

    it("does not register the listener twice when called multiple times", () => {
        const addSpy = vi.spyOn(window, "addEventListener")

        initPwaInstallCapture()
        initPwaInstallCapture()
        initPwaInstallCapture()

        expect(addSpy).not.toHaveBeenCalled()

        addSpy.mockRestore()
    })

    it("clears the deferred prompt and marks the app installed on appinstalled", () => {
        initPwaInstallCapture()
        fireBeforeInstallPrompt()
        expect(deferredInstallPrompt.value).not.toBeNull()

        window.dispatchEvent(new Event("appinstalled"))

        expect(appInstalled.value).toBe(true)
        expect(deferredInstallPrompt.value).toBeNull()
    })
})

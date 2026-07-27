import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent} from "vue"
import {mount} from "@vue/test-utils"
import {usePwaInstall} from "../../../src/composables/usePwaInstall"
import {appInstalled, deferredInstallPrompt, initPwaInstallCapture} from "../../../src/utils/pwaInstallState"

function mountPwaInstall() {
    let api: ReturnType<typeof usePwaInstall>
    const Comp = defineComponent({
        setup() {
            api = usePwaInstall()
            return () => null
        },
    })
    const wrapper = mount(Comp)
    return {api: api!, wrapper}
}

function stubMatchMedia(matches: boolean) {
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
        matches,
        media: query,
        addEventListener: () => {},
        removeEventListener: () => {},
    })) as unknown as typeof window.matchMedia
}

function stubLocation(protocol: string, hostname: string) {
    const original = window.location
    Object.defineProperty(window, "location", {
        value: {...original, protocol, hostname},
        writable: true,
        configurable: true,
    })
    return () => {
        Object.defineProperty(window, "location", {value: original, writable: true, configurable: true})
    }
}

function fireBeforeInstallPrompt() {
    const event = new Event("beforeinstallprompt") as Event & {prompt: () => Promise<void>, userChoice: Promise<unknown>}
    event.prompt = vi.fn().mockResolvedValue(undefined)
    event.userChoice = Promise.resolve({outcome: "accepted", platform: "web"})
    window.dispatchEvent(event)
    return event
}

describe("usePwaInstall", () => {
    let restoreLocation: () => void

    beforeEach(() => {
        stubMatchMedia(false)
        restoreLocation = stubLocation("https:", "kestra.example.com")
        deferredInstallPrompt.value = null
        appInstalled.value = false
    })

    afterEach(() => {
        restoreLocation()
    })

    it("cannot install until the browser fires beforeinstallprompt", () => {
        const {api} = mountPwaInstall()

        expect(api.canInstall.value).toBe(false)
    })

    it("can install once beforeinstallprompt is captured on a fresh, non-installed, secure origin", async () => {
        const {api} = mountPwaInstall()

        fireBeforeInstallPrompt()

        expect(api.canInstall.value).toBe(true)
    })

    it("cannot install when already running in standalone display-mode", () => {
        stubMatchMedia(true)
        const {api} = mountPwaInstall()

        fireBeforeInstallPrompt()

        expect(api.canInstall.value).toBe(false)
    })

    it("cannot install on an insecure origin that is not localhost", () => {
        restoreLocation()
        restoreLocation = stubLocation("http:", "kestra.example.com")
        const {api} = mountPwaInstall()

        fireBeforeInstallPrompt()

        expect(api.canInstall.value).toBe(false)
    })

    it("can install over http on localhost", () => {
        restoreLocation()
        restoreLocation = stubLocation("http:", "localhost")
        const {api} = mountPwaInstall()

        fireBeforeInstallPrompt()

        expect(api.canInstall.value).toBe(true)
    })

    it("cannot install after the prompt is dismissed", () => {
        const {api} = mountPwaInstall()
        fireBeforeInstallPrompt()

        api.dismiss()

        expect(api.canInstall.value).toBe(false)
        expect(api.dismissed.value).toBe(true)
    })

    it("triggers the captured prompt and clears it once resolved", async () => {
        const {api} = mountPwaInstall()
        const event = fireBeforeInstallPrompt()

        await api.promptInstall()

        expect(event.prompt).toHaveBeenCalledTimes(1)
        expect(api.canInstall.value).toBe(false)
    })

    it("marks the app installed and revokes the prompt on appinstalled", () => {
        const {api} = mountPwaInstall()
        fireBeforeInstallPrompt()
        expect(api.canInstall.value).toBe(true)

        window.dispatchEvent(new Event("appinstalled"))

        expect(api.canInstall.value).toBe(false)
    })

    it("can install when beforeinstallprompt was captured at bootstrap, before the composable ever ran", () => {
        initPwaInstallCapture()
        fireBeforeInstallPrompt()

        const {api} = mountPwaInstall()

        expect(api.canInstall.value).toBe(true)
    })
})

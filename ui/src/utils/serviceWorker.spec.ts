import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {computeSwScope, registerServiceWorker, SERVICE_WORKER_FILENAME} from "./serviceWorker"

describe("SERVICE_WORKER_FILENAME", () => {
    it("equals sw.js", () => {
        expect(SERVICE_WORKER_FILENAME).toBe("sw.js")
    })
})

describe("computeSwScope", () => {
    it("resolves an empty basePath to the ui root scope", () => {
        expect(computeSwScope("")).toBe("/ui/")
    })

    it("resolves the root basePath to the ui root scope", () => {
        expect(computeSwScope("/")).toBe("/ui/")
    })

    it("resolves null/undefined the same as an empty basePath", () => {
        expect(computeSwScope(null)).toBe("/ui/")
        expect(computeSwScope(undefined)).toBe("/ui/")
    })

    it("appends ui/ under a sub-path basePath without a trailing slash", () => {
        expect(computeSwScope("/kestra")).toBe("/kestra/ui/")
    })

    it("appends ui/ under a sub-path basePath with a trailing slash", () => {
        expect(computeSwScope("/kestra/")).toBe("/kestra/ui/")
    })

    it("adds a leading slash when the basePath is missing one (reverse-proxy edge case)", () => {
        expect(computeSwScope("kestra")).toBe("/kestra/ui/")
    })

    it("collapses repeated trailing slashes", () => {
        expect(computeSwScope("/kestra///")).toBe("/kestra/ui/")
    })
})

describe("registerServiceWorker", () => {
    const registerMock = vi.fn()

    beforeEach(() => {
        vi.stubEnv("PROD", true)
        registerMock.mockReset().mockResolvedValue(undefined as unknown as ServiceWorkerRegistration)
        Object.defineProperty(navigator, "serviceWorker", {
            value: {register: registerMock},
            configurable: true,
            writable: true,
        })
        delete (window as {KESTRA_BASE_PATH?: string}).KESTRA_BASE_PATH
    })

    afterEach(() => {
        vi.unstubAllEnvs()
        vi.restoreAllMocks()
        delete (navigator as {serviceWorker?: unknown}).serviceWorker
        delete (window as {KESTRA_BASE_PATH?: string}).KESTRA_BASE_PATH
    })

    it("registers the service worker when supported in production", async () => {
        await registerServiceWorker()

        expect(registerMock).toHaveBeenCalledOnce()
        expect(registerMock).toHaveBeenCalledWith("/ui/sw.js", {scope: "/ui/"})
    })

    it("uses window.KESTRA_BASE_PATH to compute the registration scope", async () => {
        window.KESTRA_BASE_PATH = "/custom/base"

        await registerServiceWorker()

        expect(registerMock).toHaveBeenCalledWith("/custom/base/ui/sw.js", {scope: "/custom/base/ui/"})
    })

    it("does nothing when import.meta.env.PROD is false", async () => {
        vi.stubEnv("PROD", false)

        await registerServiceWorker()

        expect(registerMock).not.toHaveBeenCalled()
    })

    it("does nothing and does not throw when navigator.serviceWorker is unsupported", async () => {
        delete (navigator as {serviceWorker?: unknown}).serviceWorker

        await expect(registerServiceWorker()).resolves.toBeUndefined()
    })

    it("swallows registration rejections and logs an error", async () => {
        const error = new Error("registration failed")
        registerMock.mockRejectedValue(error)
        const consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {})

        await expect(registerServiceWorker()).resolves.toBeUndefined()
        expect(consoleErrorSpy).toHaveBeenCalledWith("Service worker registration failed", error)
    })
})


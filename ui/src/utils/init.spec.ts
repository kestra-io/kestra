import {describe, it, expect, vi, beforeEach} from "vitest"
import {createApp} from "vue"

const configs = {isUiAnonymousUsageEnabled: true}
const forwarded = vi.fn()

vi.mock("vue-gtag", () => ({configure: vi.fn()}))
vi.mock("override/stores/misc", () => ({useMiscStore: () => ({configs})}))
vi.mock("./posthog", () => ({
    createPosthogVueErrorHandler: (getConfigs: () => unknown) => (error: unknown, _instance: unknown, info: string) =>
        forwarded(getConfigs(), error, info),
}))

import initApp from "./init"

describe("initApp", () => {
    beforeEach(() => vi.clearAllMocks())

    it("forwards Vue errors to product analytics for every edition", async () => {
        const app = createApp({})

        await initApp(app, [], null, {})

        const error = new Error("component crashed")
        app.config.errorHandler?.(error, null, "render function")

        expect(forwarded).toHaveBeenCalledWith(configs, error, "render function")
    })
})

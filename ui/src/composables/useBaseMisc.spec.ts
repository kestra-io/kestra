import {describe, it, expect, beforeEach, vi} from "vitest"
import {createPinia, setActivePinia, defineStore} from "pinia"
import type {MiscControllerConfiguration, MiscControllerApiUsage} from "@kestra-io/kestra-sdk"
import * as MiscAPI from "@kestra-io/kestra-sdk/misc"
import * as BasicAuth from "../utils/basicAuth"
import {useBaseMiscStore} from "./useBaseMisc"

vi.mock("@kestra-io/kestra-sdk/misc", () => ({
    configuration: vi.fn(),
    loginConfiguration: vi.fn(),
    basicAuthConfigErrors: vi.fn(),
    usages: vi.fn(),
    createBasicAuth: vi.fn(),
}))

vi.mock("../utils/basicAuth", () => ({
    isLoggedIn: vi.fn(),
}))

vi.mock("../utils/posthog", () => ({
    initPosthogIfEnabled: vi.fn(),
}))

vi.mock("../utils/analytics/posthogEvents", () => ({
    sendPosthogEvent: vi.fn(),
}))

vi.mock("../utils/uid", () => ({
    ensureUid: () => "test-uid",
}))

const useStore = defineStore("baseMisc", useBaseMiscStore)

describe("useBaseMiscStore", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        vi.clearAllMocks()
    })

    it("initializes with documented defaults", () => {
        const store = useStore()

        expect(store.configs).toBeUndefined()
        expect(store.contextInfoBarOpenTab).toBe("")
        expect(store.lastContextTab).toBe("ai")
        expect(store.theme).toBe("syncWithSystem")
        expect(store.copilotPrompt).toBeNull()
        expect(store.copilotThreadTitle).toBeNull()
        expect(store.copilotNewThread).toBe(false)
        expect(store.copilotSendInitialMessage).toBe(false)
    })

    it("openCopilot sets both the open tab and the last tab to 'ai'", () => {
        const store = useStore()
        store.contextInfoBarOpenTab = "logs"
        store.lastContextTab = "logs"

        store.openCopilot()

        expect(store.contextInfoBarOpenTab).toBe("ai")
        expect(store.lastContextTab).toBe("ai")
    })

    describe("promptCopilot", () => {
        it("stores the prompt and opens the copilot", () => {
            const store = useStore()

            store.promptCopilot("Fix this error")

            expect(store.copilotPrompt).toBe("Fix this error")
            expect(store.contextInfoBarOpenTab).toBe("ai")
            expect(store.lastContextTab).toBe("ai")
        })

        it("defaults the title to null and the send flag to false when no options are given", () => {
            const store = useStore()

            store.promptCopilot("Fix this error")

            expect(store.copilotThreadTitle).toBeNull()
            expect(store.copilotSendInitialMessage).toBe(false)
        })

        it("sets the title from options when provided", () => {
            const store = useStore()

            store.promptCopilot("Fix this error", {title: "My thread"})

            expect(store.copilotThreadTitle).toBe("My thread")
        })

        it("sets the send flag only for an explicit true", () => {
            const store = useStore()

            store.promptCopilot("Prompt A", {sendInitialMessage: true})
            expect(store.copilotSendInitialMessage).toBe(true)

            store.promptCopilot("Prompt B", {sendInitialMessage: false})
            expect(store.copilotSendInitialMessage).toBe(false)

            store.promptCopilot("Prompt C", {})
            expect(store.copilotSendInitialMessage).toBe(false)

            store.promptCopilot("Prompt D")
            expect(store.copilotSendInitialMessage).toBe(false)
        })
    })

    describe("loadConfigs", () => {
        it("stores and returns the configuration", async () => {
            const mockConfig: MiscControllerConfiguration = {
                isBasicAuthInitialized: true,
                isUiAnonymousUsageEnabled: false,
            }
            vi.mocked(MiscAPI.configuration).mockResolvedValue(mockConfig)

            const store = useStore()
            const result = await store.loadConfigs()

            expect(MiscAPI.configuration).toHaveBeenCalledOnce()
            expect(store.configs).toEqual(mockConfig)
            expect(result).toEqual(mockConfig)
        })
    })

    describe("loadAllUsages", () => {
        it("returns an empty object when basic auth is not initialised and user is not logged in", async () => {
            const store = useStore()
            store.configs = {isBasicAuthInitialized: false}
            vi.mocked(BasicAuth.isLoggedIn).mockReturnValue(false)

            const result = await store.loadAllUsages()

            expect(MiscAPI.usages).not.toHaveBeenCalled()
            expect(result).toEqual({})
        })

        it("returns an empty object when basic auth is not initialised but user is logged in", async () => {
            const store = useStore()
            store.configs = {isBasicAuthInitialized: false}
            vi.mocked(BasicAuth.isLoggedIn).mockReturnValue(true)

            const result = await store.loadAllUsages()

            expect(MiscAPI.usages).not.toHaveBeenCalled()
            expect(result).toEqual({})
        })

        it("returns an empty object when basic auth is initialised but user is not logged in", async () => {
            const store = useStore()
            store.configs = {isBasicAuthInitialized: true}
            vi.mocked(BasicAuth.isLoggedIn).mockReturnValue(false)

            const result = await store.loadAllUsages()

            expect(MiscAPI.usages).not.toHaveBeenCalled()
            expect(result).toEqual({})
        })

        it("calls the usages endpoint and returns its result when basic auth is initialised and user is logged in", async () => {
            const mockUsages: MiscControllerApiUsage = {executions: {dailyExecutionsCount: []}}
            vi.mocked(MiscAPI.usages).mockResolvedValue(mockUsages)

            const store = useStore()
            store.configs = {isBasicAuthInitialized: true}
            vi.mocked(BasicAuth.isLoggedIn).mockReturnValue(true)

            const result = await store.loadAllUsages(7)

            expect(MiscAPI.usages).toHaveBeenCalledOnce()
            expect(MiscAPI.usages).toHaveBeenCalledWith({days: 7})
            expect(result).toEqual(mockUsages)
        })

        it("queries a single day by default", async () => {
            vi.mocked(MiscAPI.usages).mockResolvedValue({})

            const store = useStore()
            store.configs = {isBasicAuthInitialized: true}
            vi.mocked(BasicAuth.isLoggedIn).mockReturnValue(true)

            await store.loadAllUsages()

            expect(MiscAPI.usages).toHaveBeenCalledWith({days: 1})
        })
    })

    describe("changeBasicAuth", () => {
        it("sends the new credentials together with the current password", async () => {
            vi.mocked(MiscAPI.createBasicAuth).mockResolvedValue(undefined as never)

            const store = useStore()

            await store.changeBasicAuth({
                username: "user",
                password: "new-pass",
                currentPassword: "old-pass",
            })

            expect(MiscAPI.createBasicAuth).toHaveBeenCalledOnce()
            expect(MiscAPI.createBasicAuth).toHaveBeenCalledWith({
                uid: "test-uid",
                username: "user",
                password: "new-pass",
                currentPassword: "old-pass",
            })
        })

        it("marks the error handled and re-throws it", async () => {
            type HandledError = Error & {__kestra_handled?: boolean}
            const error: HandledError = new Error("Bad credentials")
            vi.mocked(MiscAPI.createBasicAuth).mockRejectedValue(error)

            const store = useStore()

            await expect(store.changeBasicAuth({
                username: "user",
                password: "new-pass",
                currentPassword: "old-pass",
            })).rejects.toThrow(error)

            expect(error["__kestra_handled"]).toBe(true)
        })
    })
})

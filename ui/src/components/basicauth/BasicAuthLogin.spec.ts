import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import BasicAuthLogin from "./BasicAuthLogin.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {storageKeys} from "../../utils/constants"

const {route, push, shouldShowWelcome} = vi.hoisted(() => ({
    route: {query: {} as Record<string, string>, params: {} as Record<string, string>},
    push: vi.fn(),
    shouldShowWelcome: vi.fn(),
}))
vi.mock("vue-router", () => ({
    useRoute: () => route,
    useRouter: () => ({push, options: {history: {base: "/ui"}}}),
}))
vi.mock("@kestra-io/kestra-sdk", async (importOriginal) => ({
    ...await importOriginal<typeof import("@kestra-io/kestra-sdk")>(),
    useClient: () => ({get: vi.fn().mockResolvedValue({data: {isBasicAuthInitialized: true}})}),
}))
vi.mock("../../utils/basicAuth", () => ({signIn: vi.fn().mockResolvedValue({username: "admin@kestra.io"})}))
vi.mock("../../utils/welcomeGuard", () => ({shouldShowWelcome}))
vi.mock("../../utils/posthog", () => ({identifyPosthogUser: vi.fn()}))
vi.mock("override/stores/misc", () => ({useMiscStore: () => ({loadConfigs: vi.fn().mockResolvedValue({})})}))

const messages = {
    email: "Email",
    password: "Password",
    setup: {login_title: "Login", login: "Login", troubleshooting: "Troubleshooting"},
    gantt: "Gantt",
    login_redirect_hint: {
        after_sign_in: "After sign-in",
        execution_tab: "Execution {id} · {tab} tab",
        execution: "Execution {id}",
        tab: "{tab} tab",
    },
}

function mountLogin() {
    setActivePinia(createPinia())
    return i18nMount(BasicAuthLogin, {messages})
}

beforeEach(() => {
    route.query = {}
    route.params = {}
    push.mockReset()
    shouldShowWelcome.mockReset()
})

afterEach(() => {
    sessionStorage.removeItem(storageKeys.SESSION_ACTIVE)
})

async function signIn() {
    const wrapper = mountLogin()
    await wrapper.find("input[name=\"username\"]").setValue("admin@kestra.io")
    await wrapper.find("input[name=\"password\"]").setValue("password")
    await wrapper.find("form").trigger("submit")
    await flushPromises()
}

describe("BasicAuthLogin redirect ('from' param) open-redirect protection", () => {
    it.each([
        ["////evil.com", "backend-bypass payload (4+ leading slashes)"],
        ["//evil.com", "protocol-relative URL"],
        ["https://evil.com/path", "absolute URL"],
    ])("rejects %s (%s)", (payload) => {
        route.query = {from: payload}
        const wrapper = mountLogin()

        const hiddenInput = wrapper.find<HTMLInputElement>("input[name=\"from\"]")
        expect(hiddenInput.exists()).toBe(true)
        expect(hiddenInput.element.value).toBe("")
    })

    it("keeps a legitimate same-origin relative path", () => {
        route.query = {from: "/flows/edit/1"}
        const wrapper = mountLogin()

        const hiddenInput = wrapper.find<HTMLInputElement>("input[name=\"from\"]")
        expect(hiddenInput.element.value).toBe("/flows/edit/1")
    })
})

describe("BasicAuthLogin after sign-in hint", () => {
    it("tells where a deep link to an execution tab will land", () => {
        // Given
        route.query = {from: "/executions/company.finance/monthly_close/2qncB78egtgMA8D0HVACE2/gantt"}

        // When
        const hint = mountLogin().find("[data-test=\"login-after-sign-in\"]")

        // Then
        expect(hint.text()).toContain("company.finance / monthly_close")
        expect(hint.text()).toContain("Execution 2qncB78e · Gantt tab")
    })

    it.each([undefined, "/dashboard"])("is hidden when from is %s", (from) => {
        // Given
        route.query = from ? {from} : {}

        // When / Then
        expect(mountLogin().find("[data-test=\"login-after-sign-in\"]").exists()).toBe(false)
    })

    it("shows no tab line for a tab it does not know", () => {
        // Given
        route.query = {from: "/executions/company.finance/monthly_close/2qncB78egtgMA8D0HVACE2/constructor"}

        // When
        const hint = mountLogin().find("[data-test=\"login-after-sign-in\"]")

        // Then
        expect(hint.text()).toContain("Execution 2qncB78e")
        expect(hint.text()).not.toContain("constructor")
    })

    it("is hidden for a hostile from value", () => {
        // Given
        route.query = {from: "/executions/ns/<img src=x onerror=alert(1)>/abc/gantt"}

        // When / Then
        expect(mountLogin().find("[data-test=\"login-after-sign-in\"]").exists()).toBe(false)
    })
})

describe("BasicAuthLogin after sign-in destination", () => {
    it("opens the welcome page on a fresh instance when from is not a deep link", async () => {
        // Given
        route.query = {from: "/dashboards"}
        shouldShowWelcome.mockResolvedValue(true)

        // When
        await signIn()

        // Then
        expect(push).toHaveBeenCalledWith({name: "ai"})
    })

    it("follows a deep link to an execution even on a fresh instance", async () => {
        // Given
        const from = "/executions/company.finance/monthly_close/2qncB78egtgMA8D0HVACE2/gantt"
        route.query = {from}
        shouldShowWelcome.mockResolvedValue(true)

        // When
        await signIn()

        // Then
        expect(push).toHaveBeenCalledWith(from)
        expect(shouldShowWelcome).not.toHaveBeenCalled()
    })

    it.each([true, false])("returns to a deep link recorded with the app base path (fresh instance: %s)", async (fresh) => {
        // Given
        route.query = {from: "/ui/main/executions/company.finance/monthly_close/2qncB78egtgMA8D0HVACE2/logs"}
        shouldShowWelcome.mockResolvedValue(fresh)

        // When
        await signIn()

        // Then
        expect(push).toHaveBeenCalledWith("/main/executions/company.finance/monthly_close/2qncB78egtgMA8D0HVACE2/logs")
    })

    it("drops the app base path from any other from once the instance has flows", async () => {
        // Given
        route.query = {from: "/ui/main/namespaces/edit/company.finance?tab=files"}
        shouldShowWelcome.mockResolvedValue(false)

        // When
        await signIn()

        // Then
        expect(push).toHaveBeenCalledWith("/main/namespaces/edit/company.finance?tab=files")
    })

    it("keeps a path that only starts with the same letters as the app base path", async () => {
        // Given
        route.query = {from: "/uikit/main/flows"}
        shouldShowWelcome.mockResolvedValue(false)

        // When
        await signIn()

        // Then
        expect(push).toHaveBeenCalledWith("/uikit/main/flows")
    })

    it("follows from once the instance has flows", async () => {
        // Given
        route.query = {from: "/dashboards"}
        shouldShowWelcome.mockResolvedValue(false)

        // When
        await signIn()

        // Then
        expect(push).toHaveBeenCalledWith("/dashboards")
    })
})

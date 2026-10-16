import {describe, it, expect, vi, beforeEach} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import BasicAuthLogin from "./BasicAuthLogin.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

const {route} = vi.hoisted(() => ({
    route: {query: {} as Record<string, string>, params: {} as Record<string, string>},
}))
vi.mock("vue-router", () => ({
    useRoute: () => route,
    useRouter: () => ({push: vi.fn()}),
}))

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
})

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

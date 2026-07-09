import {describe, it, expect, vi, beforeEach} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {createPinia, setActivePinia} from "pinia"
import BasicAuthLogin from "../../../src/components/basicauth/BasicAuthLogin.vue"

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
}

const i18n = createI18n({legacy: false, locale: "en", messages: {en: messages}, missingWarn: false, fallbackWarn: false})

function mountLogin() {
    setActivePinia(createPinia())
    return mount(BasicAuthLogin, {global: {plugins: [i18n]}})
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

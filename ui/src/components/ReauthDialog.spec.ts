import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {nextTick} from "vue"
import ReauthDialog from "./ReauthDialog.vue"
import {i18nMount} from "../../tests/unit/i18nMount"
import {KsAlert} from "@kestra-io/design-system"

const {
    visible,
    loginUrl,
    lockedUsername,
    canSignInWithPassword,
    confirmReauth,
    submitReauth,
    resolveReauth,
} = await vi.hoisted(async () => {
    const {ref} = await import("vue")
    return {
        visible: ref(false),
        loginUrl: ref<string | undefined>(undefined),
        lockedUsername: ref<string | undefined>(undefined),
        canSignInWithPassword: ref(true),
        confirmReauth: vi.fn(),
        submitReauth: vi.fn(),
        resolveReauth: vi.fn(),
    }
})

vi.mock("../composables/useReauthDialog", () => ({
    useReauthDialog: () => ({
        visible,
        loginUrl,
        lockedUsername,
        canSignInWithPassword,
    }),
    confirmReauth,
    submitReauth,
    resolveReauth,
}))

const KsDialogStub = {
    name: "KsDialog",
    props: ["modelValue"],
    template: "<div v-if=\"modelValue\" data-test=\"reauth-dialog\"><slot /><slot name=\"footer\" /></div>",
}

function mountDialog() {
    return i18nMount(ReauthDialog, {
        global: {
            stubs: {
                KsDialog: KsDialogStub,
            },
        },
    })
}

const findButton = (wrapper: ReturnType<typeof mountDialog>, text: string) =>
    wrapper.findAll("button").find((btn) => btn.text().includes(text))

describe("ReauthDialog", () => {
    beforeEach(() => {
        visible.value = false
        loginUrl.value = undefined
        lockedUsername.value = undefined
        canSignInWithPassword.value = true
        confirmReauth.mockReset()
        submitReauth.mockReset()
        resolveReauth.mockReset()
        vi.spyOn(window, "open").mockImplementation(() => null)
    })

    afterEach(() => {
        vi.restoreAllMocks()
    })

    test("the dialog follows the composable's visible ref", async () => {
        visible.value = false
        const wrapper = mountDialog()

        expect(wrapper.find("[data-test=\"reauth-dialog\"]").exists()).toBe(false)
        expect(wrapper.findComponent(KsDialogStub).props("modelValue")).toBe(false)

        visible.value = true
        await nextTick()

        expect(wrapper.find("[data-test=\"reauth-dialog\"]").exists()).toBe(true)
        expect(wrapper.findComponent(KsDialogStub).props("modelValue")).toBe(true)
    })

    test("the password form is rendered only when password sign-in is available and no login tab was opened", async () => {
        visible.value = true
        canSignInWithPassword.value = true
        loginUrl.value = "https://kestra.io/login"
        const wrapper = mountDialog()
        await nextTick()

        expect(wrapper.find("form").exists()).toBe(true)

        // When password sign-in is not available
        canSignInWithPassword.value = false
        await nextTick()
        expect(wrapper.find("form").exists()).toBe(false)

        // When login tab was opened
        canSignInWithPassword.value = true
        await nextTick()
        expect(wrapper.find("form").exists()).toBe(true)

        await findButton(wrapper, "use another sign-in method")?.trigger("click")
        await nextTick()
        expect(wrapper.find("form").exists()).toBe(false)
    })

    test("the sign-in button is disabled until both fields are filled, and whenever a request is in flight", async () => {
        visible.value = true
        canSignInWithPassword.value = true
        const wrapper = mountDialog()
        await nextTick()

        const getSubmitButton = () => findButton(wrapper, "setup.login")
        expect(getSubmitButton()?.attributes("disabled")).toBeDefined()

        // Only username filled
        await wrapper.find("input[name=\"username\"]").setValue("user@example.com")
        expect(getSubmitButton()?.attributes("disabled")).toBeDefined()

        // Username whitespace only
        await wrapper.find("input[name=\"username\"]").setValue("   ")
        await wrapper.find("input[name=\"password\"]").setValue("secret")
        expect(getSubmitButton()?.attributes("disabled")).toBeDefined()

        // Both filled
        await wrapper.find("input[name=\"username\"]").setValue("user@example.com")
        await wrapper.find("input[name=\"password\"]").setValue("secret")
        expect(getSubmitButton()?.attributes("disabled")).toBeUndefined()

        // Request in flight
        let resolveSubmit!: () => void
        submitReauth.mockReturnValueOnce(new Promise<void>((resolve) => {
            resolveSubmit = resolve
        }))
        await getSubmitButton()?.trigger("click")
        await nextTick()

        expect(getSubmitButton()?.attributes("disabled")).toBeDefined()

        // Pressing enter in the password field must not fire a second request while one is in flight
        await wrapper.find("input[name=\"password\"]").trigger("keyup.enter")
        expect(submitReauth).toHaveBeenCalledTimes(1)

        resolveSubmit()
        await flushPromises()
    })

    test("a 401 or 403 renders the incorrect-credentials alert, and any other failure the generic one", async () => {
        visible.value = true
        const wrapper = mountDialog()
        await nextTick()

        await wrapper.find("input[name=\"username\"]").setValue("user@example.com")
        await wrapper.find("input[name=\"password\"]").setValue("wrong-password")

        // 401 error
        submitReauth.mockRejectedValueOnce({status: 401})
        await findButton(wrapper, "setup.login")?.trigger("click")
        await flushPromises()

        expect(wrapper.findComponent(KsAlert).props("title")).toBe("setup.validation.incorrect_creds")

        // 403 error
        submitReauth.mockRejectedValueOnce({status: 403})
        await findButton(wrapper, "setup.login")?.trigger("click")
        await flushPromises()

        expect(wrapper.findComponent(KsAlert).props("title")).toBe("setup.validation.incorrect_creds")

        // Other failure (e.g. 500)
        submitReauth.mockRejectedValueOnce({status: 500})
        await findButton(wrapper, "setup.login")?.trigger("click")
        await flushPromises()

        expect(wrapper.findComponent(KsAlert).props("title")).toBe("sign in failed")
    })

    test("a successful submit resolves the reauth as true", async () => {
        visible.value = true
        submitReauth.mockResolvedValueOnce(undefined)
        const wrapper = mountDialog()
        await nextTick()

        await wrapper.find("input[name=\"username\"]").setValue("user@example.com")
        await wrapper.find("input[name=\"password\"]").setValue("correct-password")
        await findButton(wrapper, "setup.login")?.trigger("click")
        await flushPromises()

        expect(submitReauth).toHaveBeenCalledWith({
            username: "user@example.com",
            password: "correct-password",
        })
        expect(resolveReauth).toHaveBeenCalledWith(true)
    })

    test("opening the login tab swaps the footer to continue-after-sign-in", async () => {
        visible.value = true
        canSignInWithPassword.value = true
        loginUrl.value = "https://kestra.io/login"
        const wrapper = mountDialog()
        await nextTick()

        expect(wrapper.find("[data-test=\"reauth-continue\"]").exists()).toBe(false)

        await findButton(wrapper, "use another sign-in method")?.trigger("click")
        await nextTick()

        expect(window.open).toHaveBeenCalledWith("https://kestra.io/login", "_blank", "noopener")
        expect(wrapper.find("[data-test=\"reauth-continue\"]").exists()).toBe(true)
        expect(wrapper.find("[data-test=\"reauth-tab-hint\"]").exists()).toBe(true)
        expect(findButton(wrapper, "setup.login")).toBeUndefined()
    })

    test("a successful confirm after the login tab resolves the reauth as true", async () => {
        visible.value = true
        loginUrl.value = "https://kestra.io/login"
        confirmReauth.mockResolvedValueOnce(undefined)

        const wrapper = mountDialog()
        await nextTick()

        await findButton(wrapper, "use another sign-in method")?.trigger("click")
        await nextTick()

        await wrapper.find("[data-test=\"reauth-continue\"]").trigger("click")
        await flushPromises()

        expect(confirmReauth).toHaveBeenCalledTimes(1)
        expect(resolveReauth).toHaveBeenCalledWith(true)
        expect(wrapper.findComponent(KsAlert).exists()).toBe(false)
    })

    test("a failed confirm renders the not-confirmed alert and leaves the dialog open", async () => {
        visible.value = true
        loginUrl.value = "https://kestra.io/login"
        confirmReauth.mockRejectedValueOnce(new Error("Confirmation failed"))

        const wrapper = mountDialog()
        await nextTick()

        await findButton(wrapper, "use another sign-in method")?.trigger("click")
        await nextTick()

        await wrapper.find("[data-test=\"reauth-continue\"]").trigger("click")
        await flushPromises()

        expect(confirmReauth).toHaveBeenCalled()
        expect(wrapper.findComponent(KsAlert).props("title")).toBe("sign in not confirmed")
        expect(resolveReauth).not.toHaveBeenCalled()
        expect(wrapper.find("[data-test=\"reauth-dialog\"]").exists()).toBe(true)
    })

    test("reopening clears the password, both alerts and the opened-tab flag", async () => {
        visible.value = true
        loginUrl.value = "https://kestra.io/login"
        submitReauth.mockRejectedValueOnce({status: 401})

        const wrapper = mountDialog()
        await nextTick()

        await wrapper.find("input[name=\"username\"]").setValue("user@example.com")
        await wrapper.find("input[name=\"password\"]").setValue("wrong-password")
        await findButton(wrapper, "setup.login")?.trigger("click")
        await flushPromises()

        // Opened tab, failed alert and not-confirmed alert are all active
        confirmReauth.mockRejectedValueOnce(new Error("Confirmation failed"))
        await findButton(wrapper, "use another sign-in method")?.trigger("click")
        await nextTick()
        await wrapper.find("[data-test=\"reauth-continue\"]").trigger("click")
        await flushPromises()
        expect(wrapper.find("[data-test=\"reauth-tab-hint\"]").exists()).toBe(true)
        expect(wrapper.findAllComponents(KsAlert).map((alert) => alert.props("title"))).toEqual([
            "setup.validation.incorrect_creds",
            "sign in not confirmed",
        ])

        // Close dialog
        visible.value = false
        await nextTick()

        // Reopen dialog
        visible.value = true
        await nextTick()

        expect(wrapper.findComponent(KsAlert).exists()).toBe(false)
        expect(wrapper.find("[data-test=\"reauth-tab-hint\"]").exists()).toBe(false)
        expect(wrapper.find("form").exists()).toBe(true)
        expect((wrapper.find("input[name=\"password\"]").element as HTMLInputElement).value).toBe("")
    })

    test("a locked username is prefilled and read-only", async () => {
        lockedUsername.value = "locked@kestra.io"
        visible.value = false
        const wrapper = mountDialog()

        visible.value = true
        await nextTick()

        const usernameInput = wrapper.find("input[name=\"username\"]")
        expect((usernameInput.element as HTMLInputElement).value).toBe("locked@kestra.io")
        expect(usernameInput.attributes("readonly")).toBeDefined()
    })

    test("the go-to-login button resolves the reauth as false", async () => {
        visible.value = true
        const wrapper = mountDialog()
        await nextTick()

        await findButton(wrapper, "go to login")?.trigger("click")

        expect(resolveReauth).toHaveBeenCalledWith(false)
    })
})

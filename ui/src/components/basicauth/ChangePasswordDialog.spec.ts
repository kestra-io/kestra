import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import ChangePasswordDialog from "./ChangePasswordDialog.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {useMiscStore} from "../../override/stores/misc"
import * as BasicAuth from "../../utils/basicAuth"

vi.mock("../../utils/toast", () => ({
    useToast: () => ({success: vi.fn(), error: vi.fn()}),
}))

const stubs = {
    KsDialog: {
        props: ["dirty", "modelValue"],
        template: "<div :data-dirty=\"String(dirty)\"><slot name=\"header\" /><slot /><slot name=\"footer\" /></div>",
    },
}

describe("ChangePasswordDialog pre-filled username and dirty state", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        localStorage.clear()
    })

    afterEach(() => {
        localStorage.clear()
    })

    it("pre-fills username from stored localStorage username", () => {
        BasicAuth.setStoredUsername("admin@kestra.io")

        const wrapper = i18nMount(ChangePasswordDialog, {
            props: {
                modelValue: true,
            },
            global: {stubs},
        })

        const usernameInput = wrapper.find<HTMLInputElement>("[data-test=change-password-username]")
        expect(usernameInput.exists()).toBe(true)
        expect(usernameInput.element.value).toBe("admin@kestra.io")

        expect(wrapper.find("[data-dirty]").attributes("data-dirty")).toBe("false")
    })

    it("defaults username to empty string when not stored", () => {
        const wrapper = i18nMount(ChangePasswordDialog, {
            props: {
                modelValue: true,
            },
            global: {stubs},
        })

        const usernameInput = wrapper.find<HTMLInputElement>("[data-test=change-password-username]")
        expect(usernameInput.exists()).toBe(true)
        expect(usernameInput.element.value).toBe("")
    })

    it("pre-fills username from props when provided", () => {
        BasicAuth.setStoredUsername("stored@kestra.io")

        const wrapper = i18nMount(ChangePasswordDialog, {
            props: {
                modelValue: true,
                username: "prop@kestra.io",
            },
            global: {stubs},
        })

        const usernameInput = wrapper.find<HTMLInputElement>("[data-test=change-password-username]")
        expect(usernameInput.element.value).toBe("prop@kestra.io")
    })

    it("marks dialog dirty when pre-filled username is modified", async () => {
        BasicAuth.setStoredUsername("initial@kestra.io")

        const wrapper = i18nMount(ChangePasswordDialog, {
            props: {
                modelValue: true,
            },
            global: {stubs},
        })

        expect(wrapper.find("[data-dirty]").attributes("data-dirty")).toBe("false")

        const usernameInput = wrapper.find<HTMLInputElement>("[data-test=change-password-username]")
        await usernameInput.setValue("modified@kestra.io")
        expect(wrapper.find("[data-dirty]").attributes("data-dirty")).toBe("true")

        await usernameInput.setValue("initial@kestra.io")
        expect(wrapper.find("[data-dirty]").attributes("data-dirty")).toBe("false")
    })

    it("submits changeBasicAuth and updates stored username", async () => {
        const miscStore = useMiscStore()
        miscStore.changeBasicAuth = vi.fn().mockResolvedValue({})

        BasicAuth.setStoredUsername("admin@kestra.io")

        const wrapper = i18nMount(ChangePasswordDialog, {
            props: {
                modelValue: true,
            },
            global: {stubs},
        })

        await wrapper.find<HTMLInputElement>("[data-test=change-password-current]").setValue("OldPassword1")
        await wrapper.find<HTMLInputElement>("[data-test=change-password-new]").setValue("NewPassword1")
        await wrapper.find<HTMLInputElement>("[data-test=change-password-confirm]").setValue("NewPassword1")

        const submitBtn = wrapper.find<HTMLButtonElement>("[data-test=change-password-submit]")
        expect(submitBtn.attributes("disabled")).toBeUndefined()

        await submitBtn.trigger("click")

        expect(miscStore.changeBasicAuth).toHaveBeenCalledWith({
            username: "admin@kestra.io",
            currentPassword: "OldPassword1",
            password: "NewPassword1",
        })
        expect(BasicAuth.getStoredUsername()).toBe("admin@kestra.io")
        expect(wrapper.emitted("update:modelValue")?.[0]).toEqual([false])
    })
})

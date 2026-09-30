import {describe, it, expect, vi, beforeEach} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import ChangePasswordDialog from "../../../src/components/basicauth/ChangePasswordDialog.vue"
import {useMiscStore} from "../../../src/override/stores/misc"
import {i18nMount} from "../i18nMount"

const messages = {
    change_password: {
        title: "Change password",
        current_password: "Current password",
        new_password: "New password",
        success: "Updated",
    },
}

function mountDialog() {
    return i18nMount(ChangePasswordDialog, {
        messages,
        props: {modelValue: true},
        global: {stubs: {KsDialog: {template: "<div><slot /><slot name=\"footer\" /></div>"}}},
    })
}

async function fill(wrapper: ReturnType<typeof mountDialog>, currentPassword: string) {
    await wrapper.find("[data-test=change-password-username]").setValue("admin@kestra.io")
    await wrapper.find("[data-test=change-password-current]").setValue(currentPassword)
    await wrapper.find("[data-test=change-password-new]").setValue("NewPassword1")
    await wrapper.find("[data-test=change-password-confirm]").setValue("NewPassword1")
}

beforeEach(() => {
    setActivePinia(createPinia())
})

describe("ChangePasswordDialog", () => {
    it("keeps the dialog open and shows the server's reason when the current password is wrong", async () => {
        const store = useMiscStore()
        const detail = "The current password is required and must be correct to change Basic Authentication credentials."
        vi.spyOn(store, "changeBasicAuth").mockRejectedValue({
            problem: {type: "about:blank", title: "Validation failed", status: 422, errors: [{detail}]},
        })

        const wrapper = mountDialog()
        await fill(wrapper, "WrongPassword1")
        await wrapper.find("[data-test=change-password-submit]").trigger("click")
        await flushPromises()

        expect(store.changeBasicAuth).toHaveBeenCalledWith({
            username: "admin@kestra.io",
            currentPassword: "WrongPassword1",
            password: "NewPassword1",
        })
        expect(wrapper.find("[data-test=change-password-error]").text()).toContain(detail)
        expect(wrapper.emitted("update:modelValue")).toBeUndefined()
    })
})

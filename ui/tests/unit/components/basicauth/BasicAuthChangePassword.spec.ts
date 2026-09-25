import {describe, it, expect, vi, beforeEach} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {i18nMount} from "../../i18nMount"
import BasicAuthChangePassword from "../../../../src/components/basicauth/BasicAuthChangePassword.vue"

const mocks = vi.hoisted(() => ({
    addBasicAuth: vi.fn(),
    ksMessage: vi.fn(),
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({addBasicAuth: mocks.addBasicAuth}),
}))

vi.mock("@kestra-io/design-system", async (importOriginal) => {
    const mod = await importOriginal<typeof import("@kestra-io/design-system")>()
    return {...mod, KsMessage: mocks.ksMessage}
})

const addBasicAuth = mocks.addBasicAuth
const ksMessageMock = mocks.ksMessage

const messages = {
    "setup": {
        "change_password": {
            "title": "Change password",
            "current_password": "Current password",
            "new_password": "New password",
            "username_hint": "Enter your current username (email) so your account can be identified.",
            "incorrect_current": "The current password is incorrect.",
            "success": "Password changed successfully.",
        },
        "form": {"username": "Username"},
        "validation": {"email_required": "Email is required", "password_required": "Password is required"},
    },
    "email": "Email",
    "password": "Password",
    "confirm password": "Confirm password",
    "password_requirements": {"match": "Passwords match"},
    "cancel": "Cancel",
    "save": "Save",
}

const stubs = {
    KsDialog: {template: "<div><slot /><slot name=\"footer\" /></div>"},
    KsForm: {
        template: "<form><slot /></form>",
        methods: {validate: () => Promise.resolve(true)},
    },
    KsFormItem: {template: "<div><slot /></div>"},
    KsInput: {
        props: ["modelValue"],
        emits: ["update:modelValue"],
        template: "<input :value=\"modelValue\" @input=\"$emit('update:modelValue', $event.target.value)\" />",
    },
    KsPasswordRequirements: {
        props: ["password"],
        emits: ["update:valid"],
        template: "<div />",
        mounted(this: {password: string, $emit: (e: string, v: boolean) => void}) {
            this.$emit("update:valid", this.password.length >= 8)
        },
        watch: {
            password(this: unknown, value: string) {
                (this as {$emit: (e: string, v: boolean) => void}).$emit("update:valid", value.length >= 8)
            },
        },
    },
    KsCheckItem: {props: ["met"], template: "<div><slot /></div>"},
    KsButton: {template: "<button><slot /></button>"},
    KsText: {template: "<span><slot /></span>"},
}

function mountDialog() {
    return i18nMount(BasicAuthChangePassword, {
        messages,
        props: {modelValue: true},
        global: {stubs},
    })
}

async function fillValid(wrapper: ReturnType<typeof mountDialog>) {
    const inputs = wrapper.findAll("input")
    expect(inputs.length).toBe(4)
    await inputs[0].setValue("admin@kestra.io")
    await inputs[1].setValue("OldStrongPass1")
    await inputs[2].setValue("NewStrongPass1")
    await inputs[3].setValue("NewStrongPass1")
    await flushPromises()
}

describe("BasicAuthChangePassword", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        vi.clearAllMocks()
        addBasicAuth.mockResolvedValue(undefined)
    })

    it("keeps submit disabled until the form is valid", async () => {
        const wrapper = mountDialog()
        await flushPromises()

        const submit = wrapper.findAll("button").find((b) => b.text() === "Save")!
        expect(submit.attributes("disabled")).toBeDefined()

        await fillValid(wrapper)
        expect(wrapper.findAll("button").find((b) => b.text() === "Save")!.attributes("disabled")).toBeUndefined()
    })

    it("submits username, new password and current password", async () => {
        const wrapper = mountDialog()
        await fillValid(wrapper)

        await wrapper.findAll("button").find((b) => b.text() === "Save")!.trigger("click")
        await flushPromises()

        expect(addBasicAuth).toHaveBeenCalledTimes(1)
        expect(addBasicAuth).toHaveBeenCalledWith({
            username: "admin@kestra.io",
            password: "NewStrongPass1",
            currentPassword: "OldStrongPass1",
        })
        expect(wrapper.emitted("update:modelValue")).toContainEqual([false])
        expect(ksMessageMock).toHaveBeenCalledWith(expect.objectContaining({type: "success"}))
    })

    it("shows an incorrect-current-password error on 422 and keeps the dialog open", async () => {
        addBasicAuth.mockRejectedValue({response: {status: 422}})
        const wrapper = mountDialog()
        await fillValid(wrapper)

        await wrapper.findAll("button").find((b) => b.text() === "Save")!.trigger("click")
        await flushPromises()

        expect(ksMessageMock).toHaveBeenCalledWith(expect.objectContaining({type: "error"}))
        expect(wrapper.emitted("update:modelValue")).toBeUndefined()
    })
})

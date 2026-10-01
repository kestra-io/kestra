import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import {expect, fn, userEvent, waitFor} from "storybook/test"
import ChangePasswordDialog from "../../../../src/components/basicauth/ChangePasswordDialog.vue"
import {useMiscStore} from "../../../../src/override/stores/misc"

const WRONG_PASSWORD_DETAIL = "The current password is required and must be correct to change Basic Authentication credentials."

const changeBasicAuth = fn()

const meta: Meta<typeof ChangePasswordDialog> = {
    title: "basicauth/ChangePasswordDialog",
    component: ChangePasswordDialog,
    decorators: [
        () => ({
            setup() {
                useMiscStore().changeBasicAuth = changeBasicAuth
            },
            template: "<story />",
        }),
    ],
    beforeEach() {
        changeBasicAuth.mockReset()
    },
}
export default meta
type Story = StoryObj<typeof ChangePasswordDialog>

const render: Story["render"] = () => ({
    setup() {
        const visible = ref(true)
        return () => (
            <>
                <ChangePasswordDialog modelValue={visible.value} onUpdate:modelValue={(value) => visible.value = value} />
                <div data-test="visible">{String(visible.value)}</div>
            </>
        )
    },
})

export const Default: Story = {
    render,
}

/** A wrong current password must keep the dialog open and surface the server's reason. */
export const WrongCurrentPassword: Story = {
    render,
    beforeEach() {
        changeBasicAuth.mockRejectedValue({
            problem: {type: "about:blank", title: "Validation failed", status: 422, errors: [{detail: WRONG_PASSWORD_DETAIL}]},
        })
    },
    async play({canvasElement}) {
        const byTest = (name: string) => document.querySelector<HTMLElement>(`[data-test=change-password-${name}]`)
        const input = (name: string) => {
            const el = byTest(name)!
            return el instanceof HTMLInputElement ? el : el.querySelector("input")!
        }

        await waitFor(() => expect(byTest("username")).toBeVisible())
        await userEvent.type(input("username"), "admin@kestra.io")
        await userEvent.type(input("current"), "WrongPassword1")
        await userEvent.type(input("new"), "NewPassword1")
        await userEvent.type(input("confirm"), "NewPassword1")
        await userEvent.click(byTest("submit")!)

        await expect(changeBasicAuth).toHaveBeenCalledWith({
            username: "admin@kestra.io",
            currentPassword: "WrongPassword1",
            password: "NewPassword1",
        })
        await waitFor(() => expect(byTest("error")).toHaveTextContent(WRONG_PASSWORD_DETAIL))
        await expect(canvasElement.querySelector("[data-test=visible]")).toHaveTextContent("true")
    },
}

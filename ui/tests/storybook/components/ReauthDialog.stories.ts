import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, fn, userEvent, waitFor, within} from "storybook/test"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import ReauthDialog from "../../../src/components/ReauthDialog.vue"
import {requestReauth, resolveReauth, type SignIn} from "../../../src/composables/useReauthDialog"
import en from "../../../src/translations/en.json"

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: {en}})

const meta: Meta<typeof ReauthDialog> = {
    title: "components/ReauthDialog",
    component: ReauthDialog,
    decorators: [
        (story) => ({
            components: {story},
            plugins: [i18n, KestraDesignSystem],
            template: "<story />",
        }),
    ],
    parameters: {
        docs: {
            description: {
                component:
                    "Opened by the HTTP layer on the first 401 of a logged-out session. The promise of `requestReauth` settles `true` after a successful sign-in (the failed request is then replayed) and `false` when the user asks for the login page.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof ReauthDialog>

const body = () => within(document.body)

function open(signIn: SignIn) {
    resolveReauth(false)
    return requestReauth(signIn)
}

export const Open: Story = {
    async play() {
        open(fn())
        await waitFor(async () => expect(await body().findByText("Your session has expired")).toBeVisible())
        await expect(body().getByRole("button", {name: "Login"})).toBeDisabled()
    },
}

export const WrongPassword: Story = {
    async play() {
        const signIn = fn().mockRejectedValue(new Error("Unauthorized"))
        open(signIn)

        await userEvent.type(await body().findByPlaceholderText("Email"), "me@example.com")
        await userEvent.type(body().getByPlaceholderText("Password"), "wrong")
        await userEvent.click(body().getByRole("button", {name: "Login"}))

        await expect(await body().findByText(/Invalid username or password/)).toBeVisible()
        await expect(signIn).toHaveBeenCalledWith({username: "me@example.com", password: "wrong"})
        await expect(body().getByText("Your session has expired")).toBeVisible()
    },
}

export const SignsInAndResolves: Story = {
    async play() {
        const outcome = fn()
        open(fn().mockResolvedValue(undefined)).then(outcome)

        await userEvent.type(await body().findByPlaceholderText("Email"), "me@example.com")
        await userEvent.type(body().getByPlaceholderText("Password"), "secret")
        await userEvent.click(body().getByRole("button", {name: "Login"}))

        await waitFor(() => expect(outcome).toHaveBeenCalledWith(true))
        await waitFor(() => expect(body().queryByText("Your session has expired")).not.toBeVisible())
    },
}

export const GoToLoginPage: Story = {
    async play() {
        const outcome = fn()
        open(fn()).then(outcome)

        await userEvent.click(await body().findByRole("button", {name: "Go to login page"}))

        await waitFor(() => expect(outcome).toHaveBeenCalledWith(false))
    },
}

import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, fn, spyOn, userEvent, waitFor, within} from "storybook/test"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import ReauthDialog from "../../../src/components/ReauthDialog.vue"
import {requestReauth, resolveReauth, type ReauthOptions} from "../../../src/composables/useReauthDialog"
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

function open(options: ReauthOptions) {
    resolveReauth(false)
    return requestReauth(options)
}

export const Open: Story = {
    async play() {
        open({signIn: fn()})
        await waitFor(async () => expect(await body().findByText("Your session has expired")).toBeVisible())
        await expect(body().getByRole("button", {name: "Login"})).toBeDisabled()
    },
}

export const WrongPassword: Story = {
    async play() {
        const signIn = fn().mockRejectedValue(new Error("Unauthorized"))
        open({signIn})

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
        open({signIn: fn().mockResolvedValue(undefined)}).then(outcome)

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
        open({signIn: fn()}).then(outcome)

        await userEvent.click(await body().findByRole("button", {name: "Go to login page"}))

        await waitFor(() => expect(outcome).toHaveBeenCalledWith(false))
    },
}

export const SignInInANewTabOnly: Story = {
    async play() {
        const openWindow = spyOn(window, "open").mockReturnValue(null)
        const outcome = fn()
        open({loginUrl: "/ui/login"}).then(outcome)

        await waitFor(async () => expect(await body().findByText("Your session has expired")).toBeVisible())
        await expect(body().queryByPlaceholderText("Email")).toBeNull()

        await userEvent.click(body().getByRole("button", {name: "Sign in in a new tab"}))
        await expect(openWindow).toHaveBeenCalledWith("/ui/login", "_blank", "noopener")
        await expect(await body().findByText(/come back here and continue/)).toBeVisible()

        await userEvent.click(body().getByRole("button", {name: "Continue"}))
        await waitFor(() => expect(outcome).toHaveBeenCalledWith(true))
        openWindow.mockRestore()
    },
}

export const AnotherMethodNextToPassword: Story = {
    async play() {
        const openWindow = spyOn(window, "open").mockReturnValue(null)
        open({signIn: fn(), loginUrl: "/ui/login"})

        await userEvent.click(await body().findByRole("button", {name: "Use another sign-in method"}))

        await expect(openWindow).toHaveBeenCalledWith("/ui/login", "_blank", "noopener")
        await waitFor(() => expect(body().queryByPlaceholderText("Email")).toBeNull())
        openWindow.mockRestore()
    },
}

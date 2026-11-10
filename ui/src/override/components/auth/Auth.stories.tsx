import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, userEvent, waitFor, within} from "storybook/test"
import Auth from "./Auth.vue"
import {useMiscStore} from "../../stores/misc"

const meta: Meta<typeof Auth> = {
    title: "override/auth/Auth",
    component: Auth,
}
export default meta
type Story = StoryObj<typeof Auth>

function render(isBasicAuthManagedByConfig: boolean): Story["render"] {
    return () => ({
        setup() {
            const miscStore = useMiscStore()
            miscStore.configs = {...miscStore.configs, isBasicAuthManagedByConfig}
            return () => (
                <div style={{width: "240px", padding: "24px"}}>
                    <Auth />
                </div>
            )
        },
    })
}

async function openMenu(canvasElement: HTMLElement) {
    await userEvent.click(within(canvasElement).getByRole("button", {name: /kestra/i}))
    await waitFor(() => expect(within(document.body).getByText("Logout")).toBeVisible())
}

/** Credentials set up from the UI can be changed from the menu. */
export const ManagedFromUI: Story = {
    render: render(false),
    async play({canvasElement}) {
        await openMenu(canvasElement)
        await expect(document.querySelector("[data-test=change-password-menu-item]")).toBeInTheDocument()
    },
}

/** Credentials set in the configuration file cannot be changed from the UI. */
export const ManagedByConfig: Story = {
    render: render(true),
    async play({canvasElement}) {
        await openMenu(canvasElement)
        await expect(document.querySelector("[data-test=change-password-menu-item]")).not.toBeInTheDocument()
    },
}

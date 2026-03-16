import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import {within, userEvent, expect} from "storybook/test"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"
import KsDialog from "../../../src/components/Feedback/KsDialog.vue"

const meta: Meta<typeof KsDialog> = {
    title: "Components/Feedback/KsDialog",
    component: KsDialog,
    tags: ["autodocs"],
    argTypes: {
        destroyOnClose: {control: "boolean"},
        lockScroll: {control: "boolean"},
        appendToBody: {control: "boolean"},
    },
    parameters: {
        docs: {
            description: {
                component: "KsDialog is the Kestra design-system abstraction over `ElDialog` from Element Plus. Only the props, events and slots actually used across the Kestra UI are exposed.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsDialog>

export const Default: Story = {
    render: () => ({
        components: {KsButton, KsDialog},
        setup() {
            const visible = ref(false)
            return {visible}
        },
        template: `
            <div style="padding:24px">
                <ks-button type="primary" @click="visible = true">Open Dialog</ks-button>
                <ks-dialog v-model="visible" title="Confirm Action" destroy-on-close>
                    <p>Are you sure you want to proceed?</p>
                    <template #footer>
                        <ks-button @click="visible = false">Cancel</ks-button>
                        <ks-button type="primary" @click="visible = false">Confirm</ks-button>
                    </template>
                </ks-dialog>
            </div>
        `,
    }),
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const btn = canvas.getByRole("button", {name: "Open Dialog"})
        await expect(btn).toBeTruthy()
        await userEvent.click(btn)
        await expect(document.querySelector(".kel-dialog")).toBeTruthy()
    },
}

export const WithCustomHeader: Story = {
    render: () => ({
        components: {KsButton, KsDialog},
        setup() {
            const visible = ref(false)
            return {visible}
        },
        template: `
            <div style="padding:24px">
                <ks-button type="primary" @click="visible = true">Custom Header Dialog</ks-button>
                <ks-dialog v-model="visible" destroy-on-close>
                    <template #header>
                        <div style="display:flex;align-items:center;gap:8px">
                            <strong>Custom Header</strong>
                        </div>
                    </template>
                    <p>Dialog with a custom header slot.</p>
                    <template #footer>
                        <ks-button type="primary" @click="visible = false">Close</ks-button>
                    </template>
                </ks-dialog>
            </div>
        `,
    }),
}

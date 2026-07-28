import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import {within, userEvent, expect} from "storybook/test"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"
import KsDialog from "../../../src/components/Feedback/KsDialog.vue"
import KsForm from "../../../src/components/Form/KsForm/KsForm.vue"
import KsFormItem from "../../../src/components/Form/KsForm/KsFormItem.vue"
import KsInput from "../../../src/components/Form/KsInput.vue"

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

/** Custom width */
export const CustomWidth: Story = {
    render: () => ({
        components: {KsButton, KsDialog},
        setup() {
            const visible = ref(false)
            return {visible}
        },
        template: `
            <div style="padding:24px">
                <ks-button type="primary" @click="visible = true">Open wide dialog</ks-button>
                <ks-dialog v-model="visible" title="Wide Dialog" width="60%" destroy-on-close>
                    <p>This dialog has a custom width of 60%.</p>
                    <template #footer>
                        <ks-button type="primary" @click="visible = false">Close</ks-button>
                    </template>
                </ks-dialog>
            </div>
        `,
    }),
}

/** Large – widens the dialog to 756px (capped at 90vw) */
export const Large: Story = {
    render: () => ({
        components: {KsButton, KsDialog},
        setup() {
            const visible = ref(false)
            return {visible}
        },
        template: `
            <div style="padding:24px">
                <ks-button type="primary" @click="visible = true">Open large dialog</ks-button>
                <ks-dialog v-model="visible" title="Large Dialog" large destroy-on-close>
                    <p>This dialog uses the <code>large</code> prop for a 756px width.</p>
                    <template #footer>
                        <ks-button type="primary" @click="visible = false">Close</ks-button>
                    </template>
                </ks-dialog>
            </div>
        `,
    }),
}

/** Form layout – `formLayout` gives a form inside the dialog consistent padding, column flow and gap */
export const FormLayout: Story = {
    render: () => ({
        components: {KsButton, KsDialog, KsForm, KsFormItem, KsInput},
        setup() {
            const visible = ref(false)
            const form = ref({name: "", description: ""})
            return {visible, form}
        },
        template: `
            <div style="padding:24px">
                <ks-button type="primary" @click="visible = true">Open form dialog</ks-button>
                <ks-dialog v-model="visible" title="Add a new group" form-layout destroy-on-close>
                    <ks-form :model="form">
                        <ks-form-item label="Name">
                            <ks-input v-model="form.name" />
                        </ks-form-item>
                        <ks-form-item label="Description">
                            <ks-input v-model="form.description" />
                        </ks-form-item>
                    </ks-form>
                    <template #footer>
                        <ks-button @click="visible = false">Cancel</ks-button>
                        <ks-button type="primary" @click="visible = false">Create</ks-button>
                    </template>
                </ks-dialog>
            </div>
        `,
    }),
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        await userEvent.click(canvas.getByRole("button", {name: "Open form dialog"}))
        const form = document.querySelector(".kel-dialog.is-form-layout form")
        await expect(form).toBeTruthy()
        await expect(getComputedStyle(form as Element).display).toBe("flex")
        await expect(getComputedStyle(form as Element).flexDirection).toBe("column")
    },
}

/** Destroy on close – remounts content each time */
export const DestroyOnClose: Story = {
    render: () => ({
        components: {KsButton, KsDialog},
        setup() {
            const visible = ref(false)
            const count = ref(0)
            return {visible, count}
        },
        template: `
            <div style="padding:24px">
                <ks-button type="primary" @click="() => { count++; visible = true }">
                    Open (opened {{ count }}x)
                </ks-button>
                <ks-dialog v-model="visible" title="Destroy On Close" destroy-on-close>
                    <p>This dialog destroys its content on close. Open count: {{ count }}</p>
                    <template #footer>
                        <ks-button type="primary" @click="visible = false">Close</ks-button>
                    </template>
                </ks-dialog>
            </div>
        `,
    }),
}

/** Close on click modal (backdrop) disabled */
export const NoCloseOnBackdrop: Story = {
    render: () => ({
        components: {KsButton, KsDialog},
        setup() {
            const visible = ref(false)
            return {visible}
        },
        template: `
            <div style="padding:24px">
                <ks-button type="primary" @click="visible = true">Click backdrop won't close</ks-button>
                <ks-dialog v-model="visible" title="Persistent Dialog" :close-on-click-modal="false" destroy-on-close>
                    <p>Click outside will not close this dialog. Use the button below.</p>
                    <template #footer>
                        <ks-button type="primary" @click="visible = false">Close explicitly</ks-button>
                    </template>
                </ks-dialog>
            </div>
        `,
    }),
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

import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {within, userEvent, expect} from "storybook/test"
import {ref} from "vue"
import {KsMessageBox} from "../../../src/components/Feedback/KsMessageBox"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"

const meta: Meta = {
    title: "Components/Feedback/KsMessageBox",
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component:
                    "KsMessageBox is the Kestra design-system abstraction over `ElMessageBox` from Element Plus. " +
                    "It provides three modes: `KsMessageBox.confirm()` for yes/no dialogs, " +
                    "`KsMessageBox.alert()` for informational dialogs, and " +
                    "`KsMessageBox.prompt()` for input dialogs.",
            },
        },
    },
}
export default meta
type Story = StoryObj

export const Confirm: Story = {
    render: () => ({
        components: {KsButton},
        setup() {
            const result = ref<string>("")
            const open = async () => {
                try {
                    await KsMessageBox.confirm("Are you sure you want to delete this flow?", "Confirmation", {
                        type: "warning",
                        confirmButtonText: "Delete",
                        cancelButtonText: "Cancel",
                    })
                    result.value = "confirmed"
                } catch {
                    result.value = "cancelled"
                }
            }
            return {open, result}
        },
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px">
                <ks-button type="danger" @click="open">Confirm delete</ks-button>
                <span v-if="result">Result: {{ result }}</span>
            </div>
        `,
    }),
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const btn = canvas.getByRole("button")
        await expect(btn).toBeTruthy()
        await userEvent.click(btn)
        await expect(document.querySelector(".kel-message-box")).toBeTruthy()
    },
}

export const Alert: Story = {
    render: () => ({
        components: {KsButton},
        setup() {
            const open = () =>
                KsMessageBox.alert("This action cannot be undone. The flow and all its executions will be permanently deleted.", "Notice", {
                    confirmButtonText: "I understand",
                })
            return {open}
        },
        template: `
            <div style="padding:24px">
                <ks-button @click="open">Show alert dialog</ks-button>
            </div>
        `,
    }),
}

export const Prompt: Story = {
    render: () => ({
        components: {KsButton},
        setup() {
            const result = ref<string>("")
            const open = async () => {
                try {
                    const {value} = await KsMessageBox.prompt("Enter the new namespace", "Rename namespace", {
                        inputPlaceholder: "my.namespace",
                        confirmButtonText: "Rename",
                        cancelButtonText: "Cancel",
                    })
                    result.value = `Entered: ${value}`
                } catch {
                    result.value = "cancelled"
                }
            }
            return {open, result}
        },
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px">
                <ks-button type="primary" @click="open">Rename namespace</ks-button>
                <span v-if="result">{{ result }}</span>
            </div>
        `,
    }),
}

export const WithCallbackPattern: Story = {
    name: "With callback (options object)",
    render: () => ({
        components: {KsButton},
        setup() {
            const result = ref<string>("")
            const open = () => {
                KsMessageBox({
                    title: "Confirmation",
                    message: "Proceed with the bulk operation?",
                    type: "warning",
                    showCancelButton: true,
                    callback: (action: string) => {
                        result.value = action === "confirm" ? "confirmed" : "cancelled"
                    },
                })
            }
            return {open, result}
        },
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px">
                <ks-button type="warning" @click="open">Bulk operation</ks-button>
                <span v-if="result">Result: {{ result }}</span>
            </div>
        `,
    }),
}
